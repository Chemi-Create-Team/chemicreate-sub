# Turbine section verification

`view-0.jpg`, `view-1.jpg`, `view-2.jpg` are unedited native-client screenshots captured with computer-use at three camera angles. Each machine in this Ponder contains three one-block-deep sections. The outer end plates close around ordinary Create shafts; matching adjacent sections share their internal opening. Steel shell/flange geometry is cached per stage, and shaft/blade motion uses the existing BER path.

`operation.json` records a dedicated-server trial with one HP, one IP and one LP section, ordinary shafts, and actual ultra-high-pressure pipe connections. Input was 16,000 mB of 700 K steam. Evaluation was after 140 ticks: cumulative stage work, ordinary shaft RPM, and the mass/temperature of actual condensate removed from the bottom port. The final recorded batch yielded 12,288 mB of condensate while 3,712 mB remained inside the stages, preserving the entire 16,000 mB input. These are cumulative joules, not rated watts.

`results.txt` separates the successful build and three GameTests from native-client checks. The native client completed its Ponder state assertions and all three angle captures, then crashed during shutdown in `libEGL_nvidia.so.0`, as in the previous task. The native-client Gradle task therefore does not have a successful exit code. Rendering used an NVIDIA RTX 4060 with driver 595.99.02.

Repeat the server tests with `./gradlew --no-daemon build runTurbineUnitGameTestServer`. The `client.gradle` profile reproduces the Japanese Ponder setup with `mc-debug ./gradlew --no-daemon -I docs/quality/turbine-sections/client.gradle runClient`; capture its windows using computer-use.

The targeted tests cover six size/axis placements, one-block depth, obstruction rejection, saved ownership, all three 9×9 recipes, connected/disconnected terminal faces, a finite 48,000 mB three-section manifold, preview/execution, saved mass/energy, individual recovery, three-stage expansion and a 30 °C condensate outlet. Full-plant endurance, manual survival crafting and all existing GameTests were not run.
