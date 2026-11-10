{% set profile = os.getenv("USERPROFILE", "").replace("\\", "/") %}
{% if not profile %}
{{ raise("USERPROFILE is required for sanitized Windows dependency builds") }}
{% endif %}
[conf]
# Sentry/Crashpad is intentionally built with the existing supported MSVC compiler.
# Map __FILE__ and PDB source references and use basename-only embedded PDB links.
sentry-native/*:tools.build:cflags=["/experimental:deterministic", "/pathmap:{{ profile }}=/zernyth"]
sentry-native/*:tools.build:cxxflags=["/experimental:deterministic", "/pathmap:{{ profile }}=/zernyth"]
sentry-native/*:tools.build:sharedlinkflags=["/PDBALTPATH:crashpad.pdb"]
sentry-native/*:tools.build:exelinkflags=["/PDBALTPATH:crashpad.pdb"]
{% for dependency in ["cpptrace", "raknet"] %}
{{ dependency }}/*:tools.build:cflags=["/clang:-ffile-prefix-map={{ profile }}=/zernyth", "/clang:-ffile-prefix-map={{ profile.replace('/', os.sep * 4) }}=/zernyth"]
{{ dependency }}/*:tools.build:cxxflags=["/clang:-ffile-prefix-map={{ profile }}=/zernyth", "/clang:-ffile-prefix-map={{ profile.replace('/', os.sep * 4) }}=/zernyth"]
{% endfor %}
