package dev.purple.patches.gboard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Patch-time resource selection; this class is not injected into Gboard. */
public final class PurpleNetworkResource {
    private static final String NAME = "purple_keyboard_network";
    private PurpleNetworkResource() {}

    // String.isBlank() requires Android API 33; Morphe also runs on API 26.
    private static boolean blank(String value) {
        for (int i = 0; i < value.length(); i++)
            if (!Character.isWhitespace(value.charAt(i))) return false;
        return true;
    }

    public static final class Plan {
        public final String resource;
        public final boolean create;
        private final String name;
        private Plan(String resource, boolean create, String name) {
            this.resource = resource;
            this.create = create;
            this.name = name;
        }
    }

    /** Do not replace an app's selected configuration or any XML-qualified variant. */
    public static Plan select(String configured, File resRoot) throws IOException {
        if (configured == null || resRoot == null) throw new IllegalArgumentException();
        if (!blank(configured)) return new Plan(configured, false, null);
        for (long index = 0;; index = Math.addExact(index, 1)) {
            String name = NAME + (index == 0 ? "" : "_" + index);
            if (!occupied(resRoot.toPath(), name)) return new Plan("@xml/" + name, true, name);
        }
    }

    private static boolean occupied(Path root, String name) throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("Linked resource root refused");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return false;
        File[] children = root.toFile().listFiles();
        if (children == null) throw new IOException("Resource directory unavailable");
        for (File child : children) {
            String directory = child.getName();
            if (!directory.equals("xml") && !directory.startsWith("xml-")) continue;
            Path path = child.toPath();
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path))
                throw new IOException("Invalid XML resource directory");
            if (Files.exists(path.resolve(name + ".xml"), LinkOption.NOFOLLOW_LINKS)) return true;
        }
        return false;
    }

    /** Inspect every XML resource variant that Android could select. */
    public static List<String> variants(File resRoot, String name) throws IOException {
        if (resRoot == null || name == null || !name.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Invalid XML resource name");
        Path root = resRoot.toPath();
        if (Files.isSymbolicLink(root)) throw new IOException("Linked resource root refused");
        File[] children = root.toFile().listFiles();
        if (children == null) throw new IOException("Resource directory unavailable");
        List<String> paths = new ArrayList<>();
        for (File child : children) {
            String directory = child.getName();
            if (!directory.equals("xml") && !directory.startsWith("xml-")) continue;
            Path path = child.toPath();
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path))
                throw new IOException("Invalid XML resource directory");
            Path xml = path.resolve(name + ".xml");
            if (!Files.exists(xml, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isRegularFile(xml, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Invalid XML resource file");
            paths.add("res/" + directory + "/" + name + ".xml");
        }
        if (paths.isEmpty()) throw new IOException("Selected network XML resource unavailable");
        Collections.sort(paths);
        return paths;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element && ((Element) node).getTagName().equals(name))
                result.add((Element) node);
        return result;
    }

    private static void collectLoopback(Element config, List<Element> owners) {
        for (Element domain : children(config, "domain"))
            if (domain.getTextContent().trim().equals("127.0.0.1")) owners.add(config);
        for (Element nested : children(config, "domain-config")) collectLoopback(nested, owners);
    }

    /** Never duplicate a nested loopback or broaden unrelated existing domains. */
    public static void allowLoopback(Element root) {
        if (root == null || !root.getTagName().equals("network-security-config"))
            throw new IllegalArgumentException("Invalid network security configuration");
        List<Element> owners = new ArrayList<>();
        for (Element config : children(root, "domain-config")) collectLoopback(config, owners);
        if (owners.size() > 1) throw new IllegalArgumentException("Duplicate loopback policy");
        if (owners.isEmpty()) {
            Element config = root.getOwnerDocument().createElement("domain-config");
            config.setAttribute("cleartextTrafficPermitted", "true");
            Element domain = root.getOwnerDocument().createElement("domain");
            domain.setTextContent("127.0.0.1");
            domain.setAttribute("includeSubdomains", "false");
            config.appendChild(domain);
            root.appendChild(config);
            return;
        }
        Element owner = owners.get(0);
        // If the existing configuration explicitly allows it, do not alter its
        // potentially shared domains or nested policies.
        if (owner.getAttribute("cleartextTrafficPermitted").equals("true")) return;
        List<Element> domains = children(owner, "domain");
        if (domains.size() != 1 || !children(owner, "domain-config").isEmpty())
            throw new IllegalArgumentException("Loopback shares a broader network policy");
        String subdomains = domains.get(0).getAttribute("includeSubdomains");
        if (!subdomains.isEmpty() && !subdomains.equals("false"))
            throw new IllegalArgumentException("Loopback subdomain policy cannot be changed safely");
        owner.setAttribute("cleartextTrafficPermitted", "true");
    }

    /** Missing usesCleartextTraffic retains Android's target-SDK default. */
    public static String initialXml(String declaredCleartext) {
        if (declaredCleartext == null || blank(declaredCleartext))
            return "<network-security-config/>";
        if (!declaredCleartext.equals("true") && !declaredCleartext.equals("false"))
            throw new IllegalArgumentException("Unsupported referenced cleartext policy");
        return "<network-security-config><base-config cleartextTrafficPermitted=\""
                + declaredCleartext + "\"/></network-security-config>";
    }

    /** Create only the resource selected as new; an existing one is never rewritten. */
    public static void prepare(File resRoot, Plan plan, String declaredCleartext) throws IOException {
        if (resRoot == null || plan == null) throw new IllegalArgumentException();
        if (!plan.create) return;
        String xml = initialXml(declaredCleartext);
        Path root = resRoot.toPath();
        if (Files.isSymbolicLink(root)) throw new IOException("Linked resource root refused");
        if (occupied(root, plan.name))
            throw new java.nio.file.FileAlreadyExistsException(plan.name);
        Path directory = root.resolve("xml");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Linked XML resource directory refused");
        // CREATE_NEW rejects a collision even if a file appeared after selection.
        try (var stream = Files.newOutputStream(directory.resolve(plan.name + ".xml"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            stream.write(xml.getBytes(StandardCharsets.UTF_8));
        }
    }
}
