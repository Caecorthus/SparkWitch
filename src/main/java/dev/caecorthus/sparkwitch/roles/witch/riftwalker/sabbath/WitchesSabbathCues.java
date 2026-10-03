package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * World-visible presentation of a Witches' Sabbath (plan §10): an indigo witch circle at the caster's feet, a puff
 * where each teammate vanished (a counterplay clue for anyone nearby), arrival particles with a teleport sound, and
 * owner-only actionbar lines. Never a global chat announcement; the replay line is the only record. Server only.
 * 魔女集会的世界可见表现（plan §10）：施放者脚下的靛蓝魔女阵、每名队友消失处的一团粒子（给附近玩家的反制线索）、
 * 到达粒子与传送音效，以及仅本人可见的动作栏提示。从不发全局聊天公告；回放是唯一记录。仅服务端。
 */
final class WitchesSabbathCues {
    private static final DustParticleEffect CIRCLE_DUST = new DustParticleEffect(new Vector3f(
            ((RiftwalkerRules.COLOR >> 16) & 0xFF) / 255.0F,
            ((RiftwalkerRules.COLOR >> 8) & 0xFF) / 255.0F,
            (RiftwalkerRules.COLOR & 0xFF) / 255.0F), 1.2F);
    private static final double CIRCLE_RADIUS = 1.6;
    private static final int CIRCLE_POINTS = 32;

    private WitchesSabbathCues() {
    }

    /** The caster's witch circle and cast sound. / 施放者的魔女阵与施法音效。 */
    static void circle(ServerWorld world, Vec3d feet) {
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = Math.PI * 2.0 * i / CIRCLE_POINTS;
            double x = feet.x + Math.cos(angle) * CIRCLE_RADIUS;
            double z = feet.z + Math.sin(angle) * CIRCLE_RADIUS;
            world.spawnParticles(CIRCLE_DUST, x, feet.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.WITCH, x, feet.y + 0.2, z, 1, 0.05, 0.1, 0.05, 0.0);
            }
        }
        world.spawnParticles(ParticleTypes.ENCHANT, feet.x, feet.y + 1.0, feet.z, 40, 0.6, 0.6, 0.6, 0.6);
        world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS,
                0.8F, 1.1F);
    }

    /** Where a teammate vanished, so nearby players can see it. / 队友消失处，附近玩家可以看到。 */
    static void departure(ServerWorld world, Box body) {
        Vec3d center = body.getCenter();
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 30, 0.3, 0.6, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 10, 0.3, 0.6, 0.3, 0.0);
    }

    /** Arrival burst and teleport sound. / 到达粒子与传送音效。 */
    static void arrival(ServerWorld world, Box body) {
        Vec3d center = body.getCenter();
        world.spawnParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 30, 0.3, 0.6, 0.3, 0.3);
        world.playSound(null, center.x, body.minY, center.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 0.6F, 1.2F);
    }

    /** Owner-only actionbar for the pulled teammate. / 仅被召集者可见的动作栏提示。 */
    static void notifySummoned(ServerPlayerEntity target, ServerPlayerEntity caster) {
        target.sendMessage(Text.translatable(WitchesSabbathRules.SUMMONED, caster.getName()), true);
    }

    /** Owner-only actionbar for the caster. / 仅施放者可见的动作栏提示。 */
    static void notifyCaster(ServerPlayerEntity caster, int pulled, int leftBehind) {
        Text message = leftBehind > 0
                ? Text.translatable(WitchesSabbathRules.CAST_PARTIAL, pulled, leftBehind)
                : Text.translatable(WitchesSabbathRules.CAST, pulled);
        caster.sendMessage(message, true);
    }
}
