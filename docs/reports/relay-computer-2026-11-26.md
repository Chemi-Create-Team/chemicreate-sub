# Relay computer (2026-11-26)

- Added the large relay item, relay rack and relay computer multiblock (computer plus 2 or more racks on top). It controls the nearest formed fission core or fusion reactor within 16 blocks. The relay rack recipe uses the upstream large iron plate.
- Added `FusionReactorControllerBlockEntity.command(Command, boolean)` and `setTargetPercent(int)` for control without a player, plus `ReactorCores.nearest`.
- Added a Ponder `relay_computer` with storyboard `ponder/plant/relay_computer.nbt`, and lang in en/ja/zh.
- GameTests:
  - `RelayComputerGameTests`: one rack does not form, two racks form, redstone hands control back.
  - `FusionGameTests.relayComputerStartsAColdReactorAndHoldsItsTarget`: brings a cold tokamak to a burn at 50% without an operator.
- The fission path has no dedicated test. It uses the same lease API as the reactor console's AUTO mode.
- A parallel upstream change already added the 3x3 large press and depot, so this commit only adds the computer.
