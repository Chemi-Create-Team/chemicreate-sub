package net.createplant.block;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.createplant.CreatePlant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Models and collision boxes are generated from the same integer-pixel geometry. */
public final class ReactorShapes {
    private static final Map<String,VoxelShape> SHAPES=new HashMap<>();
    static {
        try (var input=ReactorShapes.class.getResourceAsStream("/assets/"+CreatePlant.MOD_ID+"/reactor_shapes.json")) {
            if (input==null) throw new IllegalStateException("Missing reactor shapes");
            var data=new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            for (var entry:data.entrySet()) {
                VoxelShape shape=Shapes.empty();
                for (var element:entry.getValue().getAsJsonArray()) {
                    var b=element.getAsJsonArray();
                    shape=Shapes.or(shape,Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
                }
                SHAPES.put(entry.getKey(),shape.optimize());
            }
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    private ReactorShapes() { }
    public static VoxelShape machine(BlockState state) {
        return SHAPES.get("machine/"+state.getValue(ReactorMachineBlock.AXIS).getName());
    }
}
