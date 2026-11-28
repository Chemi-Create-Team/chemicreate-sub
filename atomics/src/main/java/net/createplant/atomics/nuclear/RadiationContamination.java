package net.createplant.atomics.nuclear;

import net.createplant.atomics.registry.CERadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.fluids.FluidStack;

public final class RadiationContamination {
    private RadiationContamination() {}

    public static BlockPos cell(BlockPos p) {
        return new BlockPos(
                Math.floorDiv(p.getX(), 4) * 4, p.getY(), Math.floorDiv(p.getZ(), 4) * 4);
    }

    public static void deposit(ServerLevel level, BlockPos pos, Radionuclides amount) {
        if (amount.total() <= 0) return;
        var data = RadiationWorldData.get(level);
        var at = cell(pos);
        var chunk = data.soil.computeIfAbsent(ChunkPos.asLong(at), k -> new java.util.HashMap<>());
        chunk.put(
                at,
                chunk.getOrDefault(at, Radionuclides.ZERO)
                        .plus(amount, RadiationSources.now(level)));
        data.setDirty();
    }

    public static Radionuclides at(ServerLevel level, BlockPos pos) {
        var chunk = RadiationWorldData.get(level).soil.get(ChunkPos.asLong(pos));
        return chunk == null
                ? Radionuclides.ZERO
                : chunk.getOrDefault(cell(pos), Radionuclides.ZERO).at(RadiationSources.now(level));
    }

    public static Radionuclides take(ServerLevel level, BlockPos pos, double fraction) {
        var at = cell(pos);
        var data = RadiationWorldData.get(level);
        var chunk = data.soil.get(ChunkPos.asLong(at));
        if (chunk == null) return Radionuclides.ZERO;
        var amount = chunk.getOrDefault(at, Radionuclides.ZERO).at(RadiationSources.now(level));
        var removed = amount.scale(Math.clamp(fraction, 0, 1));
        var left = amount.scale(1 - Math.clamp(fraction, 0, 1));
        if (left.total() < .001) chunk.remove(at);
        else chunk.put(at, left);
        if (chunk.isEmpty()) data.soil.remove(ChunkPos.asLong(at));
        if (removed.total() > 0) data.setDirty();
        return removed;
    }

    public static void spill(
            net.minecraft.world.level.Level level, BlockPos pos, FluidStack fluid) {
        if (net.createplant.mixture.MixtureHelper.isMixture(fluid)) {
            for (var part : net.createplant.mixture.MixtureHelper.components(fluid)) spill(level, pos, part);
            return;
        }
        if (level instanceof ServerLevel server && RadiationSources.profile(fluid) != null)
            deposit(
                    server,
                    pos,
                    RadiationSources.material(fluid, RadiationSources.now(level))
                            .at(RadiationSources.now(level))
                            .scale(fluid.getAmount() / 1000d));
    }

    public static void stain(ItemStack stack, Radionuclides amount, long now) {
        if (!stack.isEmpty() && amount.total() > 0)
            stack.set(
                    CERadiation.SURFACE.get(),
                    stack.getOrDefault(CERadiation.SURFACE.get(), Radionuclides.ZERO)
                            .plus(amount, now));
    }

    public static boolean dirty(net.minecraft.world.entity.player.Player player) {
        if (player.getData(CERadiation.EXPOSURE).getDouble("SkinBq") > .001
                || player.getData(CERadiation.EXPOSURE).getDouble("ShoeBq") > .001) return true;
        for (var worn : player.getArmorSlots())
            if (worn.getOrDefault(CERadiation.SURFACE.get(), Radionuclides.ZERO).total() > .001)
                return true;
        return false;
    }

    public static boolean wash(ServerPlayer player, double fraction) {
        long now = RadiationSources.now(player.level());
        var old = player.getData(CERadiation.EXPOSURE);
        var next = old.copy();
        var skin = RadiationWorldData.read(next.getCompound("Skin")).at(now);
        var shoe = RadiationWorldData.read(next.getCompound("Shoes")).at(now);
        var removedMaterial = skin.plus(shoe, now).scale(fraction);
        double removed = removedMaterial.total();
        next.put("Skin", RadiationWorldData.write(skin.scale(1 - fraction)));
        next.put("Shoes", RadiationWorldData.write(shoe.scale(1 - fraction)));
        next.putDouble("SkinBq", skin.total() * (1 - fraction));
        next.putDouble("ShoeBq", shoe.total() * (1 - fraction));
        for (var worn : player.getArmorSlots()) {
            var soil = worn.getOrDefault(CERadiation.SURFACE.get(), Radionuclides.ZERO).at(now);
            removed += soil.total() * fraction;
            removedMaterial = removedMaterial.plus(soil.scale(fraction), now);
            if (soil.total() > 0) worn.set(CERadiation.SURFACE.get(), soil.scale(1 - fraction));
        }
        if (removed <= 0) return false;
        // Wash water leaves a contaminated drainage patch; the dose already received is never
        // washed away.
        deposit(player.serverLevel(), player.blockPosition().below(), removedMaterial);
        player.setData(CERadiation.EXPOSURE, next);
        return true;
    }
}
