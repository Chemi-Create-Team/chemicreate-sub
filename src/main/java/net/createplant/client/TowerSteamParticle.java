package net.createplant.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Long-lived white condensate with bounded growth, rather than combustion smoke. */
public final class TowerSteamParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private TowerSteamParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);this.sprites = sprites;
        xd = dx;yd = dy;zd = dz;gravity = 0;friction = .995f;hasPhysics = false;
        quadSize = 2.8f + random.nextFloat() * .8f;lifetime = 100 + random.nextInt(40);
        setColor(.97f, .98f, 1);setAlpha(0);setSpriteFromAge(sprites);
    }
    @Override public void tick() {
        super.tick();setSpriteFromAge(sprites);
        alpha = .6f * Math.min(1, age / 10f) * Mth.clamp((lifetime - age) / 35f, 0, 1);
    }
    @Override public float getQuadSize(float partialTicks) { return quadSize * (.7f + .8f * (age + partialTicks) / lifetime); }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            var quality = Minecraft.getInstance().options.particles().get();
            if (quality == ParticleStatus.MINIMAL || quality == ParticleStatus.DECREASED && level.random.nextBoolean()) return null;
            return new TowerSteamParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
