# Fusion fuel integration

- `CEFusionFuel.register(modEventBus)` before CEWorldFluids.register.
- Creative base: COLD_BOX, REGENERATOR. Materials: LITHIUM_TARGET, IRRADIATED_TARGET, HELIUM_FEED_BUCKET; heavy_water/deuterium_depleted_water buckets come from CEWorldFluids entries.
- ElectrolysisClient: register COLD_BOX_ENTITY with KineticBlockEntityRenderer (front shaft rotates) and SingleAxisRotatingVisual if used. Controller getRotationAxis uses facing.
- New ColdBox getters allow MachineReadings status/formed/coldKelvin (K, no fake percent), watts (W), cycleHelium(0..1000), gas/liquid/water/warm tanks. Add keys only at the end of KEYS.
- FluidThermal.MATERIALS: exact proposal: `Map.entry("heavy_water",new double[]{1.1056,4210}), Map.entry("deuterium_depleted_water",new double[]{1,4180}), Map.entry("helium_recovery_feed",new double[]{.000437,2000})`.
- FluidPhase.TABLE: `entry("heavy_water",20.0276,374.55,2070,643.85,216.7,1.1056)`, `entry("deuterium_depleted_water",18.015,373.15,2257,647.1,220.64,1)`, `entry("helium_recovery_feed",10.005,30,100,50,20,.1)`; helium feed is a gameplay pseudo-mixture. No new phase-change pair.
- Merge generated lang fragments and Ponder catalog fragment in tools/data. Existing assets unchanged.
