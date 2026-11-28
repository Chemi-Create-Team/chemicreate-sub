package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.item.RadiationMeterItem;
import net.createplant.nuclear.*;
import net.createplant.registry.*;
import net.createplant.registry.CEProtectiveClothing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadiationGameTests {
    private static FakePlayer player(GameTestHelper h) {
        var p =
                new FakePlayer(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "radiation-test"));
        var at = h.absolutePos(new BlockPos(5, 2, 5));
        p.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
        p.getAbilities().invulnerable = true;
        return p;
    }

    private static void near(GameTestHelper h, double got, double expected, String what) {
        h.assertTrue(
                Math.abs(got - expected) < Math.max(1e-12, Math.abs(expected) * 1e-6),
                what + ": " + got + " / " + expected);
    }

    @GameTest(template = "test_rbmk")
    public static void physicalHalfValueLayersAndChangedPathInvalidateTheCache(GameTestHelper h) {
        var p = new BlockPos(5, 2, 5);
        var at = Vec3.atLowerCornerOf(h.absolutePos(p)).add(.1, .5, .5);
        var trace = RadiationField.get(h.getLevel()).shielding;
        for (var row :
                new Object[][] {
                    {Blocks.WATER, .09},
                    {Blocks.WHITE_CONCRETE, .05},
                    {Blocks.IRON_BLOCK, .016},
                    {CENuclear.BIOLOGICAL_SHIELD.get(), .04}
                }) {
            h.setBlock(p, (net.minecraft.world.level.block.Block) row[0]);
            near(
                    h,
                    trace.trace(h.getLevel(), at, at.add((double) row[1], 0, 0)).gamma(),
                    .5,
                    "One half-value layer transmits half the gamma field");
        }
        h.setBlock(p, Blocks.AIR);
        near(
                h,
                trace.trace(h.getLevel(), at, at.add(.04, 0, 0)).gamma(),
                1,
                "Removing shielding invalidates the cached path");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void sourceDistanceAndWaterThicknessUseTheCommonRay(GameTestHelper h) {
        var source = Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(4, 3, 4))).add(.5, .5, .5);
        var field = RadiationField.get(h.getLevel());
        var s =
                new RadiationField.Source(
                        source, new RadiationPhysics.Spectrum(1, 1, 1, 0), null, Long.MAX_VALUE);
        double a = field.contribution(h.getLevel(), s, source.add(1, 0, 0)).gamma();
        double b = field.contribution(h.getLevel(), s, source.add(2, 0, 0)).gamma();
        near(h, b, a / 4, "Doubling distance quarters gamma dose");
        h.setBlock(new BlockPos(5, 3, 4), Blocks.WATER);
        var covered = field.contribution(h.getLevel(), s, source.add(2, 0, 0));
        h.assertTrue(
                covered.gamma() < b / 1000 && covered.neutron() < b / 500 && covered.beta() < 1e-12,
                "One metre of water attenuates gamma, neutrons and beta");
        h.assertTrue(
                RadiationPhysics.betaAir(5) == 0, "Beta does not reach beyond four metres of air");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void inventoriesScaleWithQuantityAndSaveTheirCoolingHistory(GameTestHelper h) {
        var p = new BlockPos(4, 2, 4);
        h.setBlock(p, Blocks.CHEST);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(p);
        var rod = RadiationSources.stamp(CENuclear.SPENT_FUEL_ROD.toStack(), h.getLevel());
        chest.setItem(0, rod);
        double one = RadiationContents.sample(h.getLevel(), chest).gamma();
        chest.setItem(1, rod.copy());
        near(
                h,
                RadiationContents.sample(h.getLevel(), chest).gamma(),
                one * 2,
                "Two stored assemblies give twice the dose constant");
        var material = rod.get(CERadiation.MATERIAL.get());
        long later = material.tick() + (long) (RadiationPhysics.SHORT_HALF_LIFE * 20);
        var saved =
                ItemStack.parseOptional(
                        h.getLevel().registryAccess(),
                        (CompoundTag) rod.save(h.getLevel().registryAccess()));
        h.assertTrue(
                material.equals(saved.get(CERadiation.MATERIAL.get())),
                "Item serialization retains isotope quantities and reference time");
        var profile = RadiationSources.profile(rod);
        double cooled = profile.spectrum(material, later).gamma();
        near(
                h,
                profile.spectrum(saved.get(CERadiation.MATERIAL.get()), later).gamma(),
                cooled,
                "Reload cannot rejuvenate spent fuel");
        h.assertTrue(
                cooled < one * .58 && cooled > one * .57,
                "Short-lived inventory halves while the Cs group remains");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void nativeTanksMixIsotopesAndSimulationDoesNotChangeThem(GameTestHelper h) {
        long now = RadiationSources.now(h.getLevel());
        var tank = new FluidTank(4000);
        var first = new FluidStack(CEFuelCycle.HIGH_LEVEL_WASTE.get(), 1000);
        var second = first.copy();
        first.set(CERadiation.MATERIAL.get(), new Radionuclides(1000, 200, 10, now));
        second.set(CERadiation.MATERIAL.get(), new Radionuclides(100, 20, 1, now));
        tank.fill(first, FluidAction.EXECUTE);
        var before = tank.getFluid().copy();
        h.assertTrue(
                tank.fill(second, FluidAction.SIMULATE) == 1000
                        && tank.getFluidAmount() == before.getAmount()
                        && tank.getFluid().getComponents().equals(before.getComponents()),
                "Simulating a mix leaves the tank unchanged");
        h.assertTrue(
                tank.fill(second, FluidAction.EXECUTE) == 1000,
                "Different cooling histories are the same fluid");
        var material = tank.getFluid().get(CERadiation.MATERIAL.get()).at(now);
        near(
                h,
                material.total() * tank.getFluidAmount() / 1000d,
                1331,
                "Mixing conserves isotope activity, not just volume");
        var reloaded = new FluidTank(4000);
        reloaded.readFromNBT(
                h.getLevel().registryAccess(),
                tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        near(
                h,
                RadiationSources.fluid(reloaded.getFluid(), now).gamma(),
                RadiationSources.fluid(tank.getFluid(), now).gamma(),
                "Reload does not increase the liquid source");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void clothingStopsOnlyBetaAndKeepsContaminationOnTheGarment(GameTestHelper h) {
        var bare = player(h);
        var suited = player(h);
        suited.setItemSlot(EquipmentSlot.CHEST, CEProtectiveClothing.PROTECTIVE_COAT.toStack());
        suited.setItemSlot(EquipmentSlot.HEAD, CEProtectiveClothing.PROTECTIVE_CAP.toStack());
        suited.setItemSlot(EquipmentSlot.LEGS, CEProtectiveClothing.PROTECTIVE_TROUSERS.toStack());
        suited.setItemSlot(EquipmentSlot.FEET, CEProtectiveClothing.PROTECTIVE_SHOE_COVERS.toStack());
        var field = new RadiationPhysics.Spectrum(.01, .01, .01, 0);
        Radiation.apply(bare, field, Radiation.protection(bare));
        Radiation.apply(suited, field, Radiation.protection(suited));
        var a = bare.getData(CERadiation.EXPOSURE);
        var b = suited.getData(CERadiation.EXPOSURE);
        near(h, a.getDouble("GammaSvS"), b.getDouble("GammaSvS"), "Clothing cannot shield gamma");
        near(
                h,
                a.getDouble("NeutronSvS"),
                b.getDouble("NeutronSvS"),
                "Clothing cannot shield neutrons");
        near(
                h,
                b.getDouble("BetaSvS"),
                a.getDouble("BetaSvS") * .1,
                "Full clothing blocks 90 percent of beta exposure");
        var coat = suited.getItemBySlot(EquipmentSlot.CHEST);
        RadiationContamination.stain(
                coat,
                new Radionuclides(1e8, 1e8, 0, RadiationSources.now(h.getLevel())),
                RadiationSources.now(h.getLevel()));
        suited.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.assertTrue(
                RadiationSources.item(coat, RadiationSources.now(h.getLevel())).gamma() > 0,
                "Removed dirty clothing remains a source");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void contaminationIsSavedTracksOnShoesAndCanBeWashedOff(GameTestHelper h) {
        var p = player(h);
        long now = RadiationSources.now(h.getLevel());
        var soil = new Radionuclides(1e7, 1e7, 1e5, now);
        RadiationContamination.deposit(h.getLevel(), p.blockPosition(), soil);
        Radiation.sample(p, RadiationPhysics.Spectrum.ZERO);
        h.assertTrue(
                p.getData(CERadiation.EXPOSURE).getDouble("ShoeBq") > 0,
                "Walking picks up contamination");
        var old = p.blockPosition();
        p.setPos(p.getX() + 8, p.getY(), p.getZ());
        Radiation.sample(p, RadiationPhysics.Spectrum.ZERO);
        h.assertTrue(
                RadiationContamination.at(h.getLevel(), p.blockPosition()).total() > 0,
                "Shoes transfer activity to a different grid cell");
        var data = RadiationWorldData.get(h.getLevel());
        var reloaded =
                RadiationWorldData.load(
                        data.save(new CompoundTag(), h.getLevel().registryAccess()),
                        h.getLevel().registryAccess());
        h.assertTrue(
                reloaded.soil
                        .get(net.minecraft.world.level.ChunkPos.asLong(old))
                        .containsKey(RadiationContamination.cell(old)),
                "Contamination survives serialization");
        double before = p.getData(CERadiation.EXPOSURE).getDouble("ShoeBq"),
                dose = p.getData(CERadiation.EXPOSURE).getDouble("LifetimeSv");
        RadiationContamination.wash(p, .9);
        near(
                h,
                p.getData(CERadiation.EXPOSURE).getDouble("ShoeBq"),
                before * .1,
                "Soap removes surface contamination");
        near(
                h,
                p.getData(CERadiation.EXPOSURE).getDouble("LifetimeSv"),
                dose,
                "Washing cannot undo dose already received");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void respiratorReducesIntakeAndMetersReportServerReadings(GameTestHelper h) {
        var bare = player(h);
        var masked = player(h);
        masked.setItemSlot(EquipmentSlot.HEAD, CESafety.GAS_MASK.toStack());
        long now = RadiationSources.now(h.getLevel());
        var contamination = new Radionuclides(1e8, 1e8, 1e7, now);
        RadiationContamination.deposit(h.getLevel(), bare.blockPosition(), contamination);
        Radiation.sample(bare, RadiationPhysics.Spectrum.ZERO);
        var remaining = RadiationContamination.take(h.getLevel(), bare.blockPosition(), 1);
        RadiationContamination.deposit(h.getLevel(), bare.blockPosition(), contamination);
        Radiation.sample(masked, RadiationPhysics.Spectrum.ZERO);
        near(
                h,
                masked.getData(CERadiation.EXPOSURE).getDouble("CommittedSv"),
                bare.getData(CERadiation.EXPOSURE).getDouble("CommittedSv") * .02,
                "The intact filter reduces inhaled activity by the assigned protection factor");
        masked.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND, CERadiation.DOSIMETER.toStack());
        RadiationMeterItem.update(masked);
        var expectedReading =
                RadiationMeterItem.reading(
                        masked.getMainHandItem(), masked.getData(CERadiation.EXPOSURE));
        var tooltip =
                masked.getMainHandItem()
                        .getTooltipLines(
                                net.minecraft.world.item.Item.TooltipContext.of(h.getLevel()),
                                masked,
                                net.minecraft.world.item.TooltipFlag.NORMAL);
        h.assertTrue(
                tooltip.stream()
                        .anyMatch(line -> line.getString().equals(expectedReading.getString())),
                "The meter tooltip reads the owner's exposure attachment");
        h.assertTrue(
                !masked.getMainHandItem().has(CERadiation.BETA_WINDOW.get()),
                "Measuring does not modify or broadcast personal readings in equipment components");
        h.assertTrue(
                RadiationMeterItem.clickProbability(.001)
                        > RadiationMeterItem.clickProbability(1e-9),
                "Click frequency rises with radiation");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void lowRateExposureAccumulatesLifetimeDoseWithoutAnAcuteSpike(GameTestHelper h) {
        var p = player(h);
        for (int i = 0; i < 100; i++)
            Radiation.apply(p, new RadiationPhysics.Spectrum(1e-9, 0, 0, 0), 0);
        near(
                h,
                p.getData(CERadiation.EXPOSURE).getDouble("LifetimeSv"),
                1e-7,
                "Lifetime records every low-rate exposure");
        h.assertTrue(
                p.getData(CENuclear.DOSE) < 1e-9,
                "Protracted tiny doses do not behave like an acute irradiation");
        Radiation.apply(p, new RadiationPhysics.Spectrum(2, 0, 0, 0), 0);
        h.assertTrue(
                p.getData(CENuclear.SICKNESS_STAGE) >= 2,
                "The existing acute symptom stages remain active");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void reactorShieldingFollowsTheRayAndStoppedFuelStillRadiates(GameTestHelper h) {
        var key = h.absolutePos(new BlockPos(4, 2, 4));
        var box =
                new net.minecraft.world.level.levelgen.structure.BoundingBox(
                        key.getX(),
                        key.getY(),
                        key.getZ(),
                        key.getX() + 2,
                        key.getY() + 2,
                        key.getZ() + 2);
        var operating = RadiationField.core(h.getLevel(), key, box, 1e8, false);
        var target = Vec3.atCenterOf(box.getCenter()).add(5, 0, 0);
        var field = RadiationField.get(h.getLevel());
        double before = field.contribution(h.getLevel(), operating, target).gamma();
        h.setBlock(new BlockPos(8, 3, 5), Blocks.WHITE_CONCRETE);
        h.assertTrue(
                field.contribution(h.getLevel(), operating, target).gamma() < before / 1000,
                "A concrete block on this path shields the reactor without averaging unrelated"
                        + " faces");
        var stopped = RadiationField.core(h.getLevel(), key, box, 0, true);
        h.assertTrue(
                stopped.spectrum().gamma() > 0 && stopped.spectrum().neutron() == 0,
                "Decay inventory remains after shutdown but operating neutrons disappear");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void poolTransfersAndReloadsTheOriginalIsotopeInventory(GameTestHelper h) {
        var base = new BlockPos(5, 1, 5);
        FuelPoolGameTests.build(h, base, 2, 2);
        var level = h.getLevel();
        var at = h.absolutePos(base);
        level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                        at,
                        Direction.DOWN)
                .fill(
                        new FluidStack(
                                net.minecraft.world.level.material.Fluids.WATER,
                                4
                                        * net.createplant.block.entity.FuelPoolBlockEntity
                                                .WATER_PER_CELL),
                        FluidAction.EXECUTE);
        var inventory =
                level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        at,
                        Direction.UP);
        var rod = CENuclear.SPENT_FUEL_ROD.toStack();
        var activity = new Radionuclides(1e10, 1e9, 1e7, RadiationSources.now(level));
        rod.set(CERadiation.MATERIAL.get(), activity);
        h.assertTrue(
                inventory.insertItem(0, rod, false).isEmpty(),
                "The covered pool accepts an already cooled assembly");
        var pool = (net.createplant.block.entity.FuelPoolBlockEntity) level.getBlockEntity(at);
        var saved = pool.saveWithFullMetadata(level.registryAccess());
        pool.loadWithComponents(saved, level.registryAccess());
        h.assertTrue(
                activity.equals(inventory.getStackInSlot(0).get(CERadiation.MATERIAL.get())),
                "Saving and reloading the rack keeps the isotope inventory");
        var preview = inventory.extractItem(0, 1, true);
        h.assertTrue(
                activity.equals(preview.get(CERadiation.MATERIAL.get()))
                        && pool.slot(0) == net.createplant.block.entity.FuelPoolBlockEntity.SPENT,
                "Extraction preview does not rejuvenate or remove the assembly");
        var extracted = inventory.extractItem(0, 1, false);
        h.assertTrue(
                activity.equals(extracted.get(CERadiation.MATERIAL.get())),
                "The real funnel/crane endpoint preserves the cooling history");
        inventory.insertItem(0, CENuclear.FUEL_ROD.toStack(), false);
        pool.loadWithComponents(
                pool.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        h.assertTrue(
                !inventory.getStackInSlot(0).has(CERadiation.MATERIAL.get()),
                "Unused sealed fuel remains ordinary stackable fuel after pool reload");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void droppedFuelIsIndexedAndRemovalStopsItsContribution(GameTestHelper h) {
        var point = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 3, 5)));
        var target = point.add(2, 0, 0);
        var field = RadiationField.get(h.getLevel());
        field.refresh(h.getLevel());
        double before = field.measure(h.getLevel(), target, null).gamma();
        var item =
                new net.minecraft.world.entity.item.ItemEntity(
                        h.getLevel(),
                        point.x,
                        point.y,
                        point.z,
                        CENuclear.SPENT_FUEL_ROD.toStack(3));
        h.getLevel().addFreshEntity(item);
        double expected =
                field.contribution(
                                h.getLevel(),
                                new RadiationField.Source(
                                        point,
                                        RadiationSources.item(
                                                item.getItem(), RadiationSources.now(h.getLevel())),
                                        null,
                                        Long.MAX_VALUE),
                                target)
                        .gamma();
        near(
                h,
                field.measure(h.getLevel(), target, null).gamma() - before,
                expected,
                "A dropped stack contributes its actual number of assemblies");
        item.discard();
        near(
                h,
                field.measure(h.getLevel(), target, null).gamma(),
                before,
                "Removing the dropped entity immediately stops its radiation");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void droppedBucketsAndPortableContainersRemainSources(GameTestHelper h) {
        var point = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 3, 5)));
        var target = point.add(2, 0, 0);
        var field = RadiationField.get(h.getLevel());
        field.refresh(h.getLevel());
        var bucket =
                net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(
                        new FluidStack(CEFuelCycle.DISSOLVED_FUEL.get(), 1000));
        var box = new ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
        box.set(
                net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(
                        java.util.List.of(CENuclear.SPENT_FUEL_ROD.toStack())));
        for (var stack : java.util.List.of(bucket, box)) {
            double before = field.measure(h.getLevel(), target, null).gamma();
            var dropped =
                    new net.minecraft.world.entity.item.ItemEntity(
                            h.getLevel(), point.x, point.y, point.z, stack);
            h.getLevel().addFreshEntity(dropped);
            h.assertTrue(
                    field.measure(h.getLevel(), target, null).gamma() > before,
                    "A dropped radioactive bucket or packed container remains indexed");
            dropped.discard();
        }
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void physicalStorageStartsHistoryButSimulationAndFreshFuelKeepTheirState(
            GameTestHelper h) {
        var inventory = new net.neoforged.neoforge.items.ItemStackHandler(1);
        var rod = CENuclear.SPENT_FUEL_ROD.toStack();
        inventory.insertItem(0, rod, true);
        h.assertTrue(
                !rod.has(CERadiation.MATERIAL.get()) && inventory.getStackInSlot(0).isEmpty(),
                "Simulated insert does not start cooling history or fill storage");
        inventory.insertItem(0, rod, false);
        var history = inventory.getStackInSlot(0).get(CERadiation.MATERIAL.get());
        h.assertTrue(
                history != null, "Real storage creates cooling history before immediate saving");
        var reloaded = new net.neoforged.neoforge.items.ItemStackHandler(1);
        reloaded.deserializeNBT(
                h.getLevel().registryAccess(),
                inventory.serializeNBT(h.getLevel().registryAccess()));
        h.assertTrue(
                history.equals(reloaded.getStackInSlot(0).get(CERadiation.MATERIAL.get())),
                "Immediate reload retains isotope age");
        inventory.setStackInSlot(0, CENuclear.FUEL_ROD.toStack());
        h.assertTrue(
                !inventory.getStackInSlot(0).has(CERadiation.MATERIAL.get()),
                "Sealed unused fuel stays stackable with ordinary fresh fuel");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void placedWasteCanisterIsOneSourceAndPreservesItsAgeWhenRecovered(
            GameTestHelper h) {
        var pos = new BlockPos(5, 2, 5);
        var absolute = h.absolutePos(pos);
        var block = CEFuelCycle.WASTE_CANISTER.get();
        var stack = new ItemStack(block);
        var material = new Radionuclides(1000, 2000, 30, RadiationSources.now(h.getLevel()));
        stack.set(CERadiation.MATERIAL.get(), material);
        h.setBlock(pos, block);
        block.setPlacedBy(
                h.getLevel(), absolute, h.getLevel().getBlockState(absolute), player(h), stack);
        h.assertTrue(
                RadiationSources.profile(h.getLevel().getBlockState(absolute)) != null,
                "The placed waste package is a block source");
        h.assertTrue(
                RadiationSources.profile(h.getLevel().getBlockState(absolute.above())) == null
                        && RadiationSources.profile(h.getLevel().getBlockState(absolute.above(2)))
                                == null,
                "The three-block package is counted once");
        var recovered =
                net.minecraft.world.level.block.Block.getDrops(
                        h.getLevel().getBlockState(absolute), h.getLevel(), absolute, null);
        h.assertTrue(
                recovered.size() == 1
                        && material.equals(recovered.getFirst().get(CERadiation.MATERIAL.get())),
                "Picking up a placed waste package retains its isotope history");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void alphaDustHasNoExternalFieldButCanBeInhaledAndLifetimeSurvivesDeath(
            GameTestHelper h) {
        var p = player(h);
        var powder = CEUranium.YELLOWCAKE.toStack();
        h.assertTrue(
                RadiationSources.profile(CEUranium.URANINITE.toStack()) != null,
                "The real registered raw ore item is an alpha source");
        long now = RadiationSources.now(h.getLevel());
        h.assertTrue(
                RadiationSources.item(powder, now).external() == 0,
                "Fresh uranium has no modelled penetrating external field");
        RadiationSources.shed(powder, h.getLevel(), p.blockPosition());
        h.assertTrue(
                RadiationContamination.at(h.getLevel(), p.blockPosition()).longLived() > 0,
                "Loose uranium dust creates alpha contamination");
        var earlier = CEUranium.URANINITE.toStack();
        RadiationSources.shed(earlier, h.getLevel(), p.blockPosition());
        var later = CEUranium.URANINITE.toStack();
        RadiationSources.shed(later, h.getLevel(), p.blockPosition());
        h.assertTrue(
                ItemStack.isSameItemSameComponents(earlier, later)
                        && ItemStack.isSameItemSameComponents(earlier, CEUranium.URANINITE.toStack()),
                "Shedding dust leaves mined uraninite stackable with fresh ore");
        Radiation.sample(p, RadiationPhysics.Spectrum.ZERO);
        h.assertTrue(
                p.getData(CERadiation.EXPOSURE).getDouble("CommittedSv") > 0,
                "Unmasked inhalation records a committed internal dose");
        var clone = player(h);
        Radiation.clonePlayer(
                new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone, p, true));
        near(
                h,
                clone.getData(CERadiation.EXPOSURE).getDouble("LifetimeSv"),
                p.getData(CERadiation.EXPOSURE).getDouble("LifetimeSv"),
                "Lifetime dose is retained on death");
        h.succeed();
    }
}
