# Gboard patch network resource preservation

The alpha.18 source unconditionally rewrites `res/xml/purple_keyboard_network.xml`
whenever that name appears in the manifest, including when it is the app's own
selected configuration. If no configuration is selected, it also reuses that
name without checking the app's qualified XML resources. Those cases can erase
or shadow an existing resource. This is a conditional source finding; the exact
target's original resource state and a device incident have not been established.

The alpha.19 source selects the app's existing resource when present; otherwise
it searches XML resource qualifiers for a free name and exclusively creates a
new base resource. If the manifest did not specify `usesCleartextTraffic`, the
new resource leaves the base policy unset, so Android selects its platform and
target-SDK default. A literal `true` or `false` is carried into the new base
configuration. An unresolved value is refused when a new configuration is
needed, rather than guessed. These are patch-time changes, not an installed app.

The source processes every matching `res/xml*` file and finds loopback domains
inside nested `domain-config` elements, normalizing surrounding whitespace. It
does not add a second `127.0.0.1` entry. An isolated loopback policy can be
updated; a policy already explicitly permitting cleartext is left unchanged.
Shared, nested-child or subdomain cases that would broaden another host's policy
are refused until their preservation is established. An XML resource alias
without matching decoded files is also refused. The qualifier search does not
inspect aliases declared in `res/values*`: a newly chosen name could still
collide with such an alias. The search and exclusive creation prevent ordinary
file collisions, but do not prove safety against a hostile concurrent writer
changing another variant after inspection. Alias admission remains open.

The relevant [pinned AOSP parser](https://android.googlesource.com/platform/frameworks/base/+/8d5bb577f49a9cf3a6cead707f45a30cd2e78982/core/java/android/security/net/config/XmlConfigSource.java)
trims domain names, rejects duplicates and recursively parses nested
configurations. [Android's network configuration documentation](https://developer.android.com/privacy-and-security/security-config)
describes target-SDK-dependent defaults and domain inheritance. The helper uses
`java.nio.file` APIs available from API 26, which is also Morphe's documented
minimum; it avoids `String.isBlank`, added only in API 33.

Public host controls exercise resource-name collisions, qualified variants,
exclusive creation, explicit and default base policy, nested/whitespace
loopback, duplicates and refusal of broader policies. Hosted run
[40961198282](https://github.com/Sodelin/purple-patches/actions/runs/40961198282)
passed those controls but failed the full build: the pinned Morphe plugin
targets JVM 11 while the added Java source inherited the runner's JDK 21 source
level. The subsequent source explicitly aligns this compile task with Java 11;
that correction still requires a successful build. Its run and any
exact-APK verification must be recorded separately; source alone is not proof
of successful patching, an Android parser result, stock network behavior, or
Manager compatibility. A review of alternative safe handling for shared and
nested policies, the original target's actual XML, and authorized device
regression remains open. Existing bootstrap, bearer, model and install holds
remain in force.
