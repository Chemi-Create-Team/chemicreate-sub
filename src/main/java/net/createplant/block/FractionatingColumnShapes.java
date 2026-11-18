package net.createplant.block;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.Gson;
import net.createplant.CreatePlant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Collision follows the same integer-pixel boxes as the generated model. */
public final class FractionatingColumnShapes {
    private static final Map<String,VoxelShape> SHAPES=new HashMap<>();
    static {
        try (var stream=FractionatingColumnShapes.class.getResourceAsStream("/assets/"+CreatePlant.MOD_ID+"/fractionating_column_shapes.json")) {
            if (stream==null) throw new IllegalStateException("Missing fractionating column shapes");
            var data=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            for (var entry:data.entrySet()) {
                VoxelShape shape=Shapes.empty();
                for (var element:entry.getValue().getAsJsonArray()) {
                    var b=element.getAsJsonArray();shape=Shapes.or(shape,Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
                }
                SHAPES.put(entry.getKey(),shape.optimize());
            }
            VoxelShape outlet=SHAPES.get("outlet"),inlet=SHAPES.get("inlet");
            for (String key:List.copyOf(SHAPES.keySet())) if (!key.equals("outlet") && !key.equals("inlet")) {
                SHAPES.put(key+"_out",Shapes.or(SHAPES.get(key),outlet).optimize());
                SHAPES.put(key+"_in",Shapes.or(SHAPES.get(key),inlet).optimize());
            }
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    private FractionatingColumnShapes() { }
    public static VoxelShape of(BlockState state) {
        int w=state.getValue(FractionatingColumnBlock.WIDTH),c=state.getValue(FractionatingColumnBlock.CELL);
        return SHAPES.get(w+"_"+(Math.min(c/3,w-1)*3+Math.min(c%3,w-1))+"_"+state.getValue(FractionatingColumnBlock.SECTION).getSerializedName()+(state.getValue(FractionatingColumnBlock.CUT)>0 ? "_out" : state.getValue(FractionatingColumnBlock.INLET)>0 ? "_in" : ""));
    }
}
