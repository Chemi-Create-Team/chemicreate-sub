# Supplied mineral sprites

The user supplied these sheets on 2026-11-12 and chose the pictured shapes over conflicting item-pixel counts in the accompanying table.

- `ore-sheet.png`: 4 columns × 4 rows; 248 px pitch, 240 px square tiles enlarged from 16×16. First two rows: bauxite, pentlandite, ilmenite, zircon, chromite, molybdenite, uranium, halite. Last two rows: their deepslate variants. Backgrounds `#7f7f7f` / `#4d4d50` are presentation backdrops; the game models retain Minecraft's stone / deepslate beneath transparent overlays.
- `mineral-sheet.png`: 4 columns × 2 rows; 264 px pitch, 256 px square tiles enlarged from 16×16. Uraninite, bauxite, pentlandite, ilmenite, zircon, chromite, molybdenite, fluorite. Presentation background: `#8b8b8b`.

Run `python3 tools/mineral_reference_assets.py` from any directory. It invokes ImageMagick `convert` to crop, sample without interpolation and remove only the presentation background. It does not redraw the art. The metal, chlor-alkali and uranium asset generators call this same importer for their respective materials.
