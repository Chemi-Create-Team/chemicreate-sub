package net.createplant.atomics.nuclear;

import net.createplant.block.Finite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Three effective activity groups, in Bq per item or per bucket, at a saved game tick. */
public record Radionuclides(double shortLived, double cesium, double longLived, long tick) {
    public static final Codec<Radionuclides> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.DOUBLE
                                                            .fieldOf("short")
                                                            .forGetter(Radionuclides::shortLived),
                                                    Codec.DOUBLE
                                                            .fieldOf("cesium")
                                                            .forGetter(Radionuclides::cesium),
                                            Codec.DOUBLE
                                                            .fieldOf("long")
                                                            .forGetter(Radionuclides::longLived),
                                                    Codec.LONG
                                                            .fieldOf("tick")
                                                            .forGetter(Radionuclides::tick))
                                    .apply(i, Radionuclides::new));
    public static final Radionuclides ZERO = new Radionuclides(0, 0, 0, 0);

    public Radionuclides {
        shortLived = Finite.of(shortLived, 0, 0, 1e30);
        cesium = Finite.of(cesium, 0, 0, 1e30);
        longLived = Finite.of(longLived, 0, 0, 1e30);
        tick = Math.max(0, tick);
        if (shortLived + cesium + longLived == 0) tick = 0;
    }

    public Radionuclides at(long now) {
        double seconds = Math.max(0, now - tick) / 20d * RadiationPhysics.TIME_MULTIPLIER;
        return new Radionuclides(
                shortLived * RadiationPhysics.decay(seconds, RadiationPhysics.SHORT_HALF_LIFE),
                cesium * RadiationPhysics.decay(seconds, RadiationPhysics.CESIUM_HALF_LIFE),
                longLived * RadiationPhysics.decay(seconds, RadiationPhysics.LONG_HALF_LIFE),
                now);
    }

    public double total() {
        return shortLived + cesium + longLived;
    }

    public Radionuclides scale(double factor) {
        return new Radionuclides(shortLived * factor, cesium * factor, longLived * factor, tick);
    }

    public Radionuclides plus(Radionuclides other, long now) {
        var a = at(now);
        var b = other.at(now);
        return new Radionuclides(
                a.shortLived + b.shortLived, a.cesium + b.cesium, a.longLived + b.longLived, now);
    }
}
