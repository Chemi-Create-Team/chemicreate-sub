package net.createplant.block.entity;

import net.createplant.block.TankFootprint;
import net.createplant.block.TankStructure;
import net.createplant.block.WetProcessStructure.RimPort;
import net.createplant.block.WetProcessPartBlock.Role;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One casing of an open-top cylindrical tank.
 *
 * There is no controller block: the center floor casing of a recognized tank owns the structure and its
 * fluid, and every member casing exposes that fluid through the ordinary NeoForge FluidHandler capability,
 * so Create pipes and mechanical pumps can connect to any face of the tank. A casing removed while holding
 * fluid hands it to an adjacent casing, which keeps it until a tank formed around it absorbs it again.
 * Sea water left in a tank on the coast under the open sun dries into salt, taken out through any casing.
 */
public class TankCasingBlockEntity extends BlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> text,boolean sneaking) {
        var drive = clarifier();
        if (drive != null) return drive.addToGoggleTooltip(text, sneaking);
        text.add(getBlockState().getBlock().getName());
        var tank=getTank();
        if (tank!=null && !tank.fluid.isEmpty()) text.add(tank.fluid.getHoverName().copy().append(" · ").append(FluidThermal.readout(tank.fluid)));
        net.createplant.thermal.TankGrade.OPEN.describe(text);
        net.createplant.compat.display.MachineReadings.compact(text);return true;
    }
    public static final int MB_PER_BLOCK = 8_000;
    private static final int REVALIDATE_INTERVAL = 20;
    private static final int SYNC_INTERVAL = 8;
    /** Water evaporated per floor cell each second in full sun, and the evaporated water that leaves one salt. */
    public static final int EVAPORATION_PER_CELL = 1;
    public static final int WATER_PER_SALT = 1000;
    private static final int SALT_LIMIT = 64;
    private static final Direction[] HANDOFF_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    private FluidStack fluid = FluidStack.EMPTY;
    private TankStructure.Shape ownedShape;
    private boolean formed;
    private boolean announced;
    private boolean revalidate;
    private String invalidReason = "floor";
    private int generation;
    private TankCasingBlockEntity owner;
    private int ownerGeneration;
    private boolean syncPending;
    private long nextSync;
    private final LerpedFloat fluidLevel = LerpedFloat.linear().startWithValue(0);
    private boolean levelShown;
    private final IFluidHandler fluidCapability = new FluidPort();
    private int salt;
    private int evaporated;
    private final net.createplant.thermal.TankHeating heating=new net.createplant.thermal.TankHeating();
    private final IItemHandler saltCapability = new SaltPort();
    private BlockPos clarifierDrive;

    public TankCasingBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntityTypes.TANK_CASING.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEBlockEntityTypes.TANK_CASING.get(),
            TankCasingBlockEntity::fluidPort);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEBlockEntityTypes.TANK_CASING.get(),
            TankCasingBlockEntity::itemPort);
    }

    public WetProcessBlockEntity clarifier() {
        if (clarifierDrive != null && level != null && level.hasChunkAt(clarifierDrive)
            && level.getBlockEntity(clarifierDrive) instanceof WetProcessBlockEntity drive && drive.rimPort(worldPosition) != null) return drive;
        return null;
    }

    public void bindClarifier(BlockPos drive) {
        if (drive.equals(clarifierDrive)) return;
        clarifierDrive = drive.immutable();
        setChanged();
        if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public IFluidHandler fluidPort(Direction side) {
        var drive = clarifier();
        if (drive == null) return fluidCapability;
        var port = drive.rimPort(worldPosition);
        if (side != port.face(drive.front())) return null;
        return switch (port) {
            case OVERFLOW -> drive.fluidPort(Role.OVERFLOW);
            case SLURRY_INLET -> drive.fluidPort(Role.FEED);
            default -> null;
        };
    }

    public IItemHandler itemPort(Direction side) {
        var drive = clarifier();
        if (drive == null) return saltCapability;
        var port = drive.rimPort(worldPosition);
        if (port == RimPort.FLOCCULANT_INLET && side == port.face(drive.front())) return drive.itemPort(Role.FEED);
        return port == RimPort.UNDERFLOW && (side == Direction.DOWN || side == port.face(drive.front())) ? drive.itemPort(Role.UNDERFLOW) : null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TankCasingBlockEntity be) {
        // Only the owning center casing rechecks its known footprint; member casings stay idle.
        if (be.ownedShape == null)
            return;
        boolean interval = level.getGameTime() % REVALIDATE_INTERVAL == 0;
        if (be.revalidate || interval)
            be.revalidateOwnedShape();
        if (interval) {
            be.dryInTheSun();
        }
        if(be.formed && Math.floorMod(level.getGameTime()+pos.asLong(),net.createplant.thermal.TankHeating.INTERVAL)==0) {
            var base=new java.util.ArrayList<BlockPos>();var shape=be.ownedShape;
            for(int x=0;x<shape.diameter();x++)for(int z=0;z<shape.diameter();z++)if(TankFootprint.contains(x,z,shape.diameter()))base.add(shape.origin().offset(x,0,z));
            int previous=be.heating.progress();
            if(be.heating.tick(java.util.List.of(be.fluid),net.createplant.thermal.TankHeating.burnerBelow(level,base),ignored->be.getCapacity()))be.markFluidChanged();
            else if(previous!=be.heating.progress())be.setChanged();
        }
        // Fluid amounts change every tick while pumping, so the visible level is sent at a limited rate.
        if (be.syncPending && level.getGameTime() >= be.nextSync)
            be.sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TankCasingBlockEntity be) {
        if (be.ownedShape != null)
            be.fluidLevel.tickChaser();
    }

    /** Sea water (water in an ocean or beach biome) evaporates under a clear daytime sky and leaves its salt behind. */
    private void dryInTheSun() {
        if (!formed || salt >= SALT_LIMIT || fluid.isEmpty() || fluid.getFluid() != Fluids.WATER || !level.isDay() || level.isRaining())
            return;
        BlockPos top = ownedShape.center().above(ownedShape.height() + 1);
        var biome = level.getBiome(top);
        if (!(biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) || !level.canSeeSky(top))
            return;
        int lost = Math.min(fluid.getAmount(), TankFootprint.blocks(ownedShape.diameter()) * EVAPORATION_PER_CELL);
        fluid.shrink(lost);
        if (fluid.isEmpty())
            fluid = FluidStack.EMPTY;
        evaporated += lost;
        salt = Math.min(SALT_LIMIT, salt + evaporated / WATER_PER_SALT);
        evaporated %= WATER_PER_SALT;
        markFluidChanged();
    }

    /** Rechecks the tank containing this casing, forming a new one when the casings now complete it. */
    public void refresh() {
        if (level == null || level.isClientSide() || clarifier() != null)
            return;
        TankCasingBlockEntity tank = getTank();
        if (tank == null && ownedShape != null)
            tank = this;
        if (tank != null) {
            tank.revalidateOwnedShape();
            if (tank.formed) {
                invalidReason = "";
                return;
            }
        }

        TankStructure.Result result = TankStructure.find(level, worldPosition);
        invalidReason = result.failure();
        if (result.shape() != null && level.getBlockEntity(result.shape().center()) instanceof TankCasingBlockEntity center)
            center.form(result.shape());
    }

    /** Called before the casing block is removed. */
    public void onCasingRemoved() {
        TankCasingBlockEntity tank = getTank();
        if (tank != null)
            tank.revalidate = true;
        if (level != null && salt > 0)
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), new ItemStack(CEChlorAlkali.SALT.get(), salt));
        salt = 0;
        if (level == null || fluid.isEmpty())
            return;
        for (Direction side : HANDOFF_ORDER) {
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof TankCasingBlockEntity neighbor
                    && neighbor.retain(fluid)) {
                fluid = FluidStack.EMPTY;
                return;
            }
        }
    }

    /** The formed tank this casing belongs to, or null while the structure is incomplete or unloaded. */
    public TankCasingBlockEntity getTank() {
        return owner != null && !owner.isRemoved() && owner.formed && owner.generation == ownerGeneration
            ? owner : null;
    }

    public boolean isFormed() { return formed; }
    public int getDiameter() { return formed ? ownedShape.diameter() : 0; }
    public int getHeight() { return formed ? ownedShape.height() : 0; }
    public int getCapacity() { return formed ? ownedShape.volumeBlocks() * MB_PER_BLOCK : 0; }
    public int getFluidAmount() { return fluid.getAmount(); }
    public String getInvalidReason() { return invalidReason; }
    public TankStructure.Shape getShape() { return formed ? ownedShape : null; }
    public FluidStack getFluid() { return fluid; }
    public int getSalt() { return salt; }

    /** The rendered fill fraction, chasing the last synchronized amount on the client. */
    public float getFluidLevel(float partialTick) {
        return fluidLevel.getValue(partialTick);
    }

    private void revalidateOwnedShape() {
        revalidate = false;
        TankStructure.Result result = TankStructure.inspect(level, ownedShape.origin(), ownedShape.diameter());
        if (result.shape() != null) {
            if (!formed || result.shape().height() != ownedShape.height())
                form(result.shape());
            return;
        }
        invalidReason = result.failure();
        if (formed) {
            formed = false;
            announced = false;
            setChanged();
            sync();
        }
    }

    private void form(TankStructure.Shape shape) {
        ownedShape = shape;
        formed = true;
        generation++;
        invalidReason = "";
        BlockPos origin = shape.origin();
        int diameter = shape.diameter();
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                if (!TankFootprint.contains(x, z, diameter))
                    continue;
                claim(origin.offset(x, 0, z));
                if (TankFootprint.isWall(x, z, diameter)) {
                    for (int y = 1; y <= shape.height(); y++)
                        claim(origin.offset(x, y, z));
                }
            }
        }
        if (!announced) {
            announced = true;
            showFormation(shape);
        }
        setChanged();
        sync();
    }

    private void claim(BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TankCasingBlockEntity member))
            return;
        member.owner = this;
        member.ownerGeneration = generation;
        if (member == this)
            return;
        if (member.ownedShape != null) {
            // A smaller or older tank inside this footprint is replaced by this one.
            member.ownedShape = null;
            member.formed = false;
            salt = Math.min(SALT_LIMIT, salt + member.salt);
            member.salt = 0;
            member.setChanged();
            member.sync();
        }
        if (member.fluid.isEmpty() || !fluid.isEmpty() && !FluidThermal.compatible(fluid, member.fluid))
            return;
        int moved = Math.min(member.fluid.getAmount(), getCapacity() - fluid.getAmount());
        if (moved <= 0)
            return;
        fluid = FluidThermal.add(fluid, member.fluid, moved);
        member.fluid.shrink(moved);
        if (member.fluid.isEmpty())
            member.fluid = FluidStack.EMPTY;
        member.setChanged();
    }

    private void markFluidChanged() {
        setChanged();
        syncPending = true;
    }

    private void sync() {
        if (level == null || level.isClientSide())
            return;
        syncPending = false;
        nextSync = level.getGameTime() + SYNC_INTERVAL;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private boolean retain(FluidStack stack) {
        if (!fluid.isEmpty() && !FluidThermal.compatible(fluid, stack))
            return false;
        fluid = FluidThermal.add(fluid, stack, stack.getAmount());
        setChanged();
        return true;
    }

    private void showFormation(TankStructure.Shape shape) {
        if (!(level instanceof ServerLevel server))
            return;
        BlockPos origin = shape.origin();
        int diameter = shape.diameter();
        double top = origin.getY() + shape.height() + 1.05;
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                if (TankFootprint.isWall(x, z, diameter))
                    server.sendParticles(ParticleTypes.END_ROD, origin.getX() + x + .5, top, origin.getZ() + z + .5,
                        2, .25, .1, .25, .02);
            }
        }
        BlockPos center = shape.center();
        double spread = diameter * .25;
        server.sendParticles(ParticleTypes.CLOUD, center.getX() + .5, center.getY() + 1.2, center.getZ() + .5,
            12, spread, .2, spread, .015);
        server.playSound(null, center.above(shape.height()), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS,
            .8f, 1.2f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        heating.write(tag,registries);
        if (!fluid.isEmpty())
            tag.put("Fluid", fluid.save(registries));
        if (ownedShape != null) {
            tag.putLong("TankOrigin", ownedShape.origin().asLong());
            tag.putInt("TankDiameter", ownedShape.diameter());
            tag.putInt("TankHeight", ownedShape.height());
            tag.putBoolean("Formed", announced);
        }
        if (salt > 0)
            tag.putInt("Salt", salt);
        if (evaporated > 0)
            tag.putInt("Evaporated", evaporated);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clarifierDrive = tag.contains("ClarifierDrive") ? BlockPos.of(tag.getLong("ClarifierDrive")) : null;
        heating.read(tag,registries);
        fluid = tag.contains("Fluid") ? FluidStack.parseOptional(registries, tag.getCompound("Fluid")) : FluidStack.EMPTY;
        boolean drawn = ownedShape != null;
        ownedShape = tag.contains("TankOrigin")
            ? new TankStructure.Shape(BlockPos.of(tag.getLong("TankOrigin")), tag.getInt("TankDiameter"),
                tag.getInt("TankHeight")) : null;
        // A tank that was already formed is rechecked silently after loading; only client updates carry "Active".
        announced = tag.getBoolean("Formed");
        salt = tag.getInt("Salt");
        evaporated = tag.getInt("Evaporated");
        formed = tag.getBoolean("Active") && ownedShape != null;
        int capacity = getCapacity();
        float target = capacity > 0 ? Math.min(1f, fluid.getAmount() / (float) capacity) : 0;
        // The first client update shows the current level; later ones animate toward it.
        if (!levelShown)
            fluidLevel.startWithValue(target);
        levelShown = true;
        fluidLevel.chase(target, .25f, LerpedFloat.Chaser.EXP);
        // Recompiling the section re-evaluates whether the renderer draws outside it.
        if (level != null && level.isClientSide() && drawn != (ownedShape != null))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // Only the owner's visible state is sent; residual fluid in member casings is not drawn.
        if (ownedShape != null)
            saveAdditional(tag, registries);
        if (clarifierDrive != null) tag.putLong("ClarifierDrive", clarifierDrive.asLong());
        tag.putBoolean("Active", formed);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private int fillTank(FluidStack resource, IFluidHandler.FluidAction action) {
        if (!fluid.isEmpty() && !FluidThermal.mixable(fluid, resource))
            return 0;
        int accepted = Math.min(resource.getAmount(), Math.max(0, getCapacity() - fluid.getAmount()));
        if (accepted <= 0)
            return 0;
        if (action.execute()) {
            fluid = FluidThermal.add(fluid, resource, accepted);
            markFluidChanged();
        }
        return accepted;
    }

    private FluidStack drainTank(int maxDrain, IFluidHandler.FluidAction action) {
        if (maxDrain <= 0 || fluid.isEmpty())
            return FluidStack.EMPTY;
        int drained = Math.min(maxDrain, fluid.getAmount());
        FluidStack result = fluid.copyWithAmount(drained);
        if (action.execute()) {
            fluid.shrink(drained);
            if (fluid.isEmpty())
                fluid = FluidStack.EMPTY;
            markFluidChanged();
        }
        return result;
    }

    private final class FluidPort implements IFluidHandler {
        @Override public int getTanks() { return 1; }

        @Override public FluidStack getFluidInTank(int tank) {
            TankCasingBlockEntity formedTank = getTank();
            return tank == 0 && formedTank != null ? formedTank.fluid.copy() : FluidStack.EMPTY;
        }

        @Override public int getTankCapacity(int tank) {
            TankCasingBlockEntity formedTank = getTank();
            return tank == 0 && formedTank != null ? formedTank.getCapacity() : 0;
        }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && getTank() != null && stack != null && !stack.isEmpty()
                && net.createplant.thermal.TankGrade.OPEN.accepts(stack);
        }

        @Override public int fill(FluidStack resource, FluidAction action) {
            TankCasingBlockEntity formedTank = getTank();
            return formedTank != null && resource != null && !resource.isEmpty() && net.createplant.thermal.TankGrade.OPEN.accepts(resource)
                ? formedTank.fillTank(resource, action) : 0;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            TankCasingBlockEntity formedTank = getTank();
            if (formedTank == null || resource == null || resource.isEmpty()
                    || !FluidThermal.compatible(formedTank.fluid, resource))
                return FluidStack.EMPTY;
            return formedTank.drainTank(resource.getAmount(), action);
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            TankCasingBlockEntity formedTank = getTank();
            return formedTank != null ? formedTank.drainTank(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    /** The dried salt of the tank: extraction only. */
    private final class SaltPort implements IItemHandler {
        @Override public int getSlots() { return 1; }

        @Override public ItemStack getStackInSlot(int slot) {
            TankCasingBlockEntity formedTank = getTank();
            return slot == 0 && formedTank != null && formedTank.salt > 0 ? new ItemStack(CEChlorAlkali.SALT.get(), formedTank.salt) : ItemStack.EMPTY;
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            TankCasingBlockEntity formedTank = getTank();
            int taken = slot == 0 && formedTank != null ? Math.min(amount, formedTank.salt) : 0;
            if (taken <= 0)
                return ItemStack.EMPTY;
            if (!simulate) {
                formedTank.salt -= taken;
                formedTank.setChanged();
            }
            return new ItemStack(CEChlorAlkali.SALT.get(), taken);
        }

        @Override public int getSlotLimit(int slot) { return SALT_LIMIT; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
