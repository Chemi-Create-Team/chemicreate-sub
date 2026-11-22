package net.createplant.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createplant.CreatePlant;
import net.createplant.registry.CEDisplays;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.EntityBlock;

/** Opt-in checks need a real client because Create's goggles and settings use client-only helpers. */
public final class DisplayClientCheck {
    private DisplayClientCheck() { }
    public static void verify(Minecraft mc) throws java.io.IOException {
        var results=new LinkedHashMap<String,Object>();
        for (var block:BuiltInRegistries.BLOCK) {
            var id=BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || !(block instanceof EntityBlock entity)) continue;
            var be=entity.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());
            if (!(be instanceof IHaveGoggleInformation goggles)) continue;
            be.setLevel(mc.level);
            for (boolean shift:new boolean[]{false,true}) {
                var lines=new ArrayList<Component>();goggles.addToGoggleTooltip(lines,shift);
                results.put(id+" shift="+shift,lines.stream().map(Component::getString).toList());
            }
        }
        for(double temperature:new double[]{300,450}) {
            var block=com.simibubi.create.AllBlocks.FLUID_TANK.get();
            var tank=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());
            tank.getTankInventory().fill(net.createplant.thermal.FluidThermal.at(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),temperature),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if(temperature>373.15 && tank.getFillState()>=.125f) throw new IllegalStateException("Steam mass must not be drawn as liquid water");
            tank.setLevel(mc.level);var lines=new ArrayList<Component>();tank.addToGoggleTooltip(lines,false);
            if(lines.size()!=(temperature>373.15 ? 4 : 3)) throw new IllegalStateException("Water/steam summary must use four lines only when needed: "+lines);
            results.put("water phase "+temperature,lines.stream().map(Component::getString).toList());
        }
        var overflowing=new ArrayList<String>();
        for (var language:new String[]{"en_us","ja_jp"}) {
            try (var stream=DisplayClientCheck.class.getResourceAsStream("/assets/"+CreatePlant.MOD_ID+"/lang/"+language+".json")) {
                var translations=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var key:net.createplant.compat.display.MachineReadings.KEYS) {
                    String text=translations.get("display.create_refinery_n_reactors."+key).getAsString();
                    if (mc.font.width(text)>127) overflowing.add(language+" "+text);
                }
            }
        }
        if (!overflowing.isEmpty()) throw new IllegalStateException("Measurement labels overflow Create's selector: "+overflowing);
        var link=AllBlockEntityTypes.DISPLAY_LINK.create(BlockPos.ZERO,AllBlocks.DISPLAY_LINK.getDefaultState());link.setLevel(mc.level);
        var context=new DisplayLinkContext(mc.level,link);
        var config=new net.minecraft.nbt.CompoundTag();config.putInt("Metric",7);config.putInt("Mode",2);config.putString("Label","Test");
        for (boolean first:new boolean[]{true,false}) {
            var line=new ModularGuiLine();CEDisplays.MACHINE.get().initConfigurationWidgets(context,new ModularGuiLineBuilder(mc.font,line,0,0),first);
            line.loadValues(config,w->{},w->{});var saved=new net.minecraft.nbt.CompoundTag();line.saveValues(saved);
            if (first ? saved.getInt("Metric")!=7 : saved.getInt("Mode")!=2 || !saved.getString("Label").equals("Test"))
                throw new IllegalStateException("Native widgets did not preserve display settings: "+saved);
            line.forEach(w->{
                if (w instanceof net.minecraft.client.gui.components.AbstractWidget widget && (widget.getX()<0 || widget.getX()+widget.getWidth()>137))
                    throw new IllegalStateException("Display settings exceed Create's native configuration row");
            });
        }
        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("display-client-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
}
