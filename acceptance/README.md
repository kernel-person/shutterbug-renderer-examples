# Test-only Paper probe

Build with `mvn -f acceptance/probe/pom.xml package` after building the three examples. This plugin is for a **fresh disposable test world only**: it loads fixture chunks, places a wall/camera/easel and drives synthetic-player events. It is not a gameplay plugin or a substitute for a real-client playtest.

Install it alongside the three examples and your separately licensed Renderer on isolated Paper26.2/Java25. Activation must be configured by the operator outside this repository. Do not expose an offline-mode test server to the network.

The first run emits `EXAMPLES_PROBE_OK phase=initial`; stop gracefully, restart the same test world and expect `EXAMPLES_PROBE_OK phase=restart`. Any `EXAMPLES_PROBE_FAILED`, plugin event exception or unclean shutdown invalidates the run even if a later success marker appears. Keep a finite operator timeout. Inspect the tests and logs before considering the result a pass.

The probe uses a synthetic Bukkit Player and real world/provider operations. It records only its restart fixture (height/frame UUID), never licensing information. The release packager excludes this JAR. See [acceptance results](../docs/acceptance.md) for the exact scope and remaining human checks.
