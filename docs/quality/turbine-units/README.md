# Crafted turbine unit verification

- `side-a.jpg` and `side-b.jpg`: unedited computer-use screenshots of the native Minecraft client, Japanese Ponder scene, at two camera angles. Static shells and ends remain closed while the scene animates the shafts.
- `client.gradle`: repeatable native-client quality profile; use `mc-debug ./gradlew --no-daemon -I docs/quality/turbine-units/client.gradle runClient` with the project JDK. Screenshots are captured with computer-use, not the game's screenshot helper.
- `banner-sources.json`: exact historical versions and SHA-256 of the three restored banners.
- `operation.json`: 100-tick dedicated-server result after supplying 40,000 mB of 700 K steam to a connected HP/LP set; values are cumulative work, not rated instantaneous power.
- `results.txt`: final build, targeted GameTest and client-quality result excerpts.

Hardware rendering was verified with `mc-debug glxinfo -B`: NVIDIA GeForce RTX 4060, driver 595.99.02. The native client completed Ponder state checks and capture, then crashed during shutdown in `libEGL_nvidia.so.0`. Consequently the `runClient` Gradle task exits unsuccessfully; this is recorded separately from the successful render/state assertions. The normal `build` and the three dedicated-server tests completed successfully.

The test suite checks both sizes and horizontal axes, occupied-space rejection, shared finite manifold simulation/execution, mass and energy across save/load, one-item recovery, recipe loading, steam expansion in both stages and ordinary-shaft output. It does not replace long-duration full-plant testing or manual survival-play placement testing.
