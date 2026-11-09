# Security policy

Zernyth is a release candidate. No stable security support or complete isolation
of Java plugins is promised. Install only trusted plugins and local libraries:
classloader separation is not a security sandbox, and plugins share the BDS
process and embedded JVM.

Do not post credentials, private keys, player data, exploitable payloads, or
unredacted session information in public issues. Use GitHub's private
vulnerability reporting for this repository if it is enabled. If unavailable,
open an issue requesting a private reporting channel without disclosing the
vulnerability itself. No monitored private contact address is currently declared.

Include affected versions, prerequisites, impact, and a minimal synthetic
reproduction when reporting privately. Do not test against public or productive
servers. Rotate any exposed credentials; deleting a file does not remove it from
Git history.

JNI checks, mocks, and sanitizer tests cover specific scenarios and do not prove
complete memory safety. See the [validation summary](docs/validation.md) and
[security guide](docs/zernyth-security-0.8.md) for current limits.
