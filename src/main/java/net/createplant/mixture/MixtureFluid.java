package net.createplant.mixture;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** Cheap carrier identification, also safe while Minecraft's registries are bootstrapping. */
public interface MixtureFluid {
    final class Source extends BaseFlowingFluid.Source implements MixtureFluid {
        public Source(BaseFlowingFluid.Properties properties) { super(properties); }
    }
    final class Flowing extends BaseFlowingFluid.Flowing implements MixtureFluid {
        public Flowing(BaseFlowingFluid.Properties properties) { super(properties); }
    }
}
