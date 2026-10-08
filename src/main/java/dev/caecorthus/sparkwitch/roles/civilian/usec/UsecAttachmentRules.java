package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentAction;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Stable contract: the pure attachment state machine shared by the {@code sparkwitch:usec_attachment} receiver, cursor
 * loading ({@code Item#onClicked}) and the client screen's button prediction. Every transition returns {@code null}
 * when refused and never touches an item, so callers check room and item types first and then apply the result.
 * <p>
 * Bolt rule (owner): whenever a transition leaves the chamber empty while the inserted magazine still has rounds, the
 * top round is popped into the chamber and the result is {@link Outcome#bolted()}, which the server answers with
 * {@code UsecCooldowns.bolt} and the bolt sound. A single round chambered by hand also bolts. Magazines are LIFO and may
 * mix types (Q9).
 * 稳定契约：纯配件状态机，由 {@code sparkwitch:usec_attachment} 接收器、光标装填（{@code Item#onClicked}）与客户端界面的
 * 按钮预测共用。每个转换在被拒绝时返回 {@code null}，且从不触碰物品；调用方先检查空位与物品类型，再应用结果。
 * <p>
 * 拉栓规则（所有者）：任何转换让弹膛变空、而已装弹匣仍有子弹时，顶部一发被推入弹膛，结果标记为
 * {@link Outcome#bolted()}，服务端随之调用 {@code UsecCooldowns.bolt} 并播放拉栓声。手动单发压膛同样会拉栓。
 * 弹匣后进先出且可混装（Q9）。
 */
public final class UsecAttachmentRules {
    /** Main inventory size (0-35). / 主背包大小（0-35）。 */
    public static final int MAIN_SLOTS = 36;
    /** Vanilla offhand inventory index. / 原版副手的背包下标。 */
    public static final int OFFHAND_SLOT = 40;
    /** {@link #releaseSlot}: nowhere shown to put the item. / {@link #releaseSlot}：没有可显示的位置放置该物品。 */
    public static final int NO_ROOM = -1;

    private UsecAttachmentRules() {
    }

    // ---- results / 结果 ----

    /** What leaves the rifle for the inventory. / 从步枪取出、需放入背包的物品。 */
    public sealed interface Release permits None, Magazine, Round, Suppressor {
    }

    /** Nothing leaves the rifle. / 没有物品离开步枪。 */
    public record None() implements Release {
    }

    /** A detached magazine with these contents. / 卸下的弹匣及其内容。 */
    public record Magazine(UsecMagazineContents contents) implements Release {
    }

    /** One loose round. / 一发散装子弹。 */
    public record Round(UsecAmmoType type) implements Release {
    }

    /** The suppressor item. / 消音器物品。 */
    public record Suppressor() implements Release {
    }

    public static final Release NONE = new None();

    /**
     * An accepted rifle transition: the new rifle state (bolt rule already applied), what leaves it, and whether the
     * bolt cycled. / 已接受的步枪转换：新的步枪状态（已应用拉栓规则）、离开步枪的物品，以及是否拉栓。
     */
    public record Outcome(UsecRifleState rifle, Release release, boolean bolted) {
    }

    /** An accepted loose-magazine transition and the round it released, if any. / 已接受的散装弹匣转换及其退出的子弹。 */
    public record LooseOutcome(UsecMagazineContents contents, @Nullable UsecAmmoType released) {
    }

    // ---- gate / 准入 ----

    /** Outcome of the player gate, in check order; only {@link #ALLOWED} acts. / 玩家准入判定（按检查顺序）；仅 ALLOWED 生效。 */
    public enum Gate {
        /** {@link OffMatchUse.Mode#REFUSED}: a dead participant of an ACTIVE match. / ACTIVE 对局中已死亡的参与者。 */
        DEAD_PARTICIPANT,
        NOT_ALIVE,
        SPECTATOR,
        STUNNED,
        SESSION_LOCKED,
        ALLOWED
    }

    /**
     * Pure player gate shared by the packet and cursor loading. Use never checks the role (the {@link OffMatchUse}
     * spirit): a live participant of a running match and any off-match holder may act; Fear does not block (owner).
     * 数据包与光标装填共用的纯玩家准入。从不检查职业（{@link OffMatchUse} 精神）：进行中对局的存活参与者与任何对局外持有者
     * 都可操作；恐惧不阻止（所有者）。
     */
    public static Gate gate(@Nullable OffMatchUse.Mode mode, boolean alive, boolean spectator, boolean stunned,
                            boolean sessionLocked) {
        if (mode == null || mode == OffMatchUse.Mode.REFUSED) {
            return Gate.DEAD_PARTICIPANT;
        }
        if (!alive) {
            return Gate.NOT_ALIVE;
        }
        if (spectator) {
            return Gate.SPECTATOR;
        }
        if (stunned) {
            return Gate.STUNNED;
        }
        if (sessionLocked) {
            return Gate.SESSION_LOCKED;
        }
        return Gate.ALLOWED;
    }

    // ---- slots / 栏位 ----

    /** Player inventory indices an action may name: 0-35 and the offhand 40. / 动作可引用的背包下标：0-35 与副手 40。 */
    public static boolean isActionSlot(int slot) {
        return slot >= 0 && slot < MAIN_SLOTS || slot == OFFHAND_SLOT;
    }

    /**
     * A7: where an item leaving the rifle (or a loose magazine) goes, shown slots only, in {@link UsecShopPurchase}'s
     * order: the first shown same-item stack that takes it whole (hotbar 0-8, then 27-35 only while SparkFactionAPI
     * shows the second row), else the first empty shown slot in that order, else the offhand when it is empty;
     * {@link #NO_ROOM} otherwise. Hidden main slots (9-26, and 27-35 while hidden) never receive a release, because
     * Wathe's in-round inventory does not show them. Pure; shared by the server and the screen's room prediction.
     * A7：离开步枪（或散装弹匣）的物品放到哪里，只限显示中的栏位，顺序与 {@link UsecShopPurchase} 相同：先找第一个能整份放下的
     * 显示中同种物品堆（快捷栏 0-8，然后仅在 SparkFactionAPI 显示第二行时为 27-35），否则按同一顺序找第一个空的显示栏位，
     * 否则在副手为空时放入副手；都不行则为 {@link #NO_ROOM}。隐藏的主背包栏位（9-26，以及第二行隐藏时的 27-35）从不接收释放的
     * 物品，因为 Wathe 局内背包不显示它们。纯函数，由服务端与界面的空位预测共用。
     *
     * @param topsUp the shown stack in that slot is the same item and can take the whole release / 该栏位的物品堆可整份放下
     * @param empty  the slot is empty / 该栏位为空
     */
    public static int releaseSlot(IntPredicate topsUp, IntPredicate empty, boolean secondRowShown) {
        List<Integer> shown = UsecShopPurchase.shownTopUpSlots(secondRowShown);
        for (int slot : shown) {
            if (topsUp.test(slot)) {
                return slot;
            }
        }
        for (int slot : shown) {
            if (empty.test(slot)) {
                return slot;
            }
        }
        return empty.test(OFFHAND_SLOT) ? OFFHAND_SLOT : NO_ROOM;
    }

    /** Actions whose {@code itemSlot} names a second stack. / {@code itemSlot} 指向第二个物品堆的动作。 */
    public static boolean usesItemSlot(UsecAttachmentAction action) {
        return switch (action) {
            case INSERT_MAGAZINE, ATTACH_SUPPRESSOR, CHAMBER_ROUND, LOAD_ROUND_ATTACHED, LOAD_ROUND_LOOSE -> true;
            case REMOVE_MAGAZINE, DETACH_SUPPRESSOR, UNLOAD_CHAMBER, UNLOAD_ROUND_ATTACHED, UNLOAD_ROUND_LOOSE -> false;
        };
    }

    /**
     * Slot shape check: the primary slot is an action slot, and a two-stack action names a distinct second action
     * slot. Single-stack actions ignore {@code itemSlot}.
     * 栏位形状检查：主栏位须为可操作栏位；双物品动作的第二栏位须为不同的可操作栏位。单物品动作忽略 {@code itemSlot}。
     */
    public static boolean slotsValid(UsecAttachmentAction action, int primarySlot, int itemSlot) {
        if (action == null || !isActionSlot(primarySlot)) {
            return false;
        }
        return !usesItemSlot(action) || isActionSlot(itemSlot) && itemSlot != primarySlot;
    }

    // ---- bolt rule / 拉栓规则 ----

    /** True when the chamber is empty and the inserted magazine has a round. / 弹膛为空且已装弹匣有子弹时为 true。 */
    public static boolean needsBolt(UsecRifleState rifle) {
        return rifle.chamber() == null && rifle.magazine() != null && !rifle.magazine().isEmpty();
    }

    /** Pops the top round into an empty chamber when {@link #needsBolt}. / 满足 needsBolt 时把顶部一发推入空弹膛。 */
    public static UsecRifleState bolt(UsecRifleState rifle) {
        if (!needsBolt(rifle)) {
            return rifle;
        }
        UsecMagazineContents magazine = rifle.magazine();
        return new UsecRifleState(magazine.top(), magazine.pop(), rifle.suppressor());
    }

    private static Outcome settle(UsecRifleState rifle, Release release, boolean forcedBolt) {
        boolean bolted = needsBolt(rifle);
        return new Outcome(bolt(rifle), release, forcedBolt || bolted);
    }

    // ---- rifle transitions / 步枪转换 ----

    /**
     * Swap: {@code incoming} becomes the inserted magazine; the previously inserted one, if any, is released into the
     * vacated slot. / 交换：{@code incoming} 成为已装弹匣；原先装着的弹匣（若有）被释放到空出的栏位。
     */
    public static Outcome insertMagazine(UsecRifleState rifle, UsecMagazineContents incoming) {
        UsecMagazineContents contents = incoming == null ? UsecMagazineContents.EMPTY : incoming;
        Release release = rifle.magazine() == null ? NONE : new Magazine(rifle.magazine());
        return settle(rifle.withMagazine(contents), release, false);
    }

    public static @Nullable Outcome removeMagazine(UsecRifleState rifle) {
        if (rifle.magazine() == null) {
            return null;
        }
        return settle(rifle.withMagazine(null), new Magazine(rifle.magazine()), false);
    }

    public static @Nullable Outcome attachSuppressor(UsecRifleState rifle) {
        return rifle.suppressor() ? null : settle(rifle.withSuppressor(true), NONE, false);
    }

    public static @Nullable Outcome detachSuppressor(UsecRifleState rifle) {
        return rifle.suppressor() ? settle(rifle.withSuppressor(false), new Suppressor(), false) : null;
    }

    /**
     * Single-round loading (Q4+): only into an empty chamber; the automatic bolt always runs afterwards.
     * 单发压膛（Q4+）：仅限空弹膛；之后总会自动拉栓。
     */
    public static @Nullable Outcome chamberRound(UsecRifleState rifle, UsecAmmoType type) {
        if (type == null || rifle.chamber() != null) {
            return null;
        }
        return settle(rifle.withChamber(type), NONE, true);
    }

    /** Clears the chamber; the bolt rule may chamber the next round. / 退出膛内子弹；拉栓规则可能推入下一发。 */
    public static @Nullable Outcome unloadChamber(UsecRifleState rifle) {
        if (rifle.chamber() == null) {
            return null;
        }
        return settle(rifle.withChamber(null), new Round(rifle.chamber()), false);
    }

    /** Pushes one round onto the inserted magazine. / 向已装弹匣顶部压入一发。 */
    public static @Nullable Outcome loadAttached(UsecRifleState rifle, UsecAmmoType type) {
        UsecMagazineContents magazine = rifle.magazine();
        if (type == null || magazine == null || magazine.isFull()) {
            return null;
        }
        return settle(rifle.withMagazine(magazine.push(type)), NONE, false);
    }

    /** Pops the top round of the inserted magazine. / 取出已装弹匣顶部一发。 */
    public static @Nullable Outcome unloadAttached(UsecRifleState rifle) {
        UsecMagazineContents magazine = rifle.magazine();
        if (magazine == null || magazine.isEmpty()) {
            return null;
        }
        return settle(rifle.withMagazine(magazine.pop()), new Round(magazine.top()), false);
    }

    /**
     * Cursor loading onto the rifle: into the inserted magazine, or, with no magazine, a single round into an empty
     * chamber. / 光标装填到步枪：装入已装弹匣；未装弹匣时，单发压入空弹膛。
     */
    public static @Nullable Outcome cursorLoadRifle(UsecRifleState rifle, UsecAmmoType type) {
        return rifle.hasMagazine() ? loadAttached(rifle, type) : chamberRound(rifle, type);
    }

    // ---- loose magazine transitions / 散装弹匣转换 ----

    public static @Nullable UsecMagazineContents loadLoose(UsecMagazineContents contents, UsecAmmoType type) {
        UsecMagazineContents current = contents == null ? UsecMagazineContents.EMPTY : contents;
        return type == null || current.isFull() ? null : current.push(type);
    }

    public static @Nullable LooseOutcome unloadLoose(UsecMagazineContents contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        return new LooseOutcome(contents.pop(), contents.top());
    }
}
