# Open cylinder tank

Use tank casing blocks for the filled floor and one-block-thick cylindrical wall. The casing uses Create Basin placement sounds. There is no controller block: the tank forms automatically when the casing that completes the structure is placed, with a chime, sparkles along the rim and a puff of cloud over the floor. The tank has no roof.

The supported diameter is 4 to 32 blocks and the wall height is 2 to 64 blocks above the floor. For local coordinates `0 <= x,z < diameter`, the floor footprint is defined by `dx = 2*x - (diameter - 1)`, `dz = 2*z - (diameter - 1)`, and `dx*dx + dz*dz <= diameter*diameter`. This integer-grid circle is symmetric for odd and even diameters and has exactly the requested bounding width. Its outer ring is the set of footprint cells adjacent by a cardinal step to a cell outside the footprint. The remaining footprint cells are the tank interior. Tank casing blocks in the surrounding square outside the circle are rejected, so square floors do not pass validation. The interior cells must be air. The layer above the wall must contain no tank casing; pipes, pumps and other blocks may rest on the rim or above the opening.

Capacity is 8,000 mB per block of the whole cylinder: the interior cells plus every floor and wall casing, which is the footprint cell count multiplied by wall height plus one. The tank stores one fluid type and rejects incompatible fluids. The same fluid arriving at a different temperature is accepted and mixed by heat capacity, so pumped flows with drifting temperatures keep filling the tank.

Every floor and wall casing exposes the tank's NeoForge fluid handler on all faces. Connect a Create fluid pipe and a driven mechanical pump, or use a fluid container, on any casing to transfer fluid. Right-click any casing with an empty hand to revalidate and display status, diameter, height, capacity, and stored amount. Other items keep their normal use, so blocks can be placed against a casing without sneaking.

The stored fluid is drawn as a surface across the interior at the height of its fill fraction. The owning casing sends its shape and fluid to tracking clients when the tank forms or breaks, and at most every 8 ticks while the amount changes; clients animate between updates. The surface uses Create's fluid render type and tiled still texture, one strip per interior row.

The opt-in client check `-Dcreateplant.clientQualityCheck=true -Dcreateplant.tankClientCheck=true` builds two diameter-8 tanks, fills them with water and lava through the fluid capability, and saves `quality-open-tank-fluid.png`.

The center floor casing owns the formed structure and its fluid; other casings only refer to it. The structure is searched when a casing is placed and when a casing is used, and the owning casing rechecks its known footprint every 20 server ticks and immediately after a member casing is removed. Invalid structures stop fluid transfer while retaining their stored fluid. A repaired tank announces its formation again, while a tank that was already formed before a reload forms silently. A casing removed while holding fluid hands it to an adjacent casing; the next tank formed around that casing absorbs it up to its capacity. Fluid is lost only when the last casing holding it has no adjacent casing.

Open the tank casing's Ponder menu to view construction, pump connection and one floor-and-wall pattern for every diameter from 4 through 32.

The casing uses the supplied `スクリーンショット 2026-10-24 203200.png` as its texture on every face. The source image is 160×160 pixels and is downscaled with high-quality bicubic filtering to 32×32 pixels for the block texture.

tank_casing is crafted from eight iron ingots in a ring pattern (three across the top and bottom, two at the sides).
