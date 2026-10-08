package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Server entry points for the Blind's perception, called from the {@code ServerWorld} HEAD hooks and the voice drain.
 * Every hook returns at once, allocating nothing, while no Blind is active, and never changes the vanilla call (no
 * cancel, no argument change). Only public world sounds reach these hooks; private sounds ({@code playSoundToPlayer},
 * raw packets, sounds placed at each listener) structurally never do. New public sounds must keep using
 * {@code world.playSound}/{@code playSoundFromEntity} to be perceivable. Server thread only.
 * 盲人感知的服务端入口，由 {@code ServerWorld} 的 HEAD 钩子与语音取出调用。没有激活的盲人时每个钩子都立即返回且不分配内存，
 * 并且从不改变原版调用（不取消、不改参数）。只有公开的世界声音会到达这些钩子；私有声音（{@code playSoundToPlayer}、
 * 原始数据包、在每个听者处播放的声音）在结构上永远不会。新的公开声音必须继续使用
 * {@code world.playSound}/{@code playSoundFromEntity} 才能被感知。仅服务端线程。
 */
public final class BlindSoundPerception {
    private static final BlindSoundAttribution.Probe<PlayerEntity> PROBE = new ServerProbe();

    private BlindSoundPerception() {
    }

    /** HEAD of {@code ServerWorld.playSound(PlayerEntity except, x, y, z, ...)}. / 定点声音钩子。 */
    public static void onWorldSound(ServerWorld world, @Nullable PlayerEntity except, double x, double y, double z,
                                    RegistryEntry<SoundEvent> sound, SoundCategory category) {
        if (!BlindPerceptionTargets.anyActive()) {
            return;
        }
        perceive(world, except, null, x, y, z, sound, category);
    }

    /** HEAD of {@code ServerWorld.playSoundFromEntity(PlayerEntity except, Entity, ...)}. / 实体声音钩子。 */
    public static void onEntitySound(ServerWorld world, @Nullable PlayerEntity except, @Nullable Entity entity,
                                     RegistryEntry<SoundEvent> sound, SoundCategory category) {
        if (!BlindPerceptionTargets.anyActive() || entity == null) {
            return;
        }
        perceive(world, except, entity, entity.getX(), entity.getY(), entity.getZ(), sound, category);
    }

    /**
     * HEAD of {@code ServerWorld.createExplosion}: wind-charge bursts and explosions reach clients as explosion packets,
     * not sound packets. Always a one-shot; an explosion of a non-player entity (grenade, charge) is an object.
     * {@code ServerWorld.createExplosion} 的 HEAD：风弹爆发与爆炸以爆炸数据包而非声音数据包到达客户端。始终为一次性声音；
     * 非玩家实体（手雷、风弹）的爆炸是物体声。
     */
    public static void onExplosion(ServerWorld world, @Nullable Entity entity, double x, double y, double z) {
        if (!BlindPerceptionTargets.anyActive()) {
            return;
        }
        BlindPerceiver[] perceivers = BlindPerceptionTargets.perceivers();
        if (!BlindPulseFanout.anyInRange(perceivers, world, x, y, z, BlindSoundRules.NORMAL_RANGE_FACTOR)) {
            return;
        }
        PlayerEntity sourcePlayer = entity instanceof PlayerEntity player ? player : null;
        Entity sourceObject = sourcePlayer == null ? entity : null;
        fanOut(world, null, sourcePlayer, sourceObject, sourceObject != null, x, y, z,
                BlindSoundRules.NORMAL_RANGE_FACTOR, true, false);
    }

    /** Voice drain, server thread: one proximity voice frame from a speaker. / 语音取出，服务端线程。 */
    static void onVoice(ServerPlayerEntity speaker, boolean whispering) {
        if (PROBE.status(speaker) != BlindSoundAttribution.Status.EMITTER) {
            return;
        }
        ServerWorld world = speaker.getServerWorld();
        double x = speaker.getX();
        double y = speaker.getEyeY();
        double z = speaker.getZ();
        double rangeFactor = BlindSoundRules.voiceRangeFactor(whispering);
        BlindPerceiver[] perceivers = BlindPerceptionTargets.perceivers();
        if (!BlindPulseFanout.anyInRange(perceivers, world, x, y, z, rangeFactor)) {
            return;
        }
        BlindPulseFanout.fanOut(perceivers, world, new BlindPulseFanout.Source(x, y, z, speaker.getId(), true,
                rangeFactor, speaker.getId(), 0L, false, false));
    }

    private static void perceive(ServerWorld world, @Nullable PlayerEntity except, @Nullable Entity entity,
                                 double x, double y, double z, RegistryEntry<SoundEvent> sound,
                                 SoundCategory category) {
        if (!BlindSoundRules.isPerceivableCategory(category)) {
            return;
        }
        // The id is read before the range pre-check: a x2 USEC shot reaches past the Blind's ordinary range.
        // 在距离预检之前读取 id：x2 的 USEC 枪声能传到盲人普通感知距离之外。
        Identifier soundId = sound.value().getId();
        double rangeFactor = BlindSoundRules.soundRangeFactor(soundId);
        BlindPerceiver[] perceivers = BlindPerceptionTargets.perceivers();
        if (!BlindPulseFanout.anyInRange(perceivers, world, x, y, z, rangeFactor)) {
            return;
        }
        PlayerEntity sourcePlayer = entity instanceof PlayerEntity player ? player : null;
        Entity sourceObject = sourcePlayer == null ? entity : null;
        BlindSoundRules.Handling handling = BlindSoundRules.handling(soundId, except != null, sourceObject != null,
                () -> WorldBlackoutComponent.KEY.get(world).isBlackoutActive());
        if (handling == BlindSoundRules.Handling.IGNORE) {
            return;
        }
        fanOut(world, except, sourcePlayer, sourceObject, handling == BlindSoundRules.Handling.OBJECT, x, y, z,
                rangeFactor, BlindSoundRules.isOneShot(soundId), BlindSoundRules.isFootstep(soundId));
    }

    private static void fanOut(ServerWorld world, @Nullable PlayerEntity except, @Nullable PlayerEntity sourcePlayer,
                               @Nullable Entity sourceObject, boolean objectSound, double x, double y, double z,
                               double rangeFactor, boolean oneShot, boolean footstep) {
        BlindSoundAttribution.Result<PlayerEntity> result = objectSound
                ? BlindSoundAttribution.object()
                : BlindSoundAttribution.attribute(except, sourcePlayer, world.getPlayers(), x, y, z, PROBE);
        if (result.outcome() == BlindSoundAttribution.Outcome.DROP) {
            return;
        }
        int emitter = result.player() != null ? result.player().getId() : BlindPulseFanout.NO_ENTITY;
        int key = emitter != BlindPulseFanout.NO_ENTITY ? emitter
                : sourceObject != null ? sourceObject.getId() : BlindPulseFanout.NO_ENTITY;
        long block = BlockPos.asLong(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        BlindPulseFanout.fanOut(BlindPerceptionTargets.perceivers(), world,
                new BlindPulseFanout.Source(x, y, z, emitter, false, rangeFactor, key, block, oneShot, footstep));
    }

    /**
     * Server reads behind the pure attribution rule. Distance is to the player's bounding box, so a sound at the eye
     * (gunshots) or the feet (steps) both count as "at" the player.
     * 纯归属规则背后的服务端读取。距离按玩家碰撞箱计算，因此眼部（枪声）与脚下（脚步）的声音都算"在"玩家处。
     */
    private static final class ServerProbe implements BlindSoundAttribution.Probe<PlayerEntity> {
        @Override
        public BlindSoundAttribution.Status status(PlayerEntity player) {
            if (WraithStateService.isActive(player) || NoellesTaotieSeekerBridge.isSwallowed(player)) {
                return BlindSoundAttribution.Status.SILENT;
            }
            return !player.isSpectator() && GameFunctions.isPlayerPlayingAndAlive(player)
                    ? BlindSoundAttribution.Status.EMITTER
                    : BlindSoundAttribution.Status.NONE;
        }

        @Override
        public double squaredDistanceTo(PlayerEntity player, double x, double y, double z) {
            Box box = player.getBoundingBox();
            double dx = Math.max(Math.max(box.minX - x, 0.0), x - box.maxX);
            double dy = Math.max(Math.max(box.minY - y, 0.0), y - box.maxY);
            double dz = Math.max(Math.max(box.minZ - z, 0.0), z - box.maxZ);
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
