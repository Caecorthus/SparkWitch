package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Pure "who made this sound" rule for the Blind (D2, D3, D4). In order:
 * <ol>
 *   <li>the named actor ({@code except}): a silent actor drops the sound; an emitter within {@link
 *   BlindSoundRules#ACTOR_RADIUS} owns it; any other actor (dead, spectator, non-participant, far away) falls through;</li>
 *   <li>the player the sound was played from ({@code playSoundFromEntity}): silent drops, emitter owns, else falls
 *   through;</li>
 *   <li>the nearest non-spectator emitter or silent player within {@link BlindSoundRules#NEAREST_PLAYER_RADIUS}: a silent
 *   one drops the sound, an emitter owns it;</li>
 *   <li>otherwise an environment-only object sound.</li>
 * </ol>
 * Non-player sources (projectiles, traps, cars) are never resolved to their owner: an owner elsewhere did not make a
 * sound where it is heard, and D3 says objects light the environment only. Silent players (active Wraiths, swallowed
 * players) never emit, not even as an object pulse at their position. Invisible players are emitters (D4).
 * 盲人的纯"谁发出了这个声音"规则（D2、D3、D4），依次判断：
 * 指明的行动者（{@code except}）：静默者丢弃该声音；{@link BlindSoundRules#ACTOR_RADIUS} 内的发声者拥有它；其他行动者
 * （已死亡、旁观、非参与者、距离过远）继续向下判断；
 * 声音所依附的玩家（{@code playSoundFromEntity}）：静默者丢弃，发声者拥有，否则继续；
 * {@link BlindSoundRules#NEAREST_PLAYER_RADIUS} 内最近的非旁观发声者或静默者：静默者丢弃该声音，发声者拥有它；
 * 否则为只照亮环境的物体声。
 * 非玩家声源（投射物、陷阱、小车）从不归属到其主人：主人不在声源处，而 D3 规定物体只照亮环境。静默玩家（激活的怨灵、
 * 被吞者）永不发声，甚至不会在其位置产生物体脉冲。隐身玩家仍是发声者（D4）。
 */
public final class BlindSoundAttribution {
    /** How a player relates to sound perception. / 玩家与声音感知的关系。 */
    public enum Status {
        /** Playing, alive, not a spectator, not swallowed, not an active Wraith. / 在局、存活、非旁观、未被吞、非激活怨灵。 */
        EMITTER,
        /** Active Wraith or swallowed: their sounds become nothing at all. / 激活的怨灵或被吞者：其声音完全不产生感知。 */
        SILENT,
        /** Dead, spectating or not a participant: never named as the emitter. / 已死亡、旁观或非参与者：从不作为发声者。 */
        NONE
    }

    public enum Outcome {
        DROP,
        OBJECT,
        PLAYER
    }

    /** Side-specific reads, so the rule stays pure. / 端侧读取，使规则保持纯粹。 */
    public interface Probe<P> {
        Status status(P player);

        boolean isSpectator(P player);

        double squaredDistanceTo(P player, double x, double y, double z);
    }

    /** {@code player} is set only for {@link Outcome#PLAYER}. / 仅 {@link Outcome#PLAYER} 时设置 {@code player}。 */
    public record Result<P>(Outcome outcome, @Nullable P player) {
    }

    private static final Result<?> DROP = new Result<>(Outcome.DROP, null);
    private static final Result<?> OBJECT = new Result<>(Outcome.OBJECT, null);
    private static final double ACTOR_RADIUS_SQUARED =
            BlindSoundRules.ACTOR_RADIUS * BlindSoundRules.ACTOR_RADIUS;
    private static final double NEAREST_RADIUS_SQUARED =
            BlindSoundRules.NEAREST_PLAYER_RADIUS * BlindSoundRules.NEAREST_PLAYER_RADIUS;

    private BlindSoundAttribution() {
    }

    public static <P> Result<P> attribute(@Nullable P actor, @Nullable P sourcePlayer, List<? extends P> nearbyPlayers,
                                          double x, double y, double z, Probe<P> probe) {
        if (actor != null) {
            Status status = probe.status(actor);
            if (status == Status.SILENT) {
                return drop();
            }
            if (status == Status.EMITTER && probe.squaredDistanceTo(actor, x, y, z) <= ACTOR_RADIUS_SQUARED) {
                return new Result<>(Outcome.PLAYER, actor);
            }
        }
        if (sourcePlayer != null) {
            Status status = probe.status(sourcePlayer);
            if (status == Status.SILENT) {
                return drop();
            }
            if (status == Status.EMITTER) {
                return new Result<>(Outcome.PLAYER, sourcePlayer);
            }
        }
        P nearest = null;
        Status nearestStatus = Status.NONE;
        double best = NEAREST_RADIUS_SQUARED;
        for (int i = 0, size = nearbyPlayers.size(); i < size; i++) {
            P candidate = nearbyPlayers.get(i);
            if (candidate == null || probe.isSpectator(candidate)) {
                continue;
            }
            Status status = probe.status(candidate);
            if (status == Status.NONE) {
                continue;
            }
            double distance = probe.squaredDistanceTo(candidate, x, y, z);
            if (distance <= best) {
                best = distance;
                nearest = candidate;
                nearestStatus = status;
            }
        }
        if (nearest == null) {
            return object();
        }
        return nearestStatus == Status.SILENT ? drop() : new Result<>(Outcome.PLAYER, nearest);
    }

    @SuppressWarnings("unchecked")
    private static <P> Result<P> drop() {
        return (Result<P>) DROP;
    }

    @SuppressWarnings("unchecked")
    private static <P> Result<P> object() {
        return (Result<P>) OBJECT;
    }
}
