package net.createplant.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFusion;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class CasingModels {
    private static final Map<List<CTSpriteShiftEntry>, List<Supplier<? extends Block>>> CASINGS = new java.util.LinkedHashMap<>();

    private CasingModels() { }

    private static CTSpriteShiftEntry shift(String texture) {
        return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL, CreatePlant.location("block/" + texture), CreatePlant.location("block/" + texture + "_connected"));
    }

    /** Reactor plating comes in three randomly chosen shades, each with its own connected sheet. */
    private static List<CTSpriteShiftEntry> plated(String texture) {
        return List.of(shift(texture), shift(texture + "_1"), shift(texture + "_2"));
    }

    public static void init() {
        CASINGS.put(List.of(shift("diorite_casing")), List.<Supplier<? extends Block>>of(CEBlocks.DIORITE_CASING));
        // Ports and the controller show the reactor casing on their sides, so they join the same outer skin.
        CASINGS.put(plated("fusion/reinforced_casing"), List.<Supplier<? extends Block>>of(CEFusion.REINFORCED_CASING, CEFusion.CONTROLLER,
            CEFusion.DEUTERIUM_PORT, CEFusion.TRITIUM_PORT, CEFusion.ASH_PORT, CEFusion.HELIUM_INLET, CEFusion.HELIUM_OUTLET,
            CEFusion.COOLANT_INLET, CEFusion.COOLANT_OUTLET));
        CASINGS.put(plated("fusion/breeding_blanket"), List.<Supplier<? extends Block>>of(CEFusion.BREEDING_BLANKET));
        CASINGS.put(plated("fusion/vacuum_vessel"), List.<Supplier<? extends Block>>of(CEFusion.VACUUM_VESSEL));
        CASINGS.put(plated("fusion/divertor"), List.<Supplier<? extends Block>>of(CEFusion.DIVERTOR));
        CASINGS.put(List.of(shift("fusion/viewport")), List.<Supplier<? extends Block>>of(CEFusion.VIEWPORT));
        CASINGS.put(List.of(shift("fusion/toroidal_field_coil")), List.<Supplier<? extends Block>>of(CEFusion.TOROIDAL_FIELD_COIL));
        CASINGS.put(List.of(shift("fusion/central_solenoid")), List.<Supplier<? extends Block>>of(CEFusion.CENTRAL_SOLENOID));
    }

    public static void setup() {
        CASINGS.forEach((shifts, blocks) -> blocks.forEach(block -> CreateClient.CASING_CONNECTIVITY.makeCasing(block.get(), shifts.getFirst())));
    }

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        CASINGS.forEach((shifts, blocks) -> {
            for (var block : blocks) for (var state : block.get().getStateDefinition().getPossibleStates())
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (location, model) -> new CTModel(model, new ShadedCTBehaviour(shifts)));
        });
    }

    /** Joins like a Create casing and picks the connected sheet that belongs to the shade drawn on each face. */
    private static final class ShadedCTBehaviour extends EncasedCTBehaviour {
        private final List<CTSpriteShiftEntry> shifts;

        private ShadedCTBehaviour(List<CTSpriteShiftEntry> shifts) {
            super(shifts.getFirst());
            this.shifts = shifts;
        }

        @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
            for (var shift : shifts) if (shift.getOriginal() == sprite) return shift;
            return shifts.getFirst();
        }
    }
}
