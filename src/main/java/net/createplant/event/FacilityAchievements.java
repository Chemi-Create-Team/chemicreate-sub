package net.createplant.event;

import java.util.List;
import java.util.function.Predicate;
import net.createplant.CreatePlant;
import net.createplant.block.entity.*;
import net.createplant.registry.CEHeating;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Grants the "facilities/" advancements to players standing near a finished multiblock. The structures are checked in about
 * the loaded chunks within two of round each player twice a second, using each controller's own formed check, so no structure needs a hook.
 * ponytail: scans block entities per player; a server with many players in one big plant scans it once each. Upgrade path: a formed-event on the controllers.
 */
public final class FacilityAchievements {
    private record Facility(String id, Predicate<BlockEntity> built) { }
    private static final int RANGE = 24, CHUNKS = 2, PERIOD = 40;
    private static final String HUB = "hub", CRITERION = "built";
    private static final List<Facility> ALL = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
        of("fractionating_column", FractionatingColumnBlockEntity.class, FractionatingColumnBlockEntity::ready),
        of("ammonia_separator", AmmoniaSeparatorBlockEntity.class, AmmoniaSeparatorBlockEntity::ready),
        of("high_pressure_separator", HighPressureSeparatorBlockEntity.class, separator -> separator.isController() && separator.complete()),
        of("reaction_tower", ReactionTowerBlockEntity.class, tower -> tower.owner() == tower),
        of("gas_holder", GasHolderBlockEntity.class, GasHolderBlockEntity::formed),
        of("lidded_vessel", LiddedVesselBlockEntity.class, LiddedVesselBlockEntity::formed),
        of("absorption_tower", AbsorptionTowerBlockEntity.class, AbsorptionTowerBlockEntity::formed),
        of("heat_exchanger", ReactorMachineBlockEntity.class, be -> be.formed() && be.getBlockState().is(CEHeating.EXCHANGER_CONTROLLER)),
        of("large_heat_exchanger", ReactorMachineBlockEntity.class, be -> be.formed() && be.getBlockState().is(CEHeating.LARGE_EXCHANGER_CONTROLLER)),
        of("jacketed_pipe", ReactorMachineBlockEntity.class, be -> be.formed() && be.getBlockState().is(CEHeating.JACKET_CONTROLLER)),
        of("cooling_tower", CoolingTowerBlockEntity.class, CoolingTowerBlockEntity::formed),
        of("condenser", CondenserWaterboxBlockEntity.class, CondenserWaterboxBlockEntity::formed),
        of("cold_box", CryogenicColdBoxBlockEntity.class, CryogenicColdBoxBlockEntity::formed),
        of("electrolysis_furnace", ElectrolysisFurnaceBlockEntity.class, ElectrolysisFurnaceBlockEntity::formed),
        of("electrolysis_cell", ElectrolysisControllerBlockEntity.class, ElectrolysisControllerBlockEntity::formed),
        of("gasifier", GasifierBlockEntity.class, GasifierBlockEntity::formed),
        of("pressure_pump", HighPressurePumpBlockEntity.class, pump -> pump.drive() && pump.formed()),
        of("clarifier", WetProcessBlockEntity.class, WetProcessBlockEntity::formed),
        of("flare", FlareTipBlockEntity.class, FlareTipBlockEntity::formed)));
    private FacilityAchievements() { }
    /** Lets an addon add a facility whose advancement is facilities/<id>. */
    public static <T extends BlockEntity> void register(String id, Class<T> type, Predicate<T> built) { ALL.add(of(id, type, built)); }
    private static <T extends BlockEntity> Facility of(String id, Class<T> type, Predicate<T> built) {
        return new Facility(id, be -> type.isInstance(be) && built.test(type.cast(be)));
    }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIOD != 0) return;
        var pending = ALL.stream().filter(facility -> !done(player, facility.id)).toList();
        if (pending.isEmpty()) return;
        var chunks = player.serverLevel().getChunkSource();
        for (int x = -CHUNKS; x <= CHUNKS; x++) for (int z = -CHUNKS; z <= CHUNKS; z++) {
            var chunk = chunks.getChunkNow(player.chunkPosition().x + x, player.chunkPosition().z + z);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values()) {
                if (be.getBlockPos().distSqr(player.blockPosition()) > RANGE * RANGE) continue;
                for (var facility : pending) if (facility.built.test(be) && award(player, facility.id)) award(player, HUB);
            }
        }
    }
    private static AdvancementHolder holder(ServerPlayer player, String id) {
        return player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, "facilities/" + id));
    }
    private static boolean done(ServerPlayer player, String id) {
        var holder = holder(player, id);
        return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    private static boolean award(ServerPlayer player, String id) {
        var holder = holder(player, id);
        return holder != null && player.getAdvancements().award(holder, CRITERION);
    }
}
