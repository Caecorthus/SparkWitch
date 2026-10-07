package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules.MurderousWitchRules;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Apprentice passive 魔力共鸣 / Magic Resonance (owner 2026-10-06 D1). When a living witch-faction member (Grand Witch,
 * any accomplice, the Curser) or the Murderous Witch casts a witch skill or spell within 24 blocks of a living
 * Apprentice, she alone sees a purple ripple and hears a resonance at the cast point: a position, never an identity.
 * Callers report the cast; this class filters caster role and life, so a dead caster (the Wraith Curser) never echoes.
 * 预备魔女被动「魔力共鸣」（所有者 2026-10-06 D1）。存活的魔女阵营成员（大魔女、任意共犯、诅咒者）或杀意魔女在存活的预备魔女
 * 24 格内施放魔女技能或法术时，只有她能在施法点看到紫色涟漪并听到共鸣：只给位置，绝不给身份。调用方只报告施法；本类过滤施法者
 * 的职业与存活状态，因此死亡的施法者（冤魂诅咒者）不会触发共鸣。
 */
public final class ApprenticeResonance {
    public static final double RANGE_BLOCKS = 24.0;
    private static final DustParticleEffect RIPPLE = new DustParticleEffect(new Vector3f(0.62f, 0.32f, 0.92f), 1.4f);
    private static final int RING_POINTS = 16;
    private static final double RING_RADIUS = 1.1;

    private ApprenticeResonance() {
    }

    public static boolean isResonantCaster(Role role) {
        return WitchFactionRules.isWitchFactionMember(role) || MurderousWitchRules.isMurderousWitch(role);
    }

    /** Reports a witch cast at {@code position}; harmless for any other caster. / 报告一次魔女施法；其他施法者无影响。 */
    public static void onWitchCast(ServerPlayerEntity caster, Vec3d position) {
        if (caster == null || position == null) {
            return;
        }
        ServerWorld world = caster.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || !isResonantCaster(game.getRole(caster))) {
            return;
        }
        double rangeSquared = RANGE_BLOCKS * RANGE_BLOCKS;
        for (ServerPlayerEntity listener : world.getPlayers()) {
            // A Rift Gate occupant is an alive spectator and stays deaf to resonance.
            // 裂隙门内的玩家是存活旁观者，听不到共鸣。
            if (listener == caster || listener.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(listener)
                    || game.getRole(listener) != SparkWitchRoles.apprenticeWitch()
                    || listener.squaredDistanceTo(position) > rangeSquared) {
                continue;
            }
            ripple(world, listener, position);
        }
    }

    private static void ripple(ServerWorld world, ServerPlayerEntity listener, Vec3d position) {
        double y = position.y + 0.15;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_POINTS;
            world.spawnParticles(listener, RIPPLE, true,
                    position.x + Math.cos(angle) * RING_RADIUS, y, position.z + Math.sin(angle) * RING_RADIUS,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        // A short rising column keeps the ripple readable over furniture. / 短暂上升的光柱让涟漪越过家具仍可辨认。
        world.spawnParticles(listener, ParticleTypes.WITCH, true, position.x, position.y + 1.0, position.z,
                12, 0.15, 0.8, 0.15, 0.02);
        listener.networkHandler.sendPacket(new PlaySoundS2CPacket(
                Registries.SOUND_EVENT.getEntry(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE),
                SoundCategory.PLAYERS,
                position.x,
                position.y,
                position.z,
                1.6f,
                0.7f,
                listener.getRandom().nextLong()
        ));
    }
}
