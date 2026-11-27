# 範囲の仕分け（2026-11-27）

本体の軸を「ハーバー・ボッシュ（アンモニア）と、そこから作れるもの」に戻すための仕分け案。コードは未変更。

- 対象は blockstates 310件（ブロックと流体）。アイテムだけのものは含まない。
- 名前の正規表現で機械的に振り分けた目安。境界のものは要確認。
  - 浸出（`*_leach*`）は硫酸の主な使い道なので、製鉄と一緒に切り出すかは要検討。
  - 電解槽（`electrolysis_*`）は食塩電解（水素・塩素）にも使うなら本体に残す。
- 気付いた点：硝酸を作るレシピがない（使うレシピは4つ）。アンモニア → 硝酸（オストワルト法）が抜けている。

### 切り出し：原子力・核融合（66）
biological_shield, biological_shield_coolant_outlet, biological_shield_drive, control_rod_controller, control_rod_link, deepslate_uranium_ore, deepslate_zircon_ore, deuterium_depleted_water, deuterium_gas, dissolved_spent_fuel, empty_waste_canister, extraction_column_outlet, extraction_column_shell, extraction_column_structure, extraction_pulse_plate, fuel_pool, fusion_ash_port, fusion_breeding_blanket, fusion_central_solenoid, fusion_console_panel, fusion_control_console, fusion_coolant_inlet, fusion_coolant_outlet, fusion_crane_drive, fusion_deuterium_port, fusion_divertor, fusion_helium_inlet, fusion_helium_outlet, fusion_motor_generator, fusion_reactor_controller, fusion_reinforced_casing, fusion_toroidal_field_coil, fusion_tritium_port, fusion_vacuum_pump, fusion_vacuum_vessel, fusion_viewport, gas_centrifuge, heavy_water, helium_gas, high_level_raffinate, plutonium_nitrate_solution, pulse_extraction_column, reactor_console_display, reactor_control_console, reactor_feed_bundle, reactor_fuel_channel, reactor_fuel_channel_tube, reactor_graphite, reactor_top_cap, serpentinite, slime_coolant, slime_coolant_feed, tbp_extraction_solvent, tributyl_phosphate, tritium_gas, uranium_ore, uranyl_nitrate_solution, vitrification_electrode, vitrification_melter, vitrification_refractory_shell, vitrified_waste_canister, zircon_ore, zirconia_refractory_bricks, zirconium_pipe, zirconium_pump, zirconium_sulfate_solution

### 切り出し：発電・電力（47）
black_large_flywheel, blue_large_flywheel, boosted_high_pressure_steam_turbine, boosted_steam_turbine, brown_large_flywheel, coil_winder, condenser_cooling_inlet, condenser_cooling_outlet, condenser_shell, condenser_tube_bundle, condenser_waterbox, cooling_tower_basin, cooling_tower_cold_outlet, cooling_tower_distributor, cooling_tower_fill, cooling_tower_hot_inlet, cooling_tower_makeup_inlet, cooling_tower_shell, cyan_large_flywheel, electric_pump, fuel_cell, gray_large_flywheel, green_large_flywheel, high_pressure_steam_turbine, high_pressure_turbine_end, high_pressure_turbine_part, large_flywheel, lead_acid_battery_casing, lead_acid_battery_terminal, light_blue_large_flywheel, light_gray_large_flywheel, lime_large_flywheel, lithium_ion_battery_casing, lithium_ion_battery_terminal, low_pressure_turbine_end, magenta_large_flywheel, natural_draft_cooling_tower, orange_large_flywheel, pink_large_flywheel, purple_large_flywheel, red_large_flywheel, relay_computer, relay_rack, steam_turbine, transmission_tower, white_large_flywheel, winder_brake

### 切り出し：物流・採掘（7）
blasting_charge, crane_drive, industrial_arm, industrial_dispatch_board, large_depot, quarry_drive, vault_hatch

### 切り出し：製鉄・金属（59）
aluminium_sulfate_solution, bauxite_ore, cassiterite_ore, chromite_ore, clarified_water, clarifier_center_drive, clarifier_feed_port, continuous_factory_frame, continuous_factory_outlet, converter_gas, converter_shell, converter_trunnion_bearing, copper_leach_slurry, copper_sulfate_solution, cubic_press, deepslate_bauxite_ore, deepslate_cassiterite_ore, deepslate_chromite_ore, deepslate_galena_ore, deepslate_ilmenite_ore, deepslate_molybdenite_ore, deepslate_pentlandite_ore, deepslate_spodumene_ore, desulfurized_hot_metal, electrolysis_cell, electrolysis_cell_controller, electrolysis_electrode_head, electrolysis_furnace_anode, electrolysis_furnace_busbar, electrolysis_furnace_shell, galena_ore, ilmenite_ore, iron_leach_slurry, iron_sulfate_solution, large_mechanical_press, large_press_frame, lead_chloride_solution, lead_leach_slurry, molten_pig_iron, molten_stainless_steel, molten_steel, molybdenite_ore, overflow_weir, pentlandite_ore, rolling_mill, rolling_stand, rotary_kiln, rotary_kiln_exhaust, sodium_aluminate_solution, spodumene_ore, tin_leach_slurry, tin_sulfate_solution, titanium_block, titanyl_sulfate_solution, torpedo_tank, triiron_tetroxide_pile, underflow_drain, zinc_leach_slurry, zinc_sulfate_solution

### 切り出し：農薬（6）
agricultural_sprayer, crop_protectant, crude_methylphosphonate, diluted_pesticide, dimethyl_methylphosphonate, trimethyl_phosphite

### 保留：石油化学（36）
acetone, antifreeze, benzene, btx, butanol, butyraldehyde, chlorobenzene, chlorotoluene, cooled_hydrocracker_effluent, cooled_hydrotreater_effluent, cooled_olefins, cumene, cumene_process_liquor, ethylbenzene, ethylene_dichloride, ethylene_glycol, flare_tip, formaldehyde_solution, fractionating_column, heavy_oil, hexane, low_sulfur_diesel, lubricating_oil, methanol, molded_polymer, molten_paraffin, molten_polyethylene, molten_polypropylene, naphtha, phenol, recovered_solvent, styrene, synthetic_crude_oil, synthetic_naphtha, toluene, xylene

### 残す：アンモニア中核（32）
air_gas, ammonia_gas, ammonia_separator, ammonium_chloride_liquor, argon_gas, cryogenic_cold_box_drive, cryogenic_regenerator, cryogenic_tank, gas_holder, gas_receiver, gas_tank_container, heat_exchanger_controller, high_pressure_pump, high_pressure_separator, hydrogen_gas, large_absorption_tower, large_absorption_tower_ring, large_heat_exchanger_controller, liquid_fertilizer, nitric_acid, nitrogen_gas, oxygen_gas, pipe_pressure_gauge, pressure_pump_casing, pressure_pump_intake, pressure_reducing_valve, pressure_tank, reaction_tower, reciprocating_compressor, reciprocating_cylinder, reciprocating_drive_shaft, steam_gasifier

### 残す：硫酸・無機（15）
brine, caustic_soda_solution, deepslate_halite_ore, dilute_sulfuric_acid, halite_ore, hydrochloric_acid, neutralized_wastewater, phosphoric_acid, relief_scrubber, sodium_hypochlorite_solution, sulfur_scrubber_liquor, sulfuric_acid, sulfurous_acid, wastewater, wastewater_slurry

### 残す：容器・配管・計装・建材（42）
ceramic_pipe, charred_wood, control_valve_actuator, cooler_radiator, cooling_fan, cooling_fan_shroud, corrosion_resistant_floor, corrosion_resistant_floor_slab, diorite_casing, facility_constructor, flow_regulator, gas_detector, gas_recovery_duct, horizontal_fluid_tank, hose_connector, industrial_concrete, industrial_concrete_slab, industrial_concrete_stairs, industrial_glass, jacketed_pipe, jacketed_pipe_controller, lidded_vessel, nickel_alloy_pipe, nickel_alloy_pump, pid_controller, pid_instrument_rack, pipe_flow_meter, pipe_thermometer, process_transmitter, silica_refractory_bricks, stainless_boiler, stainless_steel_block, stainless_steel_encased_pipe, stainless_steel_glass_pipe, stainless_steel_pipe, stainless_steel_pump, stainless_steel_tank, steel_block, stirred_tank, tank_casing, tube_piped_tank, vessel_stirrer

