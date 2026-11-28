package net.createplant.client;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.ponder.PonderText;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.LoggerFactory;

/** Opt-in developer smoke check, inactive in normal play. Captures the actual Ponder renderer. */
public final class ClientQualityCheck {
    private static int phase, ticks;
    // Run one startup locale per JVM. Resource reloads can invalidate Create's
    // cached bitmap glyphs; that is unrelated to the asset/translation check.
    private static final String[] LANGUAGES={System.getProperty("createplant.clientQualityLanguage","ja_jp")};
    private static int language,target;
    private static String[] TARGETS=System.getProperty("createplant.clientQualityTargets","reciprocating_compressor,fractionating_column,high_pressure_turbine_blade,reactor_separator_core,cooling_fan").split(",");
    private static final Set<String> scenes=new TreeSet<>();
    private ClientQualityCheck() { }

    public interface Step { void run(Minecraft mc) throws Exception; }
    public interface Tick { boolean run(Minecraft mc) throws Exception; }
    public interface PonderCheck { void run(Minecraft mc,String scene) throws Exception; }
    /** What follows a finished addon check: its own screenshot target, a stop, or the reciprocating compressor check when that is switched on. */
    public enum Finish { SHOW, STOP, THEN_RECIPROCATING }
    /** A dev check an addon owns: it starts under its system property and then runs in its own phase. */
    public record AddonCheck(String property,int phase,Step start,Tick tick,Finish finish) { }

    private static final List<AddonCheck> ADDON_CHECKS=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<PonderCheck> ADDON_PONDER_CHECKS=new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void register(AddonCheck check) { ADDON_CHECKS.add(check); }
    /** Runs after each captured Ponder scene; it checks the system properties itself. */
    public static void registerPonderCheck(PonderCheck check) { ADDON_PONDER_CHECKS.add(check); }

    private static void finish(Minecraft mc,Finish finish) {
        switch (finish) {
            case SHOW -> show(mc);
            case STOP -> mc.stop();
            case THEN_RECIPROCATING -> {
                if (Boolean.getBoolean("createplant.reciprocatingClientCheck")) { ReciprocatingClientCheck.start(mc);ticks=0;phase=14; }
                else show(mc);
            }
        }
    }
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        for (var check:ADDON_CHECKS) if (phase==check.phase()) {
            try { if (check.tick().run(mc)) finish(mc,check.finish()); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==96) {
            try { if (CreativeTabsClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==0 && (Boolean.getBoolean("createplant.mixtureClientCheck") || Boolean.getBoolean("createplant.seasonalPlantClientCheck") || Boolean.getBoolean("createplant.highStressClientCheck") || Boolean.getBoolean("createplant.fuelPoolClientCheck") || Boolean.getBoolean("createplant.craneClientCheck") || Boolean.getBoolean("createplant.cranePonderCheck")) && mc.level!=null && mc.player!=null && mc.getOverlay()==null) { phase=4;ticks=0; }
        if (phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.level==null && mc.getOverlay()==null && ++ticks>100) {
            phase=4;
            mc.createWorldOpenFlows().createFreshLevel("createplant-quality-"+System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Create-Plant quality check",net.minecraft.world.level.GameType.CREATIVE,false,
                    net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L,false,false),
                access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if (phase==4 && mc.level!=null && mc.getOverlay()==null && ++ticks>150) { phase=1;verify(mc); }
        if (phase==92) {
            try { if (MineralClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==91) {
            try { if (ForgingClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==8) {
            try { if (TankClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==11) {
            try { if (FinFanClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==15) {
            try { if (HorizontalTankClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==14) {
            try { if (ReciprocatingClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==24) {
            try {
                if (VesselWorldClientCheck.tick(mc)) {
                    if (Boolean.getBoolean("createplant.vesselContentsClientCheck")) { VesselContentsClientCheck.start(mc);ticks=0;phase=28; }
                    else show(mc);
                }
            } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==30) {
            try { if (GasBellClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==31) {
            try { if (CharredWoodClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==33) {
            try { if (ElectrolysisClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==28) {
            try { if (VesselContentsClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==26) {
            try { if (PowderItemsClientCheck.tick(mc)) show(mc); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==27) {
            try {
                if (SoundClientCheck.tick(mc)) {
                    if (Boolean.getBoolean("createplant.powderItemsClientCheck")) { PowderItemsClientCheck.start(mc);ticks=0;phase=26; }
                    else show(mc);
                }
            } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==12) {
            try { if (StainlessPipeClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==18) {
            try { if (PipeGaugeClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==20) {
            try { if (LoadClientCheck.tick(mc)) mc.stop(); } catch (Exception exception) { fail(mc,exception); }
            return;
        }
        if (phase==2 && mc.getOverlay()!=null) { ticks=0;return; }
        if (phase==2 && Boolean.getBoolean("createplant.separatorDrumClientCheck") && mc.screen instanceof PonderUI ui) ui.coolDownAfterSkip();
        if ((phase==2 || phase==3) && Boolean.getBoolean("createplant.wetProcessingClientCheck") && mc.screen instanceof PonderUI ui) ui.coolDownAfterSkip();
        if (phase==2 && Boolean.getBoolean("createplant.pulsationTubeClientCheck") && mc.screen instanceof PonderUI ui) ui.coolDownAfterSkip();
        if (phase==2 && ++ticks>Integer.getInteger("createplant.clientQualityCaptureDelay",35)) {
            verifyTargetState(mc);
            try { for (var verify:ADDON_PONDER_CHECKS) verify.run(mc,TARGETS[target].split("@",2)[0]); } catch (Exception exception) { fail(mc,exception);return; }
            phase=3;
            if (!Boolean.getBoolean("createplant.clientQualitySkipScreenshots")) Screenshot.grab(mc.gameDirectory,"quality-"+LANGUAGES[language]+"-"+TARGETS[target]+".png",mc.getMainRenderTarget(),message->{ });
            if (++target<TARGETS.length) { show(mc);return; }
            target=0;
            complete(mc);
        }
    }
    private static void complete(Minecraft mc) {
        phase=3;
                try {
                    Files.writeString(mc.gameDirectory.toPath().resolve("client-quality-result-"+LANGUAGES[0]+".json"),new GsonBuilder().setPrettyPrinting().create()
                        .toJson(Map.of("languages",LANGUAGES,"scenes",scenes,"runtimeTranslations","passed","ponderRender",Boolean.getBoolean("createplant.clientQualitySkipScreenshots") ? "checked" : "captured","renderTargets",TARGETS,
                            "translationScope",System.getProperty("createplant.clientQualityScenes","all"))));
                    LoggerFactory.getLogger(ClientQualityCheck.class).info("CLIENT QUALITY PASSED: {} scenes, {}",scenes.size(),LANGUAGES[0]);
                    if (!Boolean.getBoolean("createplant.clientQualityKeepOpen")) mc.stop();
                } catch (Exception exception) { fail(mc,exception); }
    }
    private static void verifyTargetState(Minecraft mc) {
        if (Boolean.getBoolean("createplant.wetProcessingClientCheck")) {
            var world=((PonderUI)mc.screen).getActiveScene().getWorld();
            if (!(world.getBlockEntity(new net.minecraft.core.BlockPos(4,2,5)) instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel)
                || !(world.getBlockEntity(new net.minecraft.core.BlockPos(17,5,13)) instanceof net.createplant.block.entity.WetProcessBlockEntity machine)
                || !machine.formed() || machine.width()!=14 || machine.height()!=4 || machine.tanks[0].isEmpty() || vessel.getTankInventory().isEmpty())
                throw new IllegalStateException("Wet process Ponder must show a filled stirred vessel and a circular clarifier");
            for (var cell : net.createplant.block.WetProcessStructure.cells(machine.getBlockPos(), machine.getBlockState().getValue(net.createplant.block.WetProcessDriveBlock.FACING), machine.width(), machine.height()))
                if (world.getBlockState(cell.pos()) != cell.state()) throw new IllegalStateException("Wet process lesson differs from the real structure at " + cell.pos() + ": " + world.getBlockState(cell.pos()) + " / " + cell.state());
                else if (!cell.state().isAir() && mc.getBlockRenderer().getBlockModel(cell.state()) == mc.getModelManager().getMissingModel()) throw new IllegalStateException("Missing wet process model at " + cell.pos());
                else if (world.getBlockEntity(cell.pos()) instanceof net.createplant.block.entity.WetProcessPartBlockEntity part && part.owner() != machine) throw new IllegalStateException("Wet process goggles must resolve the synced drive at " + cell.pos());
        }
        if (Boolean.getBoolean("createplant.pulsationTubeClientCheck")) {
            var world=((PonderUI)mc.screen).getActiveScene().getWorld();var rows=new java.util.ArrayList<Map<String,Object>>();
            for(var at:java.util.List.of(new net.minecraft.core.BlockPos(3,2,4),new net.minecraft.core.BlockPos(4,2,7),new net.minecraft.core.BlockPos(7,2,7))) {
                if(!(world.getBlockEntity(at) instanceof net.createplant.block.entity.PulsationTubeBlockEntity tube) || !tube.isController() || tube.getHeight()!=3 || tube.getWidth()!=1)
                    throw new IllegalStateException("Ponder must show three separate axial tubes");
                rows.add(Map.of("axis",tube.getMainConnectionAxis().getName(),"length",tube.getHeight(),"gasMb",tube.getTankInventory().getFluidAmount(),"dial",tube.pressureDial()));
            }
            try {Files.writeString(mc.gameDirectory.toPath().resolve("pulsation-ponder-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));}
            catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
        if (Boolean.getBoolean("createplant.separatorDrumClientCheck")) {
            var scene=((PonderUI)mc.screen).getActiveScene();
            var at=new net.minecraft.core.BlockPos(target<2 ? 2 : 130,2,2);
            if (!(scene.getWorld().getBlockEntity(at) instanceof net.createplant.block.entity.AmmoniaSeparatorBlockEntity drum)
                || !drum.isController() || drum.getWidth()!=1 || drum.getHeight()!=3)
                throw new IllegalStateException("The Ponder must show a one-wide, three-layer separator");
            for(int y=0;y<3;y++) if(scene.getWorld().getBlockState(at.above(y)).getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE)
                !=com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN)
                throw new IllegalStateException("The drum still has a Create tank window");
            if(target==0 && drum.liquidFraction()<.5f || target==1 && drum.liquidFraction()>.2f)
                throw new IllegalStateException("The Ponder sight glass must show the selected liquid level");
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("separator-ponder-state-"+target+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("scene",scene.getId().toString(),"layers",drum.getHeight(),"width",drum.getWidth(),"front",drum.front().getSerializedName(),"liquidFraction",drum.liquidFraction(),"fluid",drum.gaugeLiquid().getHoverName().getString())));
            } catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
        if (TARGETS[target].contains("turbine_unit@")) {
            var world = ((PonderUI) mc.screen).getActiveScene().getWorld();
            for (var unit : net.createplant.ponder.TurbineUnitPonderScenes.layouts()) {
                for (var entry : net.createplant.block.TurbineUnit.states(unit).entrySet()) {
                    if (!world.getBlockState(entry.getKey()).equals(entry.getValue()))
                        throw new IllegalStateException("Incomplete turbine Ponder at " + entry.getKey());
                }
            }
        }

        if (Boolean.getBoolean("createplant.vesselClientCheck")) VesselClientCheck.verify(mc);
    }
    private static void verify(Minecraft mc) {
            try {
                if (!mc.getLanguageManager().getSelected().equals(LANGUAGES[0]))
                    throw new IllegalStateException("Start Minecraft with lang:"+LANGUAGES[0]+" in the development options.txt");
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToasts().clear();
                if (PonderIndex.editingModeActive()) throw new IllegalStateException("Quality check must run outside Ponder editing mode");
                var scope=new HashSet<>(Arrays.asList(System.getProperty("createplant.clientQualityScenes","").split(",")));scope.remove("");
                try (var stream=ClientQualityCheck.class.getResourceAsStream("/assets/"+CreatePlant.MOD_ID+"/lang/"+LANGUAGES[language]+".json")) {
                    var expected=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var entry:expected.entrySet()) if (entry.getKey().startsWith(PonderText.SHARED_PREFIX)) {
                        String name=entry.getKey().substring(PonderText.SHARED_PREFIX.length());
                        if (!scope.isEmpty() && scope.stream().noneMatch(scene->name.startsWith(scene+"."))) continue;
                        String expectedText=entry.getValue().getAsString();
                        Object[] arguments=name.startsWith("guide.manufacturing_") && expectedText.matches("(?s).*%(?:[0-9]+\\$)?s.*") ? new Object[]{"feed", "products"} : new Object[0];
                        String actual=PonderIndex.getLangAccess().getShared(CreatePlant.location(name),arguments);
                        if (!actual.equals(String.format(java.util.Locale.ROOT,expectedText,arguments))) throw new IllegalStateException("Wrong shared text: "+entry.getKey()+" = "+actual);
                    }
                    var visited=new HashSet<>();var allTargets=new java.util.LinkedHashMap<String,String>();
                    for (var entry:PonderIndex.getSceneAccess().getRegisteredEntries()) {
                        if (!entry.getKey().getNamespace().equals(CreatePlant.MOD_ID)
                            || net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.getKey())==net.minecraft.world.item.Items.AIR || !visited.add(entry.getValue())) continue;
                        for (var scene:PonderIndex.getSceneAccess().compile(java.util.List.of(entry.getValue()))) {
                            if (!scope.isEmpty() && !scope.contains(scene.getId().getPath())) continue;
                            String key=CreatePlant.MOD_ID+".ponder."+scene.getId().getPath()+".header";
                            if (!expected.has(key) || !scene.getTitle().equals(expected.get(key).getAsString())) throw new IllegalStateException("Wrong title: "+key+" = "+scene.getTitle());
                            scenes.add(scene.getId().toString());allTargets.putIfAbsent(scene.getId().toString(),entry.getKey().getPath()+"@"+scene.getId().getPath());
                        }
                    }
                    if (Boolean.getBoolean("createplant.clientQualityAllScenes")) TARGETS=allTargets.values().toArray(String[]::new);
                }
                if (Boolean.getBoolean("createplant.creativeTabsClientCheck")) { CreativeTabsClientCheck.start(mc);ticks=0;phase=96;return; }
                if (Boolean.getBoolean("createplant.mineralClientCheck")) { MineralClientCheck.start(mc);ticks=0;phase=92;return; }
                if (Boolean.getBoolean("createplant.forgingClientCheck")) { ForgingClientCheck.start(mc);ticks=0;phase=91;return; }
                if (Boolean.getBoolean("createplant.thermalClientCheck")) {
                    ThermalClientCheck.verify(mc);
                    if (Boolean.getBoolean("createplant.fluidStatePreview")) { mc.setScreen(null);phase=95;return; }
                }
                if (Boolean.getBoolean("createplant.displayClientCheck")) DisplayClientCheck.verify(mc);
                if (Boolean.getBoolean("createplant.machineSoundClientCheck")) { SoundClientCheck.start(mc);ticks=0;phase=27;return; }
                if (Boolean.getBoolean("createplant.powderItemsClientCheck")) { PowderItemsClientCheck.start(mc);ticks=0;phase=26;return; }
                if (Boolean.getBoolean("createplant.tankClientCheck")) { TankClientCheck.start(mc);ticks=0;phase=8;return; }
                if (Boolean.getBoolean("createplant.finFanClientCheck")) { FinFanClientCheck.start(mc);ticks=0;phase=11;return; }
                if (Boolean.getBoolean("createplant.horizontalTankClientCheck")) { HorizontalTankClientCheck.start(mc);ticks=0;phase=15;return; }
                if (Boolean.getBoolean("createplant.reciprocatingClientCheck") && !Boolean.getBoolean("createplant.reactorConsoleClientCheck")) { ReciprocatingClientCheck.start(mc);ticks=0;phase=14;return; }
                if (Boolean.getBoolean("createplant.ironPipeClientCheck")) { StainlessPipeClientCheck.start(mc);ticks=0;phase=12;return; }
                if (Boolean.getBoolean("createplant.pipeGaugeClientCheck")) { PipeGaugeClientCheck.start(mc);ticks=0;phase=18;return; }
                if (Boolean.getBoolean("createplant.loadClientCheck")) { LoadClientCheck.start(mc);ticks=0;phase=20;return; }
                if (Boolean.getBoolean("createplant.vesselWorldClientCheck")) { VesselWorldClientCheck.start(mc);ticks=0;phase=24;return; }
                if (Boolean.getBoolean("createplant.gasBellClientCheck")) { GasBellClientCheck.start(mc);ticks=0;phase=30;return; }
                if (Boolean.getBoolean("createplant.charredWoodClientCheck")) { CharredWoodClientCheck.start(mc);ticks=0;phase=31;return; }
                if (Boolean.getBoolean("createplant.electrolysisClientCheck")) { ElectrolysisClientCheck.start(mc);ticks=0;phase=33;return; }
                if (Boolean.getBoolean("createplant.vesselContentsClientCheck")) { VesselContentsClientCheck.start(mc);ticks=0;phase=28;return; }
                for (var check:ADDON_CHECKS) if (Boolean.getBoolean(check.property())) { check.start().run(mc);ticks=0;phase=check.phase();return; }
                show(mc);
            } catch (Exception exception) { fail(mc,exception); }
    }
    private static void show(Minecraft mc) {
        String[] request=TARGETS[target].split("@",2);
        if(request.length==2 && request[1].startsWith("jei_")) {
            net.createplant.compat.jei.CreatePlantJeiPlugin.showCheckCategory(request[1].substring(4));
            LoggerFactory.getLogger(ClientQualityCheck.class).info("CLIENT QUALITY TARGET {} -> JEI",TARGETS[target]);
            ticks=0;phase=2;return;
        }
        PonderUI ui=PonderUI.of(request[0].contains(":") ? net.minecraft.resources.ResourceLocation.parse(request[0]) : CreatePlant.location(request[0]));mc.setScreen(ui);
        if (request.length==2) {
            if (request[1].matches("\\d+")) {
                int sceneIndex=Integer.parseInt(request[1]);
                for (int i=0;i<sceneIndex;i++) if (!ui.mouseScrolled(0,0,0,1)) throw new IllegalArgumentException("Missing Ponder scene: "+TARGETS[target]);
            } else while (!ui.getActiveScene().getId().getPath().equals(request[1]))
                if (!ui.mouseScrolled(0,0,0,1)) throw new IllegalArgumentException("Missing Ponder scene: "+TARGETS[target]);
        }
        LoggerFactory.getLogger(ClientQualityCheck.class).info("CLIENT QUALITY TARGET {} -> {}",TARGETS[target],ui.getActiveScene().getId());
        ui.seekToTime(Integer.getInteger("createplant.clientQualitySeek."+target,
            Integer.getInteger("createplant.clientQualitySeek",Math.max(1,ui.getActiveScene().getTotalTime()-Integer.getInteger("createplant.clientQualitySeekFromEnd",90)))));ticks=0;phase=2;
        String rotation=System.getProperty("createplant.clientQualityRotation."+target,System.getProperty("createplant.clientQualityRotation"));
        if (rotation!=null) ui.getActiveScene().getTransform().yRotation.startWithValue(Float.parseFloat(rotation));
    }
    private static void fail(Minecraft mc,Throwable error) {
        phase=-1;LoggerFactory.getLogger(ClientQualityCheck.class).error("CLIENT QUALITY FAILED",error);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("client-quality-error.txt"),error.toString()); } catch (Exception ignored) { }
        mc.stop();
    }
}
