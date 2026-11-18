package net.createplant.client;

import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.CoolerRadiatorBlock;
import net.createplant.block.CoolingFanBlock;
import net.createplant.block.CoolingFanShroudBlock;
import net.createplant.registry.CEProcessBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Joins the side faces of formed radiators into one framed 3x3 panel per fan section. */
public final class FinFanModels {
    /** A 3x3 sheet: the tile is picked from which neighbours belong to the same fan section. */
    private static final CTType SECTION = new CTType() {
        private final ResourceLocation id = CreatePlant.location("fin_fan_section");
        @Override public ResourceLocation getId() { return id; }
        @Override public int getSheetSize() { return 3; }
        @Override public ConnectedTextureBehaviour.ContextRequirement getContextRequirement() {
            return ConnectedTextureBehaviour.ContextRequirement.builder().axisAligned().build();
        }
        @Override public int getTextureIndex(ConnectedTextureBehaviour.CTContext context) {
            int column = context.left ? context.right ? 1 : 2 : 0;
            int row = context.up ? context.down ? 1 : 2 : 0;
            return row * 3 + column;
        }
    };
    private static final CTSpriteShiftEntry[] PANELS = {shift("radiator_fin_panel"), shift("radiator_side_panel"), shift("radiator_end_panel")};

    private FinFanModels() { }

    /** Sprite shifts must be registered before the block atlas is stitched. */
    public static void init() { }

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var behaviour = new Behaviour();
        for (var state : CEProcessBlocks.COOLER_RADIATOR.get().getStateDefinition().getPossibleStates()) {
            if (state.getValue(CoolerRadiatorBlock.SECTION) != CoolerRadiatorBlock.Section.NONE)
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new CTModel(model, behaviour));
        }
    }

    private static CTSpriteShiftEntry shift(String name) {
        return CTSpriteShifter.getCT(SECTION, CreatePlant.location("block/fin_fan/" + name), CreatePlant.location("block/fin_fan/" + name + "_connected"));
    }

    /** The fan hub above a formed radiator, read from the fan or the orientation of the shroud over its column. */
    private static BlockPos hub(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (!state.is(CEProcessBlocks.COOLER_RADIATOR.get()))
            return null;
        int up = switch (state.getValue(CoolerRadiatorBlock.SECTION)) {
            case BOTTOM -> 3;
            case MIDDLE -> 2;
            case TOP -> 1;
            case NONE -> 0;
        };
        if (up == 0)
            return null;
        BlockPos top = pos.above(up);
        BlockState cover = level.getBlockState(top);
        if (cover.getBlock() instanceof CoolingFanBlock)
            return top;
        if (!(cover.getBlock() instanceof CoolingFanShroudBlock))
            return null;
        Direction facing = cover.getValue(CoolingFanShroudBlock.FACING);
        return switch (cover.getValue(CoolingFanShroudBlock.PART)) {
            case SIDE -> top.relative(facing);
            case CORNER -> top.relative(facing).relative(facing.getCounterClockWise());
            case NONE -> null;
        };
    }

    private static final class Behaviour extends ConnectedTextureBehaviour {
        @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
            for (var panel : PANELS) {
                if (panel.getOriginal() == sprite)
                    return panel;
            }
            return null;
        }

        @Override public CTType getDataType(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal() ? SECTION : null;
        }

        /** Right is where the face's U grows when seen from outside; the model UVs follow vanilla's per-face layout. */
        @Override public CTContext buildContext(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face, ContextRequirement requirement) {
            var context = new CTContext();
            BlockPos hub = hub(level, pos, state);
            if (hub == null)
                return context;
            Direction right = face.getCounterClockWise();
            context.up = hub.equals(hub(level, pos.above(), level.getBlockState(pos.above())));
            context.down = hub.equals(hub(level, pos.below(), level.getBlockState(pos.below())));
            context.right = hub.equals(hub(level, pos.relative(right), level.getBlockState(pos.relative(right))));
            context.left = hub.equals(hub(level, pos.relative(right.getOpposite()), level.getBlockState(pos.relative(right.getOpposite()))));
            return context;
        }
    }
}
