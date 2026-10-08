package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pure server rules of one USEC rifle shot: the ordered fire re-checks (D3), the aim, the bolt cycle, target
 * eligibility and the innocent-shot punishment decision (Q8). Side-effect free so every rule is unit-testable; the
 * {@link UsecRifleFireService} gathers the facts and applies the outcome.
 * USEC 步枪单次射击的纯服务端规则：有序的开火复核（D3）、朝向、拉栓循环、目标资格与误杀惩罚判定（Q8）。无副作用，
 * 每条规则都可单测；{@link UsecRifleFireService} 负责收集事实并执行结果。
 */
public final class UsecFireRules {
    /** Ticks between the shot and the bolt sound when a round was chambered. / 有子弹上膛时，开枪到拉栓声的间隔刻数。 */
    public static final int BOLT_SOUND_DELAY_TICKS = 10;
    /** Random pitch spread of the unsuppressed shot (1 ± this). / 未消音枪声的随机音调幅度（1 ± 此值）。 */
    public static final float SHOT_PITCH_SPREAD = 0.03F;
    private static final float MAX_PITCH = 90.0F;

    private UsecFireRules() {
    }

    /**
     * Outcome of a fire request, in check order (the first failing check wins). Only {@link #EMPTY_CHAMBER} gives
     * feedback (a dry click); every other refusal is silent and costs nothing.
     * 开火请求的结果，按检查顺序排列（第一个不满足的检查即为结果）。只有 {@link #EMPTY_CHAMBER} 有反馈（空响）；其余拒绝均静默且
     * 不产生任何代价。
     */
    public enum Decision {
        /** {@link OffMatchUse.Mode#REFUSED}: a dead participant of an ACTIVE round. / ACTIVE 对局中已死亡的参与者。 */
        DEAD_PARTICIPANT,
        NOT_HOLDING,
        SPECTATOR,
        STUNNED,
        SESSION_LOCKED,
        KIDNAPPED,
        WRAITH,
        WEAPON_BLOCKED,
        COOLING_DOWN,
        EMPTY_CHAMBER,
        FIRE
    }

    /**
     * Side-effect-free facts about one fire request. / 单次开火请求的无副作用事实。
     *
     * @param mode          {@link OffMatchUse#mode(net.minecraft.entity.player.PlayerEntity)}; REFUSED or null never fires
     * @param holdingRifle  the main hand holds the USEC rifle
     * @param spectator     spectator game mode (Rift Gate occupant, swallowed, Wathe-dead)
     * @param stunned       Control Expert stun
     * @param sessionLocked Seeker remote session
     * @param kidnapped     under Kidnapper control
     * @param activeWraith  an active Wraith
     * @param weaponBlocked SparkTraits blocks a weapon action with the held rifle
     * @param coolingDown   the rifle item cooldown (round start or bolt) is running
     * @param chambered     a round sits in the chamber
     */
    public record Facts(OffMatchUse.Mode mode, boolean holdingRifle, boolean spectator, boolean stunned,
                        boolean sessionLocked, boolean kidnapped, boolean activeWraith, boolean weaponBlocked,
                        boolean coolingDown, boolean chambered) {
    }

    public static Decision decide(Facts facts) {
        if (facts.mode() == null || facts.mode() == OffMatchUse.Mode.REFUSED) {
            return Decision.DEAD_PARTICIPANT;
        }
        if (!facts.holdingRifle()) {
            return Decision.NOT_HOLDING;
        }
        if (facts.spectator()) {
            return Decision.SPECTATOR;
        }
        if (facts.stunned()) {
            return Decision.STUNNED;
        }
        if (facts.sessionLocked()) {
            return Decision.SESSION_LOCKED;
        }
        if (facts.kidnapped()) {
            return Decision.KIDNAPPED;
        }
        if (facts.activeWraith()) {
            return Decision.WRAITH;
        }
        if (facts.weaponBlocked()) {
            return Decision.WEAPON_BLOCKED;
        }
        if (facts.coolingDown()) {
            return Decision.COOLING_DOWN;
        }
        if (!facts.chambered()) {
            return Decision.EMPTY_CHAMBER;
        }
        return Decision.FIRE;
    }

    /**
     * Unit shot direction: the client's click-time aim when the payload carries a finite one, else the server rotation
     * (Death Ray rule). Yaw is wrapped and pitch clamped to [-90, 90]; the client is trusted for direction only.
     * 单位射击方向：载荷带有有限朝向时采用客户端点击瞬间的朝向，否则回退到服务端朝向（与死亡射线一致）。偏航角折回，俯仰角
     * 夹在 [-90, 90]；客户端只被信任于方向。
     */
    public static Vec3d aimDirection(boolean payloadHasAim, float payloadYaw, float payloadPitch, float serverYaw,
                                     float serverPitch) {
        float yaw = payloadHasAim ? payloadYaw : serverYaw;
        float pitch = payloadHasAim ? payloadPitch : serverPitch;
        if (!Float.isFinite(yaw)) {
            yaw = 0.0F;
        }
        if (!Float.isFinite(pitch)) {
            pitch = 0.0F;
        }
        return Vec3d.fromPolar(MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH), MathHelper.wrapDegrees(yaw)).normalize();
    }

    /**
     * The bolt cycle of one shot: the chambered round fires, then the attached magazine's top round (LIFO, Q9) is
     * chambered; with no magazine or an empty one the chamber stays empty. The bolt cooldown applies either way.
     * 单次射击的拉栓循环：弹膛内的子弹发射，随后已装弹匣顶部的子弹（后进先出，Q9）上膛；没有弹匣或弹匣为空时弹膛保持为空。
     * 无论如何都进入拉栓冷却。
     *
     * @param fired      the round that fires / 发射的子弹
     * @param next       the rifle state after the shot / 射击后的步枪状态
     * @param chambered  whether a new round entered the chamber (plays the bolt sound) / 是否有新子弹上膛（播放拉栓声）
     */
    public record Cycle(UsecAmmoType fired, UsecRifleState next, boolean chambered) {
    }

    /** Null when nothing is chambered. / 弹膛为空时为 null。 */
    public static @Nullable Cycle cycle(@Nullable UsecRifleState state) {
        if (state == null || state.chamber() == null) {
            return null;
        }
        UsecMagazineContents magazine = state.magazine();
        UsecAmmoType nextRound = magazine == null ? null : magazine.top();
        UsecMagazineContents remaining = magazine == null ? null : magazine.pop();
        return new Cycle(state.chamber(), new UsecRifleState(nextRound, remaining, state.suppressor()),
                nextRound != null);
    }

    /**
     * Whether the delayed bolt sound of a shot still plays: the shooter is online (same world, not a spectator), the
     * main hand still holds the very rifle stack that fired, that stack still carries the post-shot state (an
     * attachment action in between, e.g. UNLOAD_CHAMBER, plays its own bolt and must not be doubled), and the round
     * context is unchanged: the same {@link OffMatchUse.Mode} and the same Wathe game status (still the ACTIVE round,
     * or still the same off-match context).
     * 射击的延迟拉栓声是否仍应播放：射手在线（同一世界、非旁观者）；主手仍是开火的那把步枪物品堆；该物品堆仍保持射击后的状态
     * （期间的配件操作如退膛会自行播放拉栓声，不得重复）；对局情境未变：相同的 {@link OffMatchUse.Mode} 与相同的 Wathe 游戏状态
     * （仍是该 ACTIVE 对局，或仍是同一非对局情境）。
     */
    public static boolean boltSoundDue(boolean online, boolean sameStackInMainHand, boolean stateUnchanged,
                                       @Nullable OffMatchUse.Mode modeAtShot, @Nullable OffMatchUse.Mode modeNow,
                                       @Nullable GameWorldComponent.GameStatus statusAtShot,
                                       @Nullable GameWorldComponent.GameStatus statusNow) {
        return online && sameStackInMainHand && stateUnchanged && modeAtShot != null && modeAtShot == modeNow
                && statusAtShot == statusNow;
    }

    /**
     * Whether a candidate player may be hit (before geometry), the Shriek Gun's target semantics
     * ({@code ShriekGunService.isEligible}): never the shooter, a spectator (Rift Gate occupants, swallowed or
     * Wathe-dead players), a creative player, a player who is not playing and alive in Wathe, an active Wraith or a
     * SparkTraits Last Escape player. Vendetta isolation is {@code ShriekGunRules.vendettaAllows}: an active Vendetta
     * and everyone else touch only their exact pair. An active Vendetta is itself an active, promoted Wraith, so it is
     * never a target; there is no bound-killer exception.
     * 候选玩家能否被命中（几何判定之前），与啸音铳的目标语义一致（{@code ShriekGunService.isEligible}）：射手本人、旁观者
     * （裂隙门内、被吞下或 Wathe 判定死亡的玩家）、创造模式玩家、在 Wathe 中并非在局且存活的玩家、激活冤魂与 SparkTraits
     * 绝处逢生玩家都不能命中。复仇者隔离即 {@code ShriekGunRules.vendettaAllows}：激活的复仇者与其他人只能触及其确切配对。
     * 激活的复仇者本身就是已激活、已晋升的冤魂，因此永远不是目标；不存在绑定杀手例外。
     */
    public static boolean targetEligible(boolean self, boolean spectatorOrCreative, boolean playingAndAlive,
                                         boolean activeWraith, boolean lastEscape, boolean shooterVendetta,
                                         boolean targetVendetta, boolean exactPair) {
        return !self && !spectatorOrCreative && playingAndAlive && !activeWraith && !lastEscape
                && vendettaAllows(shooterVendetta, targetVendetta, exactPair);
    }

    /**
     * Same rule as {@code ShriekGunRules.vendettaAllows} (kept local so USEC does not depend on a witch role).
     * 与 {@code ShriekGunRules.vendettaAllows} 相同的规则（保留在本地，避免 USEC 依赖魔女职业包）。
     */
    static boolean vendettaAllows(boolean shooterVendetta, boolean targetVendetta, boolean exactPair) {
        return !(shooterVendetta || targetVendetta) || exactPair;
    }

    /**
     * Hit punishment outcome (Q8 and O1, revolver parity); every non-custom one runs 4 ticks after the hit.
     * 命中惩罚结果（Q8 与 O1，与左轮一致）；除自定义惩罚外均在命中后 4 刻执行。
     */
    public enum Punishment {
        NONE,
        /** A {@code ShouldPunishGunShooter} listener supplied its own punishment. / 监听器提供了自定义惩罚。 */
        CUSTOM,
        /**
         * Wathe {@code KILL_SHOOTER}: as {@link #CONFISCATE}, then the USEC dies ({@code wathe:shot_innocent}); Wathe
         * also takes the revolver before it kills. / 同 {@link #CONFISCATE}，随后 USEC 死亡；Wathe 同样先收走左轮再处死。
         */
        KILL_SHOOTER,
        /**
         * Wathe {@code PREVENT_GUN_PICKUP}: sanity 0, gun pickup prevented, the rifle is confiscated.
         * 理智清零、禁止拾枪，并没收狙击步枪。
         */
        CONFISCATE
    }

    /**
     * A civilian shot a civilian (SparkFactionAPI effective factions, pre-shot snapshot). / 好人射击好人（SparkFactionAPI
     * 有效阵营，射击前快照）。
     */
    public static boolean isInnocentShot(@Nullable Identifier shooterFaction, @Nullable Identifier victimFaction) {
        return FactionIds.CIVILIAN.equals(shooterFaction) && FactionIds.CIVILIAN.equals(victimFaction);
    }

    /**
     * Revolver-parity punishment for a player hit (owner O1, 2026-10-07; Wathe's revolver receiver): it
     * is decided on the hit itself, so a victim saved by a Saint, a shield, Last Stand or a Judge denial still costs
     * the shooter. A listener's custom result wins for any hit, as in Wathe; otherwise only an innocent shot by a
     * non-creative shooter that no listener cancelled is punished, by Wathe's configured punishment
     * ({@code PREVENT_GUN_PICKUP} confiscates, anything else kills the shooter, Wathe's own default). No backfire
     * roll: Wathe's backfire chance defaults to 0.
     * 与左轮一致的命中惩罚（所有者 O1，2026-10-07；Wathe 左轮接收器）：在命中时即作判定，因此即使受害者
     * 被圣徒、护盾、背水一战或法官禁杀救下，射手仍要受罚。与 Wathe 一样，监听器的自定义结果对任何命中都优先；否则只有未被
     * 监听器取消、非创造模式射手的误杀才受罚，执行 Wathe 配置的惩罚（{@code PREVENT_GUN_PICKUP} 没收，其余一律处死射手，
     * 即 Wathe 自身的默认值）。没有走火判定：Wathe 的走火概率默认为 0。
     */
    public static Punishment punishment(boolean innocentShot, boolean shooterCreative,
                                        @Nullable ShouldPunishGunShooter.PunishResult eventResult,
                                        @Nullable GameWorldComponent.ShootInnocentPunishment configured) {
        if (eventResult != null && eventResult.hasCustomPunishment()) {
            return Punishment.CUSTOM;
        }
        if (!innocentShot || shooterCreative || (eventResult != null && !eventResult.shouldPunish())) {
            return Punishment.NONE;
        }
        return configured == GameWorldComponent.ShootInnocentPunishment.PREVENT_GUN_PICKUP
                ? Punishment.CONFISCATE
                : Punishment.KILL_SHOOTER;
    }
}
