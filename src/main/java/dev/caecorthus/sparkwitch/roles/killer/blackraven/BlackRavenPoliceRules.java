package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure police-variant rules of the Black Raven (plan v2 §3.1, 2026-10-21): native Vigilante gun parity, the starting
 * revolver, and the Feather Blade friendly-fire punishment. Every role test here takes the RAW role
 * ({@code GameWorldComponent#getRole}); the disguise overlay widens only {@code isRole}.
 * 黑羽鸦作为警察变体的纯规则（计划 v2 §3.1，2026-10-21）：原生义警枪械对等、开局左轮、羽刃误杀惩罚。
 * 这里的职业判断都使用原始职业（{@code GameWorldComponent#getRole}）；伪装覆盖层只放宽 {@code isRole}。
 */
public final class BlackRavenPoliceRules {
    public static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");
    /**
     * Coins per completed task (owner, 2026-10-25), the Judge/Seeker/Control Expert amount; paid into the Black Raven's
     * own wallet, disguised or not. / 每完成一个任务的金币（所有者 2026-10-25），与法官/搜寻者/控场专家相同；无论是否伪装，
     * 都存入黑羽鸦自己的钱包。
     */
    public static final int TASK_MONEY_REWARD = 50;

    private BlackRavenPoliceRules() {
    }

    /**
     * Running round, playing and alive, RAW role Black Raven, survival, not a restricted Wraith (Seeker eligibility).
     * 对局进行中、参与且存活、原始职业为黑羽鸦、生存模式、不是受限冤魂（与搜寻者相同）。
     */
    public static boolean earnsTaskMoney(boolean active, boolean playingAndAlive, @Nullable Role rawRole,
                                         boolean spectator, boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && BlackRavenRules.isBlackRaven(rawRole)
                && !spectator && !creative && !wraithRestricted;
    }

    /**
     * Pays only on an exact FALSE Impostor answer: an absent SparkTraits reads FALSE, an unknown answer (null) never
     * pays (Judge semantics; SparkTraits pays an Impostor's task income itself).
     * 仅在内鬼答案确切为 FALSE 时发放：未安装 SparkTraits 视为 FALSE，未知（null）从不发放（与法官相同；内鬼的任务收入由
     * SparkTraits 自行支付）。
     */
    public static boolean receivesTaskMoney(@Nullable Boolean impostor) {
        return Boolean.FALSE.equals(impostor);
    }

    /**
     * True only when Wathe's gun path asks about the native Vigilante and the shooter's raw role is the Black Raven,
     * so a disguised Raven firing a revolver keeps the Vigilante treatment too.
     * 仅当 Wathe 枪械路径询问原生义警且射手的原始职业是黑羽鸦时为真；因此伪装中的黑羽鸦开枪同样享受义警待遇。
     */
    public static boolean countsAsNativeVigilanteForGun(@Nullable Role queried, @Nullable Role actual) {
        return queried != null && VIGILANTE_ID.equals(queried.identifier()) && BlackRavenRules.isBlackRaven(actual);
    }

    /**
     * The kit grants a Wathe revolver only when the player holds none anywhere in the live inventory (a forced
     * re-assign has already returned a stashed one through {@code revertForRoleReassign}).
     * 只有当前背包中任何位置都没有左轮时，套装才发放一把 Wathe 左轮（被强制重新指定时，
     * {@code revertForRoleReassign} 已先把暂存中的左轮还回）。
     */
    public static boolean grantsStartingRevolver(boolean alreadyHoldsRevolver) {
        return !alreadyHoldsRevolver;
    }

    /**
     * Feather Blade friendly fire, the Swordfish gate: both factions are pre-kill SparkFactionAPI snapshots; only a
     * civilian marker whose mark really killed a civilian is punished. An intercepted death (SparkTraits) or a
     * non-final/fake death never counts, nor does a victim that was already dead. The marker's own liveness is
     * checked separately ({@link #punishment}).
     * 羽刃误杀，与剑鱼相同的门槛：双方阵营均为击杀前的 SparkFactionAPI 快照；只有好人标记者的标记确实杀死好人时才惩罚。
     * 被拦截的死亡（SparkTraits）、非最终死亡/假死，以及原本已死亡的受害者都不算。标记者自身的存活另行判断（{@link #punishment}）。
     */
    public static boolean shouldPunishMarkKill(@Nullable Identifier markerFaction, @Nullable Identifier victimFaction,
                                               boolean deadBefore, boolean deadAfter,
                                               boolean intercepted, boolean nonFinal) {
        return FactionIds.CIVILIAN.equals(markerFaction) && FactionIds.CIVILIAN.equals(victimFaction)
                && !deadBefore && deadAfter && !intercepted && !nonFinal;
    }

    /**
     * Which punishment a settled mark applies. Nothing unless the kill qualifies AND the marker is online, still
     * playing and alive, and neither creative nor spectator (so a mark that settles after the Raven died still kills
     * but punishes nobody). Wathe's server setting picks the kind, like the revolver: {@code KILL_SHOOTER} kills the
     * marker, {@code PREVENT_GUN_PICKUP} confiscates the revolvers and bans gun pickup without a death.
     * 标记结算后施加哪种惩罚。只有击杀符合条件、且标记者在线、仍在对局中存活、既非创造也非旁观时才惩罚（因此黑羽鸦死后
     * 才结算的标记仍会击杀，但不惩罚任何人）。惩罚种类与左轮相同，由 Wathe 服务器设置决定：{@code KILL_SHOOTER} 处死标记者，
     * {@code PREVENT_GUN_PICKUP} 收走左轮并禁止捡枪，不处死。
     */
    public static MarkPunishment punishment(boolean qualifyingKill, boolean markerOnline, boolean markerPlayingAndAlive,
                                            boolean markerSurvival,
                                            @Nullable GameWorldComponent.ShootInnocentPunishment setting) {
        if (!qualifyingKill || !markerOnline || !markerPlayingAndAlive || !markerSurvival) {
            return MarkPunishment.NONE;
        }
        return setting == GameWorldComponent.ShootInnocentPunishment.PREVENT_GUN_PICKUP
                ? MarkPunishment.PREVENT_GUN_PICKUP
                : MarkPunishment.KILL_MARKER;
    }

    /** Outcome of {@link #punishment}. / {@link #punishment} 的结果。 */
    public enum MarkPunishment {
        NONE,
        /** {@code killPlayer(marker, true, null, wathe:shot_innocent)}. / 以 wathe:shot_innocent 处死标记者。 */
        KILL_MARKER,
        /** Revolvers dropped, sanity 0, gun pickup banned, no death. / 掉落左轮、理智清零、禁止捡枪，不处死。 */
        PREVENT_GUN_PICKUP
    }
}
