package dev.caecorthus.sparkwitch.roles.civilian.junker;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Pure corpse-search gates (owner decisions 2026-11-04): booleans (or boxes) in, a verdict out, no world access.
 * {@link JunkerService} reads every input from server state. A refusal of any kind costs nothing: no cooldown, no item,
 * and only an empty corpse is marked searched.
 * 纯规则的搜刮门槛（所有者 2026-11-04 决定）：输入布尔值、输出判定，不访问世界。{@link JunkerService} 从服务端状态
 * 读取每个输入。任何拒绝都不消耗任何东西：不进冷却、不拿物品，只有空尸体会被标记为已搜刮。
 */
public final class JunkerSearchRules {
    /** Outcome of a search request (the ability key). / 搜刮请求（技能键）的结果。 */
    public enum StartVerdict {
        /** Start the five-second search. / 开始五秒搜刮。 */
        START,
        /** Refused silently (state gate, busy, cooling down, not aiming at a visible body). / 静默拒绝。 */
        REFUSE,
        /** Refused by Grand Witch Fear; the player is told why. / 被大魔女恐惧拒绝，并提示原因。 */
        FEARED,
        /** Unspent loot that blocks the next search is still carried. / 仍带着挡住下一次搜刮的战利品。 */
        BLOCKED,
        /** No empty hotbar slot for the loot. / 快捷栏没有空位放战利品。 */
        HOTBAR_FULL,
        /** This corpse was already searched. / 这具尸体已被搜刮过。 */
        SEARCHED,
        /** Nothing to take; the corpse is marked searched at once (accepted tell, owner 2026-11-04). / 尸体上什么都没有，立即标记为已搜刮。 */
        EMPTY
    }

    /** Outcome when the five seconds are up; every outcome but TRANSFER takes nothing and costs nothing. / 五秒结束时的结果。 */
    public enum CompletionVerdict {
        /** Move one stack from the dead owner and start the cooldown. / 从死者身上转移一组物品并进入冷却。 */
        TRANSFER,
        /** Blocking loot was picked back up meanwhile. / 期间又捡回了挡搜刮的战利品。 */
        BLOCKED,
        /** The hotbar filled up meanwhile. / 期间快捷栏被占满。 */
        HOTBAR_FULL,
        /** Someone else finished first. / 别人先搜完了。 */
        SEARCHED,
        /** Nothing left, or the owner is back in play. / 已没有东西，或死者已重新入局。 */
        EMPTY
    }

    /**
     * The state gates that hold for the whole search, re-read on the request and on every search tick.
     * 整个搜刮过程中都必须成立的状态门槛，在请求时与每个搜刮刻重新读取。
     *
     * @param ragpicker        the real (raw) role is the 拾荒者 / 真实职业是拾荒者
     * @param roundActive      the round is ACTIVE / 回合为 ACTIVE
     * @param playingAndAlive  Wathe's playing-and-alive check / Wathe 的在局存活判定
     * @param spectator        spectator game mode / 旁观模式
     * @param swallowed        swallowed by a NoellesRoles Taotie / 被饕餮吞下
     * @param stunned          Control Expert stun (or a landing Black Raven) / 控场专家眩晕
     * @param roleSkillBlocked SparkTraits role-skill block / SparkTraits 职业技能封锁
     * @param dueling          either duelist of a running Knight duel / 进行中骑士决斗的任一方
     * @param insideRift       inside a Rift Gate / 身处裂隙门内
     * @param feared           Grand Witch Fear (Apprentice exemptions applied) / 大魔女恐惧（已计入预备魔女豁免）
     */
    public record Gates(boolean ragpicker, boolean roundActive, boolean playingAndAlive, boolean spectator,
                        boolean swallowed, boolean stunned, boolean roleSkillBlocked, boolean dueling,
                        boolean insideRift, boolean feared) {
        /** Every gate except Fear passes. / 除恐惧外的门槛全部通过。 */
        public boolean openIgnoringFear() {
            return ragpicker && roundActive && playingAndAlive && !spectator && !swallowed && !stunned
                    && !roleSkillBlocked && !dueling && !insideRift;
        }
    }

    private JunkerSearchRules() {
    }

    /**
     * Request gate, checked in order: the state gates except Fear, not already searching, cooldown ready, aiming at the
     * body within range with a clear block line; then Fear (so its message only appears when the search could
     * otherwise start, and a feared player learns nothing about the corpse); then blocking loot, a free hotbar slot,
     * already searched, and an empty corpse.
     * 请求门槛，按顺序检查：除恐惧外的状态门槛、未在搜刮中、冷却就绪、在范围内瞄准尸体且方块视线无遮挡；然后是恐惧
     * （提示只在本可开始搜刮时出现，被恐惧的玩家也得不到尸体的任何信息）；再依次是挡搜刮的战利品、快捷栏空位、
     * 已被搜刮、空尸体。
     */
    public static StartVerdict startVerdict(Gates gates, boolean searching, boolean cooldownReady, boolean aimedAtBody,
                                            boolean blockingLoot, boolean hotbarFree, boolean corpseSearched,
                                            boolean corpseHasLoot) {
        if (!gates.openIgnoringFear() || searching || !cooldownReady || !aimedAtBody) {
            return StartVerdict.REFUSE;
        }
        if (gates.feared()) {
            return StartVerdict.FEARED;
        }
        if (blockingLoot) {
            return StartVerdict.BLOCKED;
        }
        if (!hotbarFree) {
            return StartVerdict.HOTBAR_FULL;
        }
        if (corpseSearched) {
            return StartVerdict.SEARCHED;
        }
        return corpseHasLoot ? StartVerdict.START : StartVerdict.EMPTY;
    }

    /**
     * Per-tick gate of a running search: every state gate, Fear included, and the aim/range/line-of-sight check. Any
     * failure cancels the search; the cooldown is not re-read, so a forced cooldown raised mid-search never interrupts it.
     * 进行中搜刮的每刻门槛：全部状态门槛（含恐惧）以及瞄准/范围/视线检查。任何一项失败都会取消搜刮；冷却不再重读，
     * 因此搜刮中途被强制抬高的冷却不会打断它。
     */
    public static boolean keepsSearching(Gates gates, boolean aimedAtBody) {
        return gates.openIgnoringFear() && !gates.feared() && aimedAtBody;
    }

    /**
     * Completion, checked in the request's order: blocking loot (dropping it during the search and picking it back up
     * must not yield a second item), a free hotbar slot, already searched by someone else, then whether the dead owner
     * still has anything.
     * 完成时按与请求相同的顺序检查：挡搜刮的战利品（搜刮中丢下再捡回不得换来第二件）、快捷栏空位、是否已被别人搜刮，
     * 再看死者身上是否还有东西。
     */
    public static CompletionVerdict completionVerdict(boolean blockingLoot, boolean hotbarFree, boolean corpseSearched,
                                                      boolean corpseHasLoot) {
        if (blockingLoot) {
            return CompletionVerdict.BLOCKED;
        }
        if (!hotbarFree) {
            return CompletionVerdict.HOTBAR_FULL;
        }
        if (corpseSearched) {
            return CompletionVerdict.SEARCHED;
        }
        return corpseHasLoot ? CompletionVerdict.TRANSFER : CompletionVerdict.EMPTY;
    }

    /**
     * Whether a corpse's owner can be looted at all. The owner must be in Wathe's dead set and must not have become a
     * Wraith this round (Wraiths stay in the dead set but play on, their inventory cleared and re-kitted). An online
     * owner must also be a spectator and not an active Wraith; an offline owner is judged by the first two alone. Any
     * other owner's corpse counts as empty (accepted instant answer, owner decision 7).
     * 尸体的主人能否被搜刮。主人必须在 Wathe 的死亡名单中，且本回合没有成为冤魂（冤魂留在死亡名单中但继续游戏，背包会被
     * 清空并重新发放）。在线的主人还必须处于旁观模式且不是活跃冤魂；离线主人只按前两条判断。其他主人的尸体一律视为空
     * （接受的即时回答，所有者决定 7）。
     */
    public static boolean ownerYieldsLoot(boolean dead, boolean convertedToWraith, boolean online, boolean spectator,
                                          boolean activeWraith) {
        return dead && !convertedToWraith && (!online || (spectator && !activeWraith));
    }

    /**
     * Aim and line-of-sight target. The widened {@code aimBox} is aim tolerance only: the look ray from {@code eye} to
     * {@code end} must reach it (or start inside it). The returned point is on the REAL {@code bodyBox}: where the look
     * ray hits it, else the real-box point nearest the widened hit. The caller casts blocks from the eye to that point
     * and passes only on a miss, so a door or pane thinner than the margin still hides the body.
     * 瞄准与视线目标。放宽的 {@code aimBox} 只用于瞄准容差：从 {@code eye} 到 {@code end} 的视线射线必须命中它（或从其内部
     * 出发）。返回点位于真实的 {@code bodyBox} 上：视线命中真实碰撞箱处，否则取真实碰撞箱上离放宽命中点最近的点。调用方从
     * 眼睛到该点投射方块射线，只有未命中才通过，因此比余量更薄的门或玻璃板仍会挡住尸体。
     */
    public static Optional<Vec3d> lineOfSightTarget(Box bodyBox, Box aimBox, Vec3d eye, Vec3d end) {
        Vec3d aimHit;
        if (aimBox.contains(eye)) {
            aimHit = eye;
        } else {
            Optional<Vec3d> hit = aimBox.raycast(eye, end);
            if (hit.isEmpty()) {
                return Optional.empty();
            }
            aimHit = hit.get();
        }
        if (bodyBox.contains(eye)) {
            return Optional.of(eye);
        }
        Optional<Vec3d> exact = bodyBox.raycast(eye, end);
        return Optional.of(exact.orElseGet(() -> clampInto(aimHit, bodyBox)));
    }

    /** The point of {@code box} nearest {@code point}. / {@code box} 上离 {@code point} 最近的点。 */
    static Vec3d clampInto(Vec3d point, Box box) {
        return new Vec3d(
                Math.max(box.minX, Math.min(box.maxX, point.x)),
                Math.max(box.minY, Math.min(box.maxY, point.y)),
                Math.max(box.minZ, Math.min(box.maxZ, point.z)));
    }
}
