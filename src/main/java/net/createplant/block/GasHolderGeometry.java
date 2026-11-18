package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;

/** Three nested bells retain half a block of overlap at the end of each stroke. */
public record GasHolderGeometry(int width,int height) {
    public static final int STAGES=3,MAX_WIDTH=7,MAX_HEIGHT=8;
    public double stroke() { return height-.5; }
    public double travel() { return STAGES*stroke(); }
    public double offset(double lift,int stage) {
        double offset=0;
        for (int i=0;i<=stage;i++) offset+=Math.clamp(lift-(STAGES-1-i)*stroke(),0,stroke());
        return offset;
    }
    public double inset(int stage) { return (stage+2)/16d; }
    public List<AABB> shell(int stage) {
        double a=inset(stage),b=width-a,t=1/16d,c=2/16d;
        var boxes=new ArrayList<AABB>();
        boxes.add(new AABB(a+c,0,a,b-c,height,a+t));boxes.add(new AABB(a+c,0,b-t,b-c,height,b));
        boxes.add(new AABB(a,0,a+c,a+t,height,b-c));boxes.add(new AABB(b-t,0,a+c,b,height,b-c));
        for (int x=0;x<2;x++) for (int z=0;z<2;z++) for (int step=0;step<2;step++) {
            double x0=x==0 ? a+step*t : b-(step+1)*t,z0=z==0 ? a+(1-step)*t : b-(2-step)*t;
            boxes.add(new AABB(x0,0,z0,x0+t,height,z0+t));
        }
        if (stage==STAGES-1) for (int i=0;i<3;i++) {
            double edge=a+i/16d;
            boxes.add(new AABB(edge,height+i/16d,edge,width-edge,height+(i+1)/16d,width-edge));
        }
        return List.copyOf(boxes);
    }
}
