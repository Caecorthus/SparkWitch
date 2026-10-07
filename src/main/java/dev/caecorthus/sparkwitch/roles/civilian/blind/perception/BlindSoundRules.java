package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure filter rules for the Blind's server sound perception (D3, C3, C9). Ambience never counts: the AMBIENT, MUSIC and
 * RECORDS categories and every {@code *:ambient.*} id. Objects light the environment only: Wathe's door toggle (always
 * played with no actor, auto-close included), Wathe's light sounds when no player is named as the actor (dropped
 * while a Wathe blackout runs, because its flicker is the same null-actor call), and anything played from a non-player
 * entity. The one-shot list is deliberately small: gunshots, explosions and door sounds bypass the per-emitter
 * throttle; explosions created through {@code ServerWorld.createExplosion} are always one-shots. A few sound ids carry a
 * range factor (the USEC rifle shots) that scales each Blind's range for that pulse; volume is never read.
 * 盲人服务端声音感知的纯过滤规则（D3、C3、C9）。氛围音永不计入：AMBIENT、MUSIC、RECORDS 类别以及所有
 * {@code *:ambient.*} id。物体只照亮环境：Wathe 的门开关声（始终以无行动者方式播放，包括自动关门）、未指明行动玩家时的
 * Wathe 灯光声（Wathe 停电期间丢弃，因为停电闪烁是同一个无行动者调用），以及从非玩家实体处播放的任何声音。一次性声音
 * 名单刻意保持很短：枪声、爆炸与门的声音不受同一发声者节流限制；经 {@code ServerWorld.createExplosion} 的爆炸一律视为
 * 一次性声音。少数声音 id 带有距离系数（USEC 步枪枪声），对该次脉冲缩放每个盲人的感知距离；从不读取音量。
 */
public final class BlindSoundRules {
    /**
     * A sound with no usable actor belongs to a participant only when it lies inside that participant's hitbox or
     * within this many blocks of it (D2/D3); anything farther is an object.
     * 无可用行动者的声音只有位于某参与者碰撞箱内、或距其不超过此距离时才归属于该参与者（D2/D3）；更远的一律为物体声。
     */
    public static final double HITBOX_MARGIN = 0.3;
    /**
     * A named actor ({@code except}) farther than this from the sound is not at the sound, so attribution falls through
     * instead of outlining someone elsewhere (D2/D3). Generous: block reach is 4.5.
     * 指明的行动者（{@code except}）若距声源超过此距离，就不在声源处，归属会继续向下判断，而不是勾勒远处的人（D2/D3）。
     * 留有余量：方块交互距离为 4.5。
     */
    public static final double ACTOR_RADIUS = 8.0;
    /** Range factor of an ordinary sound, explosion or voice frame. / 普通声音、爆炸或语音帧的距离系数。 */
    public static final double NORMAL_RANGE_FACTOR = 1.0;
    /** C9: a whisper halves the Blind's range for that voice pulse. / C9：悄悄话使该语音脉冲的感知距离减半。 */
    public static final double WHISPER_RANGE_FACTOR = 0.5;

    /**
     * Stable contract: per-sound-id range factors, multiplied into each Blind's own range for that one pulse (USEC Q6,
     * D12): the unsuppressed USEC rifle shot carries twice as far (20/60/100/300), the suppressed one half as far
     * (5/15/25/75). Every other id, the Wathe revolver included, is {@link #NORMAL_RANGE_FACTOR}. Volume is never
     * read: a new loud or quiet sound joins this table instead.
     * 稳定契约：按声音 id 的距离系数，对该次脉冲乘到每个盲人自身的感知距离上（USEC Q6、D12）：未消音的 USEC 步枪枪声
     * 传两倍远（20/60/100/300），消音枪声传一半远（5/15/25/75）。其余所有 id（包括 Wathe 左轮）都是
     * {@link #NORMAL_RANGE_FACTOR}。从不读取音量：新的响亮或安静声音应加入此表。
     */
    static final Map<Identifier, Double> SOUND_RANGE_FACTORS = Map.of(
            UsecRules.SHOOT_SOUND_ID, UsecRules.BLIND_LOUD_RANGE_FACTOR,
            UsecRules.SHOOT_SUPPRESSED_SOUND_ID, UsecRules.BLIND_SUPPRESSED_RANGE_FACTOR);
    /**
     * The largest factor any source can carry (the unsuppressed USEC shot, x2).
     * 任一声源可能携带的最大系数（未消音 USEC 枪声，x2）。
     */
    public static final double MAX_RANGE_FACTOR = maxRangeFactor();
    /**
     * Largest possible effective range, used as a cheap pre-cull: the Attuned ComTac range (150) times
     * {@link #MAX_RANGE_FACTOR} (x2), i.e. 300.
     * 最大可能有效感知距离，用作廉价预筛：佩戴 ComTac 且共鸣时的距离（150）乘以 {@link #MAX_RANGE_FACTOR}（x2），即 300。
     */
    public static final int MAX_PERCEPTION_RANGE =
            (int) Math.ceil(BlindRules.perceptionRange(true, true) * MAX_RANGE_FACTOR);

    /**
     * One-shot sounds that bypass the per-(emitter, Blind) throttle: every Wathe-style gunshot (revolver, derringer,
     * firecracker decoy, Demon Hunter, Assassin guess), both USEC rifle shots and its bolt (played within the 10-tick
     * throttle window after the shot), grenade, M67 and bomb explosions, generic explosions (Hunter shotgun), Wathe's
     * door toggle and every vanilla door, trapdoor and fence-gate open or close.
     * 不受（发声者，盲人）节流限制的一次性声音：所有 Wathe 式枪声（左轮、德林杰、鞭炮诱饵、恶魔猎手、刺客猜测）、
     * USEC 步枪的两种枪声及拉栓声（拉栓在枪声后 10 刻节流窗口内播放）、手雷、M67 与炸弹爆炸、通用爆炸（猎人霰弹枪）、
     * Wathe 的门开关，以及所有原版门、活板门与栅栏门的开关声。
     */
    static final Set<Identifier> ONE_SHOT_SOUNDS = oneShotSounds();

    /** Always an environment-only object pulse, even with a named actor (D3). / 始终为只照亮环境的物体脉冲（D3）。 */
    static final Set<Identifier> OBJECT_SOUNDS = Set.of(Identifier.of("wathe", "block.door.toggle"));

    /**
     * Light sounds that are objects when no player is the named actor, and dropped then while a blackout runs (D3).
     * 未指明玩家行动者时为物体声的灯光声；此时若停电正在进行则丢弃（D3）。
     */
    static final Set<Identifier> ACTORLESS_LIGHT_SOUNDS = Set.of(
            Identifier.of("wathe", "block.light.toggle"),
            Identifier.of("wathe", "block.button.toggle_no_power"));

    /**
     * Silent placeholder sounds: vanilla broadcasts {@code minecraft:intentionally_empty} for silent equips (the ComTac
     * VIII) with a null actor, which must not reveal the wearer.
     * 静音占位声音：原版对静音穿戴（ComTac VIII）会以无行动者方式广播 {@code minecraft:intentionally_empty}，不能借此暴露佩戴者。
     */
    static final Identifier INTENTIONALLY_EMPTY = Identifier.ofVanilla("intentionally_empty");

    private static final String AMBIENT_PATH_PREFIX = "ambient.";
    private static final String STEP_PATH_SUFFIX = ".step";

    private BlindSoundRules() {
    }

    /** D3: AMBIENT, MUSIC and RECORDS never count. / D3：AMBIENT、MUSIC、RECORDS 永不计入。 */
    public static boolean isPerceivableCategory(@Nullable SoundCategory category) {
        return category != null
                && category != SoundCategory.AMBIENT
                && category != SoundCategory.MUSIC
                && category != SoundCategory.RECORDS;
    }

    /** How the perception pipeline treats one public sound. / 感知管线如何处理一个公开声音。 */
    public enum Handling {
        /** Never perceived. / 永不被感知。 */
        IGNORE,
        /** An environment-only OBJECT pulse at the source, never a player. / 声源处只照亮环境的 OBJECT 脉冲，从不显示玩家。 */
        OBJECT,
        /** Resolved by {@link BlindSoundAttribution}. / 交由 {@link BlindSoundAttribution} 判定。 */
        ATTRIBUTE
    }

    /**
     * Ignores the silent placeholder and any {@code ambient.*} path; Wathe's door toggle, a sound played from a
     * non-player entity, and an actorless Wathe light sound are objects, except that the light sound is ignored while
     * {@code blackoutActive} (asked only for that sound); everything else is attributed.
     * 忽略静音占位与任何 {@code ambient.*} 路径；Wathe 门开关声、从非玩家实体处播放的声音、无行动者的 Wathe 灯光声都是物体声，
     * 但停电期间（仅对该灯光声询问 {@code blackoutActive}）忽略灯光声；其余声音交由归属判定。
     */
    public static Handling handling(@Nullable Identifier soundId, boolean namedActor, boolean nonPlayerSource,
                                    BooleanSupplier blackoutActive) {
        if (soundId == null || INTENTIONALLY_EMPTY.equals(soundId)
                || soundId.getPath().startsWith(AMBIENT_PATH_PREFIX)) {
            return Handling.IGNORE;
        }
        if (!namedActor && ACTORLESS_LIGHT_SOUNDS.contains(soundId)) {
            return blackoutActive.getAsBoolean() ? Handling.IGNORE : Handling.OBJECT;
        }
        return nonPlayerSource || OBJECT_SOUNDS.contains(soundId) ? Handling.OBJECT : Handling.ATTRIBUTE;
    }

    /**
     * Footstep sounds are the block sound groups' step events, which all end in {@code .step} (vanilla
     * {@code block.stone.step}, Wathe's {@code block.vent_shaft.step}). Landing ({@code .fall}) is not a footstep.
     * 脚步声是方块声音组的踏步事件，路径都以 {@code .step} 结尾（原版 {@code block.stone.step}、Wathe 的
     * {@code block.vent_shaft.step}）。落地声（{@code .fall}）不算脚步。
     */
    public static boolean isFootstep(@Nullable Identifier soundId) {
        return soundId != null && soundId.getPath().endsWith(STEP_PATH_SUFFIX);
    }

    public static boolean isOneShot(@Nullable Identifier soundId) {
        return soundId != null && ONE_SHOT_SOUNDS.contains(soundId);
    }

    /**
     * The range factor of one public sound: its {@link #SOUND_RANGE_FACTORS} entry, else {@link #NORMAL_RANGE_FACTOR}.
     * 单个公开声音的距离系数：取其 {@link #SOUND_RANGE_FACTORS} 条目，否则为 {@link #NORMAL_RANGE_FACTOR}。
     */
    public static double soundRangeFactor(@Nullable Identifier soundId) {
        return soundId == null ? NORMAL_RANGE_FACTOR : SOUND_RANGE_FACTORS.getOrDefault(soundId, NORMAL_RANGE_FACTOR);
    }

    /** The range factor of one voice frame: whispers count half (C9). / 单个语音帧的距离系数：悄悄话按一半计算（C9）。 */
    public static double voiceRangeFactor(boolean whispering) {
        return whispering ? WHISPER_RANGE_FACTOR : NORMAL_RANGE_FACTOR;
    }

    /**
     * The Blind's range for one pulse: its own range times the source's factor ({@link #soundRangeFactor},
     * {@link #voiceRangeFactor}).
     * 单个脉冲的感知距离：盲人自身距离乘以声源系数（{@link #soundRangeFactor}、{@link #voiceRangeFactor}）。
     */
    public static double effectiveRange(int perceptionRange, double rangeFactor) {
        return perceptionRange * rangeFactor;
    }

    /** Inclusive squared-distance range test. / 含边界的平方距离判断。 */
    public static boolean withinRange(double squaredDistance, double range) {
        return range > 0.0 && squaredDistance <= range * range;
    }

    private static double maxRangeFactor() {
        double max = NORMAL_RANGE_FACTOR;
        for (double factor : SOUND_RANGE_FACTORS.values()) {
            max = Math.max(max, factor);
        }
        return max;
    }

    private static Set<Identifier> oneShotSounds() {
        Set<Identifier> sounds = new HashSet<>(Set.of(
                Identifier.of("wathe", "item.revolver.shoot"),
                UsecRules.SHOOT_SOUND_ID,
                UsecRules.SHOOT_SUPPRESSED_SOUND_ID,
                UsecRules.BOLT_SOUND_ID,
                Identifier.of("wathe", "item.grenade.explode"),
                Identifier.of("sparkstrength", "item.m67.explode"),
                Identifier.of("noellesroles", "item.bomb.explode"),
                Identifier.of("minecraft", "entity.generic.explode"),
                Identifier.of("wathe", "block.door.toggle")));
        for (String material : new String[]{"wooden", "iron", "copper", "bamboo_wood", "cherry_wood", "nether_wood"}) {
            for (String block : new String[]{"door", "trapdoor"}) {
                sounds.add(Identifier.ofVanilla("block." + material + "_" + block + ".open"));
                sounds.add(Identifier.ofVanilla("block." + material + "_" + block + ".close"));
            }
        }
        for (String gate : new String[]{"fence_gate", "bamboo_wood_fence_gate", "cherry_wood_fence_gate",
                "nether_wood_fence_gate"}) {
            sounds.add(Identifier.ofVanilla("block." + gate + ".open"));
            sounds.add(Identifier.ofVanilla("block." + gate + ".close"));
        }
        return Set.copyOf(sounds);
    }
}
