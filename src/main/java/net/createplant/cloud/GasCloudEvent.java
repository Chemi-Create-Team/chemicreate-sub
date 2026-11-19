package net.createplant.cloud;

import net.neoforged.bus.api.Event;

public abstract class GasCloudEvent extends Event {
    public final GasCloud cloud;

    protected GasCloudEvent(GasCloud cloud) { this.cloud = cloud; }

    public static final class Sample extends GasCloudEvent {
        Sample(GasCloud cloud) { super(cloud); }
    }

    public static final class Rebuilt extends GasCloudEvent {
        Rebuilt(GasCloud cloud) { super(cloud); }
    }
}
