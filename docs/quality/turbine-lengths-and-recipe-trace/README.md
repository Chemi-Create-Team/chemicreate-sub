# Turbine length and recipe tracing checks

The two requested changes were verified together with `tests.gradle` and `client.gradle`.

- `turbines-front.jpg`, `turbines-back.jpg`: native Ponder screenshots of 5 HP sections (3×3×5) and 10 LP sections (9×9×10).
- `powder-hydrogen-nitrogen.jpg`: gunpowder Ponder showing live 750 mB hydrogen + 250 mB nitrogen → 1,000 mB synthesis gas, including the sources of both gases.
- `powder-nitrate.jpg`: the ammonium nitrate stage of the same gunpowder family. The following crafting step uses the actual vanilla-gunpowder recipe; it is not the screenshot's current frame.
- `jei-synthesis-feed.jpg`, `jei-haber-bosch.jpg`: actual JEI recipe screens. The latter shows synthesis gas, the iron catalyst and the compression/heating note.
- `recipe-trace-audit.json`: counts retrieved from the live JEI runtime: 19 categories and 122 displayed recipes. Focus lookups prove the synthesis-gas producer includes hydrogen and nitrogen, and synthesis gas leads to a Haber-Bosch recipe with iron catalyst.
- `turbine-fixed-length-operation.json`: 140-tick server trial with a 40,000 mB / 700 K steam charge. HP work 28.472 MJ, LP work 6.044 MJ; 256 RPM. Removed condensate 8,000 mB, remaining stage inventory 32,000 mB: the complete input is accounted for. Work is cumulative, not rated power.
- `results.txt`: build, all ten targeted GameTests, recipe registration/JEI focus checks, language and native rendering results.

Repeat the focused tests with `./gradlew --no-daemon -I docs/quality/turbine-lengths-and-recipe-trace/tests.gradle build runTurbineUnitGameTestServer`. The profile selects only turbine formation/operation, recipe visibility and the existing manufacturing Ponder validations. It does not run the entire GameTest suite.

Repeat the visual setup with `mc-debug ./gradlew --no-daemon -I docs/quality/turbine-lengths-and-recipe-trace/client.gradle runClient`. Screenshots above came from computer-use. The client finished all checks and then hit the existing `libEGL_nvidia.so.0` shutdown crash; that failing task exit is recorded separately from the passed assertions. Long-duration plant operation and every JEI recipe's physical execution were not tested.
