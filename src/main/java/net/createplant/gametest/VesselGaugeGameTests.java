package net.createplant.gametest;

import java.util.ArrayList;
import net.createplant.CreatePlant;
import net.createplant.block.PipeGaugeBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Direct vessel taps, the unchanged inline mode and read-only linked measurements. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class VesselGaugeGameTests {
    private static final BlockPos BASE=new BlockPos(5,3,5), TAP=BASE.west();
    private static LiddedVesselBlockEntity vessel(GameTestHelper h) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)h.setBlock(BASE.offset(x,0,z),CETransport.LIDDED_VESSEL.get());
        var owner=((LiddedVesselBlockEntity)h.getBlockEntity(BASE)).getControllerBE();
        owner.getTankInventory().fill(FluidThermal.at(new FluidStack(Fluids.WATER,4000),400),FluidAction.EXECUTE);
        return owner;
    }
    private static PipeGaugeBlockEntity tap(GameTestHelper h) {
        h.setBlock(TAP,CETransport.PIPE_PRESSURE_GAUGE.get().defaultBlockState().setValue(PipeGaugeBlock.AXIS,Direction.Axis.X));
        return (PipeGaugeBlockEntity)h.getBlockEntity(TAP);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=40)
    public static void aWallPlacementSelectsTheStemAxisAndAnOutwardDial(GameTestHelper h) {
        vessel(h);var block=CETransport.PIPE_PRESSURE_GAUGE.get();var player=h.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
        BlockPos wall=h.absolutePos(BASE);
        var hit=new BlockHitResult(Vec3.atCenterOf(wall).add(-.5,0,0),Direction.WEST,wall,false);
        var placement=block.getStateForPlacement(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)));
        h.assertTrue(placement!=null && placement.getValue(PipeGaugeBlock.AXIS)==Direction.Axis.X,"Using a gauge on a wall selects the axis normal to that wall");
        h.setBlock(TAP,placement);BlockPos absolute=h.absolutePos(TAP);
        h.assertTrue(block.shouldRenderHeadOnFace(h.getLevel(),absolute,placement,Direction.WEST),"The direct tap exposes its dial away from the vessel");
        h.assertTrue(!block.shouldRenderHeadOnFace(h.getLevel(),absolute,placement,Direction.NORTH) && !block.shouldRenderHeadOnFace(h.getLevel(),absolute,placement,Direction.EAST),"A direct tap has no sideways or inward dial");
        h.setBlock(TAP.west(),com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState());
        h.assertTrue(block.mountedFace(h.getLevel(),absolute,placement)==null && block.shouldRenderHeadOnFace(h.getLevel(),absolute,placement,Direction.NORTH),"Continuing the pipe beyond the meter restores the existing perpendicular inline heads");
        h.succeed();
    }
    @GameTest(template="test_petrochemical",timeoutTicks=40)
    public static void linkedVesselDetailsAndSimulationPreserveAllStockAndFitFourLines(GameTestHelper h) {
        var owner=vessel(h);var gauge=tap(h);var lookup=h.getLevel().registryAccess();
        var data=owner.saveWithoutMetadata(lookup);var catalyst=new ItemStackHandler(1);
        catalyst.setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location("iron_catalyst"))));
        data.put("Catalyst",catalyst.serializeNBT(lookup));
        var charge=new ItemStackHandler(13);charge.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3));data.put("Charge",charge.serializeNBT(lookup));
        owner.loadWithComponents(data,lookup);gauge.tick();
        var before=owner.saveWithoutMetadata(lookup);
        h.assertTrue(MachineReadings.read(owner,"fluid_7").value()==owner.displayedFluid(7).getAmount() && owner.displayedFluid(7).getAmount()>0,"The linked fluid entry exposes the actual liquid inventory");
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(BASE),Direction.DOWN);
        handler.drain(250,FluidAction.SIMULATE);handler.fill(new FluidStack(Fluids.WATER,250),FluidAction.SIMULATE);
        var normal=new ArrayList<Component>();var shifted=new ArrayList<Component>();
        owner.addToGoggleTooltip(normal,false);owner.addToGoggleTooltip(shifted,true);
        h.assertTrue(!normal.isEmpty() && !shifted.isEmpty(),"Both ordinary and Shift goggles show the overview");
        h.assertTrue(before.equals(owner.saveWithoutMetadata(lookup)),"Linked queries, tooltip construction and simulated transfers preserve saved material and maintenance state");
        h.succeed();
    }
}
