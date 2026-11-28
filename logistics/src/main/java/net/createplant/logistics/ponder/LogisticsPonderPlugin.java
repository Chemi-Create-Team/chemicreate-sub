package net.createplant.logistics.ponder;

import net.createplant.CreatePlant;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class LogisticsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreatePlant.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreatePlant.location("industrial_arm"), CreatePlant.location("welding_torch"), CreatePlant.location("welded_iron_beam"))
            .addStoryBoard("plant/industrial_arm", ArmPonderScenes::arm);
        helper.forComponents(CreatePlant.location("magnetic_core_memory"), CreatePlant.location("ferrite_core"), CreatePlant.location("enamelled_copper_wire"),
                CreatePlant.location("incomplete_magnetic_core_memory"), CreatePlant.location("industrial_arm"))
            .addStoryBoard("plant/industrial_arm", ArmPonderScenes::memory);
        helper.forComponents(CreatePlant.location("relay_computer"), CreatePlant.location("relay_rack"), CreatePlant.location("large_relay"))
            .addStoryBoard("plant/relay_computer", RelayPonderScenes::computer);
        helper.forComponents(CreatePlant.location("quarry_drive"))
            .addStoryBoard("crane/quarry_frame", QuarryPonderScenes::quarryFrame);
    }
}
