package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import java.util.Set;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure filter rules for the Blind's server sound perception (D3, C3, C9). Ambience never counts: the AMBIENT, MUSIC and
 * RECORDS categories, every {@code *:ambient.*} id, and Wathe's light sounds when no player is named as the actor (the
 * blackout flicker and a player's light switch are the same null-actor call, so both are dropped). The one-shot list is
 * deliberately small: gunshots, explosions and door toggles bypass the per-emitter throttle; explosions created through
 * {@code ServerWorld.createExplosion} are always one-shots.
 * 盲人服务端声音感知的纯过滤规则（D3、C3、C9）。氛围音永不计入：AMBIENT、MUSIC、RECORDS 类别，所有
 * {@code *:ambient.*} id，以及未指明行动玩家时的 Wathe 灯光声（停电闪烁与玩家开关灯是同一个无行动者调用，因此一并丢弃）。
 * 一次性声音名单刻意保持很短：枪声、爆炸与门的开关不受同一发声者节流限制；经 {@code ServerWorld.createExplosion} 的爆炸
 * 一律视为一次性声音。
 */
public final class BlindSoundRules {
    /** A sound with no usable actor is attributed to the nearest participant within this many blocks. / 无可用行动者时按此距离归属最近的参与者。 */
    public static final double NEAREST_PLAYER_RADIUS = 1.5;
    /**
     * A named actor ({@code except}) farther than this from the sound is not at the sound, so attribution falls through
     * instead of outlining someone elsewhere (D2/D3). Generous: block reach is 4.5.
     * 指明的行动者（{@code except}）若距声源超过此距离，就不在声源处，归属会继续向下判断，而不是勾勒远处的人（D2/D3）。
     * 留有余量：方块交互距离为 4.5。
     */
    public static final double ACTOR_RADIUS = 8.0;
    /** C9: a whisper halves the Blind's range for that voice pulse. / C9：悄悄话使该语音脉冲的感知距离减半。 */
    public static final double WHISPER_RANGE_FACTOR = 0.5;
    /** Largest possible perception range (ComTac and Attune), used as a cheap pre-cull. / 最大可能感知距离，用作廉价预筛。 */
    public static final int MAX_PERCEPTION_RANGE = BlindRules.perceptionRange(true, true);

    /**
     * One-shot sounds that bypass the per-(emitter, Blind) throttle: every Wathe-style gunshot (revolver, derringer,
     * firecracker decoy, Demon Hunter, Assassin guess), grenade and bomb explosions, generic explosions (Hunter shotgun)
     * and door toggles.
     * 不受（发声者，盲人）节流限制的一次性声音：所有 Wathe 式枪声（左轮、德林杰、鞭炮诱饵、恶魔猎手、刺客猜测）、
     * 手雷与炸弹爆炸、通用爆炸（猎人霰弹枪）以及门的开关。
     */
    static final Set<Identifier> ONE_SHOT_SOUNDS = Set.of(
            Identifier.of("wathe", "item.revolver.shoot"),
            Identifier.of("wathe", "item.grenade.explode"),
            Identifier.of("noellesroles", "item.bomb.explode"),
            Identifier.of("minecraft", "entity.generic.explode"),
            Identifier.of("wathe", "block.door.toggle"));

    /** Light sounds ignored unless a player is the named actor (blackout flicker, D3). / 除非指明玩家行动者否则忽略的灯光声。 */
    static final Set<Identifier> ACTORLESS_LIGHT_SOUNDS = Set.of(
            Identifier.of("wathe", "block.light.toggle"),
            Identifier.of("wathe", "block.button.toggle_no_power"));

    private static final String AMBIENT_PATH_PREFIX = "ambient.";

    private BlindSoundRules() {
    }

    /** D3: AMBIENT, MUSIC and RECORDS never count. / D3：AMBIENT、MUSIC、RECORDS 永不计入。 */
    public static boolean isPerceivableCategory(@Nullable SoundCategory category) {
        return category != null
                && category != SoundCategory.AMBIENT
                && category != SoundCategory.MUSIC
                && category != SoundCategory.RECORDS;
    }

    /**
     * Ambience by id: any {@code ambient.*} path, and Wathe's light sounds when no player is the named actor.
     * 按 id 判断的氛围音：任何 {@code ambient.*} 路径，以及未指明玩家行动者时的 Wathe 灯光声。
     */
    public static boolean isIgnoredSound(@Nullable Identifier soundId, boolean namedActor) {
        if (soundId == null) {
            return true;
        }
        return soundId.getPath().startsWith(AMBIENT_PATH_PREFIX)
                || (!namedActor && ACTORLESS_LIGHT_SOUNDS.contains(soundId));
    }

    public static boolean isOneShot(@Nullable Identifier soundId) {
        return soundId != null && ONE_SHOT_SOUNDS.contains(soundId);
    }

    /** The Blind's range for one pulse: whispers count half (C9). / 单个脉冲的感知距离：悄悄话按一半计算（C9）。 */
    public static double effectiveRange(int perceptionRange, boolean whispering) {
        return whispering ? perceptionRange * WHISPER_RANGE_FACTOR : perceptionRange;
    }

    /** Inclusive squared-distance range test. / 含边界的平方距离判断。 */
    public static boolean withinRange(double squaredDistance, double range) {
        return range > 0.0 && squaredDistance <= range * range;
    }
}
