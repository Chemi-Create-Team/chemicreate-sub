package net.createplant.event;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createplant.CreatePlant;
import net.createplant.item.StatefulBuckets;
import net.createplant.thermal.FluidIdentity;
import net.createplant.thermal.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

@EventBusSubscriber(modid=CreatePlant.MOD_ID)
public final class LegacyFluidBlocks {
    private static final Map<ServerLevel,LinkedHashSet<ChunkPos>> PENDING=new WeakHashMap<>();
    private record LegacyCell(BlockPos position,BlockState state) { }

    private LegacyFluidBlocks() { }

    private static boolean legacy(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && FluidIdentity.legacy(state.getFluidState().getType());
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk))return;
        for(var section:chunk.getSections()) {
            if(!section.maybeHas(LegacyFluidBlocks::legacy))continue;
            synchronized(PENDING) { PENDING.computeIfAbsent(level,ignored->new LinkedHashSet<>()).add(chunk.getPos()); }
            return;
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        List<ChunkPos> work=new ArrayList<>(2);
        synchronized(PENDING) {
            var waiting=PENDING.get(level);
            if(waiting==null)return;
            var entries=waiting.iterator();
            while(entries.hasNext() && work.size()<2) { work.add(entries.next());entries.remove(); }
        }
        for(var position:work) {
            var chunk=level.getChunkSource().getChunkNow(position.x,position.z);
            if(chunk!=null)migrate(level,chunk);
        }
    }

    private static void migrate(ServerLevel level,LevelChunk chunk) {
        var changes=new ArrayList<LegacyCell>();
        var sections=chunk.getSections();
        for(int index=0;index<sections.length;index++) {
            var section=sections[index];
            if(!section.maybeHas(LegacyFluidBlocks::legacy))continue;
            int baseY=chunk.getSectionYFromSectionIndex(index)*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                var state=section.getBlockState(x,y,z);
                if(legacy(state))changes.add(new LegacyCell(new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z),state));
            }
        }
        for(var cell:changes)migrate(level,cell);
    }

    private static void migrate(ServerLevel level,LegacyCell cell) {
        var position=cell.position();
        var original=cell.state().getFluidState();
        if(level.getBlockState(position)!=cell.state())return;
        var fluid=new FluidStack(original.getType(),1000);
        if(fluid.getFluid()==original.getType())return;
        if(StatefulBuckets.defaultState(fluid) && !FluidState.gas(fluid) && fluid.getFluid() instanceof FlowingFluid flowing) {
            var replacement=flowing.getSource(false).createLegacyBlock();
            if(replacement.hasProperty(LiquidBlock.LEVEL)) {
                level.setBlock(position,replacement.setValue(LiquidBlock.LEVEL,cell.state().getValue(LiquidBlock.LEVEL)),Block.UPDATE_ALL);
                return;
            }
        }
        var bucket=original.isSource() ? FluidUtil.getFilledBucket(fluid) : net.minecraft.world.item.ItemStack.EMPTY;
        if(original.isSource() && bucket.isEmpty())return;
        if(!level.setBlock(position,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL) || bucket.isEmpty())return;
        var recovered=new ItemEntity(level,position.getX()+.5,position.getY()+.5,position.getZ()+.5,bucket);
        recovered.setDefaultPickUpDelay();
        level.addFreshEntity(recovered);
    }
}
