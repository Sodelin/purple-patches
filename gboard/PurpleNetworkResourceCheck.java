package dev.purple.patches.gboard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.xml.parsers.DocumentBuilderFactory;

/** Public, disposable patch-time resource fixtures. */
public final class PurpleNetworkResourceCheck {
    private static void require(boolean value) {
        if (!value) throw new AssertionError("Network resource control failed");
    }
    private static org.w3c.dom.Document parse(String value) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void refuse(org.w3c.dom.Document document) {
        try { PurpleNetworkResource.allowLoopback(document.getDocumentElement()); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { /* No unsafe rewrite. */ }
    }
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("purple-network-check-");
        try {
            Path res = scratch.resolve("res");
            Path xml = Files.createDirectories(res.resolve("xml"));
            Path qualified = Files.createDirectories(res.resolve("xml-v26"));
            Path original = xml.resolve("purple_keyboard_network.xml");
            byte[] originalBytes = "<original>keep</original>".getBytes(StandardCharsets.UTF_8);
            Files.write(original, originalBytes);
            Files.writeString(qualified.resolve("purple_keyboard_network.xml"), "qualified original 0");

            var existing = PurpleNetworkResource.select("@xml/purple_keyboard_network", res.toFile());
            require(!existing.create && existing.resource.equals("@xml/purple_keyboard_network"));
            PurpleNetworkResource.prepare(res.toFile(), existing, "true");
            require(java.util.Arrays.equals(Files.readAllBytes(original), originalBytes));
            require(new java.util.HashSet<>(PurpleNetworkResource.variants(res.toFile(), "purple_keyboard_network"))
                    .equals(java.util.Set.of("res/xml/purple_keyboard_network.xml", "res/xml-v26/purple_keyboard_network.xml")));

            Files.writeString(qualified.resolve("purple_keyboard_network_1.xml"), "qualified original");
            var fresh = PurpleNetworkResource.select("", res.toFile());
            require(fresh.create && fresh.resource.equals("@xml/purple_keyboard_network_2"));
            PurpleNetworkResource.prepare(res.toFile(), fresh, "");
            var parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(xml.resolve("purple_keyboard_network_2.xml").toFile());
            require(parsed.getDocumentElement().getTagName().equals("network-security-config"));
            require(parsed.getElementsByTagName("base-config").getLength() == 0);
            require(java.util.Arrays.equals(Files.readAllBytes(original), originalBytes));
            require(Files.readString(qualified.resolve("purple_keyboard_network_1.xml")).equals("qualified original"));

            for (String value : new String[]{"true", "false"}) {
                var next = PurpleNetworkResource.select("", res.toFile());
                PurpleNetworkResource.prepare(res.toFile(), next, value);
                String file = next.resource.substring("@xml/".length()) + ".xml";
                var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.resolve(file).toFile());
                var base = (org.w3c.dom.Element) doc.getElementsByTagName("base-config").item(0);
                require(base.getAttribute("cleartextTrafficPermitted").equals(value));
            }

            var invalid = PurpleNetworkResource.select("", res.toFile());
            try { PurpleNetworkResource.prepare(res.toFile(), invalid, "@bool/unknown"); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { /* No guessed policy. */ }
            String invalidName = invalid.resource.substring("@xml/".length()) + ".xml";
            require(!Files.exists(xml.resolve(invalidName)));

            var raced = PurpleNetworkResource.select("", res.toFile());
            Path racedPath = xml.resolve(raced.resource.substring("@xml/".length()) + ".xml");
            Files.writeString(racedPath, "appeared after selection");
            try { PurpleNetworkResource.prepare(res.toFile(), raced, "false"); throw new AssertionError(); }
            catch (java.nio.file.FileAlreadyExistsException expected) { /* Do not clobber it. */ }
            require(Files.readString(racedPath).equals("appeared after selection"));

            var qualifiedRace = PurpleNetworkResource.select("", res.toFile());
            Path racedVariant = qualified.resolve(qualifiedRace.resource.substring("@xml/".length()) + ".xml");
            Files.writeString(racedVariant, "qualified after selection");
            try { PurpleNetworkResource.prepare(res.toFile(), qualifiedRace, "false"); throw new AssertionError(); }
            catch (java.nio.file.FileAlreadyExistsException expected) { /* Preserve this variant too. */ }
            require(Files.readString(racedVariant).equals("qualified after selection"));

            var nested = parse("<network-security-config><base-config cleartextTrafficPermitted='false'/>"
                    + "<domain-config cleartextTrafficPermitted='false'><domain>example.test</domain>"
                    + "<domain-config cleartextTrafficPermitted='false'><domain> 127.0.0.1 </domain></domain-config>"
                    + "</domain-config></network-security-config>");
            PurpleNetworkResource.allowLoopback(nested.getDocumentElement());
            var nestedConfigs = nested.getElementsByTagName("domain-config");
            require(nestedConfigs.getLength() == 2);
            require(((org.w3c.dom.Element) nestedConfigs.item(0)).getAttribute("cleartextTrafficPermitted").equals("false"));
            require(((org.w3c.dom.Element) nestedConfigs.item(1)).getAttribute("cleartextTrafficPermitted").equals("true"));
            require(nested.getElementsByTagName("domain").getLength() == 2);

            var shared = parse("<network-security-config><domain-config cleartextTrafficPermitted='false'>"
                    + "<domain>127.0.0.1</domain><domain>example.test</domain></domain-config></network-security-config>");
            refuse(shared);
            require(((org.w3c.dom.Element) shared.getElementsByTagName("domain-config").item(0))
                    .getAttribute("cleartextTrafficPermitted").equals("false"));
            var alreadyAllowed = parse("<network-security-config><domain-config cleartextTrafficPermitted='true'>"
                    + "<domain includeSubdomains='true'>127.0.0.1</domain><domain>example.test</domain>"
                    + "</domain-config></network-security-config>");
            PurpleNetworkResource.allowLoopback(alreadyAllowed.getDocumentElement());
            require(alreadyAllowed.getElementsByTagName("domain-config").getLength() == 1);
            var broad = parse("<network-security-config><domain-config cleartextTrafficPermitted='false'>"
                    + "<domain includeSubdomains='true'>127.0.0.1</domain></domain-config></network-security-config>");
            refuse(broad);
            var duplicate = parse("<network-security-config><domain-config><domain>127.0.0.1</domain>"
                    + "<domain-config><domain> 127.0.0.1 </domain></domain-config>"
                    + "</domain-config></network-security-config>");
            refuse(duplicate);
            var freshConfig = parse("<network-security-config><base-config cleartextTrafficPermitted='false'/></network-security-config>");
            PurpleNetworkResource.allowLoopback(freshConfig.getDocumentElement());
            require(freshConfig.getElementsByTagName("domain").getLength() == 1);
            require(((org.w3c.dom.Element) freshConfig.getElementsByTagName("domain-config").item(0))
                    .getAttribute("cleartextTrafficPermitted").equals("true"));
            System.out.println("Network resource preservation controls passed");
        } finally {
            try (var paths = Files.walk(scratch)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception failure) { throw new RuntimeException(failure); }
                });
            }
        }
    }
}
