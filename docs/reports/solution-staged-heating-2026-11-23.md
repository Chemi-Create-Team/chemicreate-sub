# Solutions leave solids when heated (2026-11-23)

`tools/generate_solution_staging.py` writes the recipes, powder manifest entries and lang keys. Run it after the other generators.

Heat levels: `heated` (kindled burner) covers evaporation and dehydration (about 100-500 C); `superheated` (seething) is the 700 C+ calcination.

- Evaporation (heated): solution -> crystals/hydrate + steam, for zirconium, titanyl, aluminium, iron, copper, zinc, tin sulfates, lead chloride,
  uranyl and plutonium nitrate, sodium aluminate, caustic soda, ammonium chloride, brine (-> salt), wastewater (-> treatment sludge).
- Zirconium: hydrate -> zirconyl sulfate + SO3 + steam (heated) -> zirconia + SO3 (superheated).
- Titanyl sulfate hydrate -> titanium dioxide, aluminium sulfate hydrate -> alumina, iron sulfate hydrate -> iron oxide red (SO2 + SO3): superheated.
- Uranyl nitrate hydrate -> uranium trioxide, plutonium nitrate crystals -> plutonium dioxide: heated, as before.
- Not given a solid: acids, hypochlorite, antifreeze, formaldehyde, pesticide, liquid fertiliser, spent-fuel liquors (volatile or no defined solute).
- Unverified in game; the one-step recipes `zirconia`, `titanium_dioxide`, `uranium_trioxide` and `plutonium_oxide_recovery` now take the crystal item.
