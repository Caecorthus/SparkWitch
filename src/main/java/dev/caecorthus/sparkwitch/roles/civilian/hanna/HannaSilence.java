package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for "a floating player's own actions are silent for everyone else" (the float of
 * {@link HannaRules}). Called by {@code mixin/hanna/ServerWorldHannaSilenceMixin}, which wraps
 * {@code ServerWorld#playSound} and {@code #playSoundFromEntity}, the two calls every public world sound funnels
 * through; a muted sound never runs the vanilla method, so no client receives it and the Blind never perceives it.
 * <ul>
 *   <li>Source named ({@code except != null}): vanilla sends the sound to everyone but that player, whose client
 *   already played it (steps, landing, swimming, eating, anything through {@code PlayerEntity#playSound}). Muted when
 *   that player floats; nothing changes for the player.</li>
 *   <li>No source ({@code except == null}): muted only inside {@link HannaSoundScope}, when the acting player floats
 *   and the sound is within {@link #SELF_SOUND_RADIUS} of their eyes. The same packet is then sent to the actor alone,
 *   under vanilla's own range test, so their door or gun sounds to them as it always does.</li>
 *   <li>Anything else passes untouched. The no-op path (no scope, no source, or a source that does not float) reads
 *   one thread-local cell and at most one component, and allocates nothing.</li>
 * </ul>
 * Not covered, because they never reach these two calls: private sounds, {@code syncWorldEvent} sounds, and sounds a
 * client makes up on its own.
 * 「浮游玩家自身的行动对其他人静音」（{@link HannaRules} 的浮游）的服务端权威。由
 * {@code mixin/hanna/ServerWorldHannaSilenceMixin} 调用，它包裹 {@code ServerWorld#playSound} 与
 * {@code #playSoundFromEntity}（所有公开世界声音的两个汇聚点）；被静音的声音不会执行原版方法，任何客户端都收不到，盲人也感知不到。
 * <ul>
 *   <li>有来源（{@code except != null}）：原版把声音发给除该玩家外的所有人，其客户端已在本地播放（脚步、落地、游泳、进食，以及
 *   所有经过 {@code PlayerEntity#playSound} 的声音）。该玩家在浮游时静音；对其本人没有任何变化。</li>
 *   <li>无来源（{@code except == null}）：仅在 {@link HannaSoundScope} 内、行动者在浮游且声音位于其眼睛
 *   {@link #SELF_SOUND_RADIUS} 之内时静音。随后按原版自己的距离判定把同一数据包只发给行动者，其开门或开枪的声音对本人照旧。</li>
 *   <li>其余一律原样放行。空操作路径（无作用域、无来源，或来源未浮游）只读取一个线程局部单元和至多一个组件，不产生分配。</li>
 * </ul>
 * 不覆盖从不经过这两个调用的声音：私有声音、{@code syncWorldEvent} 声音，以及客户端自行生成的声音。
 */
public final class HannaSilence {
    /**
     * "Sounds she makes around herself": a sourceless sound counts as the actor's own only this close to their eyes.
     * Block reach is 4.5 from the eyes and Wathe plays door, lock and button sounds at the block centre, up to about
     * 0.9 further (one more block for the other half of a door); a gunshot or stab is played at the actor's own
     * position. Farther consequences of the same action (a victim's death across the car, a grenade landing) stay
     * audible for everyone.
     * 「她在自己身边制造的声音」：无来源声音只有距行动者眼睛这么近才算其自身的声音。方块交互距离为眼睛起 4.5 格，Wathe 的门、锁、
     * 按钮声音在方块中心播放，最多再远约 0.9 格（门的另一半再加一格）；枪声与刺击声在行动者自身位置播放。同一行动更远处的后果
     * （车厢另一头受害者的死亡声、手雷落地）对所有人仍然可闻。
     */
    public static final double SELF_SOUND_RADIUS = 6.0D;
    private static final double SELF_SOUND_RADIUS_SQUARED = SELF_SOUND_RADIUS * SELF_SOUND_RADIUS;

    private HannaSilence() {
    }

    /**
     * Pure rule. {@code sourceNamed}: the sound has an {@code except} player, and {@code sourceFloating} says whether
     * that player floats. Otherwise {@code actorFloating} and {@code actorDistanceSquared} describe the player acting
     * in {@link HannaSoundScope} (false when there is none) and the squared distance from their eyes to the sound.
     * 纯规则。{@code sourceNamed}：声音带有 {@code except} 玩家，{@code sourceFloating} 表示其是否在浮游。否则
     * {@code actorFloating} 与 {@code actorDistanceSquared} 描述 {@link HannaSoundScope} 内的行动者（没有则为 false）
     * 及其眼睛到声音的平方距离。
     */
    public static boolean mutes(boolean sourceNamed, boolean sourceFloating, boolean actorFloating,
                                double actorDistanceSquared) {
        if (sourceNamed) {
            return sourceFloating;
        }
        return actorFloating && actorDistanceSquared <= SELF_SOUND_RADIUS_SQUARED;
    }

    /**
     * {@code ServerWorld#playSound} gate: true when the broadcast must be skipped. A muted sourceless sound has
     * already been sent to the acting player alone.
     * {@code ServerWorld#playSound} 的闸门：返回 true 时必须跳过广播。被静音的无来源声音此时已单独发给行动者。
     */
    public static boolean interceptWorldSound(ServerWorld world, @Nullable PlayerEntity except, double x, double y,
                                              double z, RegistryEntry<SoundEvent> sound, SoundCategory category,
                                              float volume, float pitch, long seed) {
        if (except != null) {
            return mutes(true, isFloating(except), false, 0.0D);
        }
        ServerPlayerEntity actor = floatingActorNear(world, x, y, z);
        if (actor == null) {
            return false;
        }
        if (hears(actor, x, y, z, sound, volume)) {
            sendTo(actor, new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch, seed));
        }
        return true;
    }

    /**
     * {@code ServerWorld#playSoundFromEntity} gate, the entity-attached twin of {@link #interceptWorldSound}; the sound
     * sits at the entity's position.
     * {@code ServerWorld#playSoundFromEntity} 的闸门，是 {@link #interceptWorldSound} 的实体附着版本；声音位于实体所在位置。
     */
    public static boolean interceptEntitySound(ServerWorld world, @Nullable PlayerEntity except, Entity entity,
                                               RegistryEntry<SoundEvent> sound, SoundCategory category, float volume,
                                               float pitch, long seed) {
        if (except != null) {
            return mutes(true, isFloating(except), false, 0.0D);
        }
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        ServerPlayerEntity actor = floatingActorNear(world, x, y, z);
        if (actor == null) {
            return false;
        }
        if (hears(actor, x, y, z, sound, volume)) {
            sendTo(actor, new PlaySoundFromEntityS2CPacket(sound, category, entity, volume, pitch, seed));
        }
        return true;
    }

    /**
     * The scope's actor when the sourceless sound at this position is their own and they float, else null. Cheapest
     * checks first: the thread-local cell, the world, the distance, then the component.
     * 该位置的无来源声音属于作用域行动者自身且其在浮游时返回行动者，否则返回 null。先做最便宜的判断：线程局部单元、世界、
     * 距离，最后才是组件。
     */
    @Nullable
    private static ServerPlayerEntity floatingActorNear(ServerWorld world, double x, double y, double z) {
        ServerPlayerEntity actor = HannaSoundScope.actor();
        if (actor == null || actor.getWorld() != world) {
            return null;
        }
        double dx = x - actor.getX();
        double dy = y - actor.getEyeY();
        double dz = z - actor.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > SELF_SOUND_RADIUS_SQUARED) {
            return null;
        }
        return mutes(false, false, isFloating(actor), distanceSquared) ? actor : null;
    }

    /** Never reads the component of a removed player; a missing component reads as not floating. / 不读取已移除玩家的组件。 */
    private static boolean isFloating(PlayerEntity player) {
        if (player.isRemoved()) {
            return false;
        }
        HannaComponent component = HannaComponent.KEY.getNullable(player);
        return component != null && component.isFloating();
    }

    /**
     * Vanilla's {@code PlayerManager#sendToAround} range test for one player (the world was matched by the caller).
     * 原版 {@code PlayerManager#sendToAround} 对单个玩家的距离判定（世界已由调用方比对）。
     */
    private static boolean hears(ServerPlayerEntity player, double x, double y, double z,
                                 RegistryEntry<SoundEvent> sound, float volume) {
        double range = sound.value().getDistanceToTravel(volume);
        double dx = x - player.getX();
        double dy = y - player.getY();
        double dz = z - player.getZ();
        return dx * dx + dy * dy + dz * dz < range * range;
    }

    private static void sendTo(ServerPlayerEntity player, Packet<?> packet) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(packet);
        }
    }
}
