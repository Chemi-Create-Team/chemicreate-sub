package net.createplant.mixin;

import java.util.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.fluids.*;
import net.createplant.thermal.*;
import net.createplant.block.entity.*;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native pumping with flow controls, electric work and shared-inventory reservations. */
@Mixin(value=FluidNetwork.class,remap=false)
public class NativeFluidNetworkMixin {
    @Shadow Level world;
    @Shadow BlockFace start;
    @Shadow List<Pair<BlockFace,FlowSource>> targets;
    @Unique private final Map<BlockPos,Integer> createPlant$reserved=new HashMap<>();
    @Unique private final Map<BlockPos,Integer> createPlant$electricReserved=new HashMap<>();
    @Unique private final Map<Object,Integer> createPlant$promised=new IdentityHashMap<>();
    @Unique private final FluidRoutes createPlant$routes=new FluidRoutes();
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lcom/simibubi/create/content/fluids/FluidNetwork;transferSpeed:I",opcode=org.objectweb.asm.Opcodes.PUTFIELD))
    private void createPlant$mainFlow(FluidNetwork network,int speed,Operation<Void> original){
        int multiplier=1;
        for(var at:List.of(start.getPos(),start.getConnectedPos())) {
            multiplier=Math.max(multiplier,createPlant$bores(at));
            for(var side:net.minecraft.core.Direction.values())multiplier=Math.max(multiplier,createPlant$bores(at.relative(side)));
        }
        original.call(network,speed*multiplier);
    }
    @Unique private int createPlant$bores(BlockPos at){if(!world.hasChunkAt(at))return 1;var state=world.getBlockState(at);return net.createplant.transport.PipeMaterial.pipe(state)==net.createplant.transport.PipeMaterial.STAINLESS ? net.createplant.transport.PipeMaterial.MAIN_FLOW : 1;}
    @Inject(method="reset",at=@At("HEAD"))
    private void createPlant$reset(CallbackInfo ci){createPlant$routes.clear();}
    @Inject(method="tick",at=@At("HEAD"))
    private void createPlant$begin(CallbackInfo ci){createPlant$reserved.clear();createPlant$promised.clear();createPlant$electricReserved.clear();}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/FluidStack;setAmount(I)V",ordinal=1))
    private void createPlant$removeMixtureRemainder(FluidStack stack,int remaining,Operation<Void> original){
        if(net.createplant.mixture.MixtureHelper.isMixture(stack) && remaining<=stack.getAmount())stack.shrink(stack.getAmount()-remaining);
        else original.call(stack,remaining);
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"))
    private boolean createPlant$mixtureFlow(FluidStack a,FluidStack b,Operation<Boolean> original){
        return net.createplant.mixture.MixtureHelper.isMixture(a) && net.createplant.mixture.MixtureHelper.isMixture(b) || original.call(a,b);
    }
    @Unique private int createPlant$budget(int requested){
        int total=0;var reserved=new HashMap<BlockPos,Integer>();
        for(var target:targets){var path=createPlant$routes.path(world,start,target.getFirst());if(path.isEmpty())continue;var controls=createPlant$routes.controls(path);
            int n=FlowQuota.remaining(world,controls,reserved,requested-total);n=createPlant$electricLimit(path,n);
            FlowQuota.accept(world,controls,reserved,n,false);total+=n;if(total>=requested)return requested;
        }
        return total;
    }
    @Unique private int createPlant$electricLimit(List<BlockPos> path,int requested){
        for(var at:path)if(world.getBlockEntity(at) instanceof ElectricPumpBlockEntity pump)requested=Math.min(requested,(int)(pump.storedJoules()/.1));
        return requested;
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;drain(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack createPlant$limitSource(IFluidHandler source,FluidStack request,IFluidHandler.FluidAction action,Operation<FluidStack> original){int n=createPlant$budget(request.getAmount());return n==0 ? FluidStack.EMPTY : original.call(source,request.copyWithAmount(n),action);}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack createPlant$limitGeneric(IFluidHandler source,int request,IFluidHandler.FluidAction action,Operation<FluidStack> original){int n=createPlant$budget(request);return n==0 ? FluidStack.EMPTY : original.call(source,n,action);}
    @Unique private Object createPlant$inventory(BlockFace face,IFluidHandler handler){
        var be=world.getBlockEntity(face.getConnectedPos());
        if(be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)return tank.getControllerBE();
        if(be instanceof LiddedVesselBlockEntity tank)return tank.getControllerBE();
        var turbine=TurbineThermal.source(world,face.getConnectedPos());if(turbine!=null)return turbine;
        return handler;
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)I"))
    private int createPlant$exchange(IFluidHandler target,FluidStack stack,IFluidHandler.FluidAction action,Operation<Integer> original,@Local Pair<BlockFace,FlowSource> pair,@Local(name="toTransfer") int portion){
        if(world.isClientSide || stack.isEmpty())return original.call(target,stack,action);
        int prior=action.simulate() ? Math.max(0,stack.getAmount()-portion) : 0;
        var path=createPlant$routes.path(world,start,pair.getFirst());var controls=createPlant$routes.controls(path);
        int allowed=createPlant$electricLimit(path,FlowQuota.remaining(world,controls,action.simulate() ? createPlant$reserved : Map.of(),stack.getAmount()-prior));
        if(action.simulate())for(var at:path)if(world.getBlockEntity(at) instanceof ElectricPumpBlockEntity pump)allowed=Math.min(allowed,Math.max(0,(int)(pump.storedJoules()/.1)-createPlant$electricReserved.getOrDefault(at,0)));
        Object owner=createPlant$inventory(pair.getFirst(),target);
        int promised=action.simulate() ? createPlant$promised.getOrDefault(owner,0) : 0;
        if(promised>prior)allowed=Math.min(allowed,Math.max(0,original.call(target,stack.copyWithAmount(promised+allowed),action)-promised));
        if(allowed<=0)return prior;
        int filled=original.call(target,stack.copyWithAmount(prior+allowed),action),net=Math.max(0,filled-prior);
        FlowQuota.accept(world,controls,createPlant$reserved,net,action.execute());
        if(action.simulate()){createPlant$promised.merge(owner,net,Integer::sum);for(var at:path)if(world.getBlockEntity(at) instanceof ElectricPumpBlockEntity)createPlant$electricReserved.merge(at,net,Integer::sum); }
        else if(filled>0){
            for(var at:path)if(world.getBlockEntity(at) instanceof ElectricPumpBlockEntity pump)pump.spendWork(filled*.1);
            createPlant$routes.observe(world,path,stack.copyWithAmount(filled));
        }
        return filled;
    }
}
