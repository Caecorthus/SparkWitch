package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineContents;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentAction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client only, pure: what the attachment screen offers. It turns the rifle state and the inventory parts into the three
 * detail boxes (① chamber, ② magazine, ③ muzzle) and the parts list, with one button for every
 * {@link UsecAttachmentAction}, and predicts each button through the same {@link UsecAttachmentRules} the server runs
 * (room is approximated from the client inventory). Prediction only enables or dims a button; the server decides. No
 * client types, so it is unit-tested directly. Slots are player inventory indices (0-35, offhand 40).
 * 仅客户端，纯逻辑：配件界面提供的内容。把步枪状态与背包零件转换为三个详图框（①弹膛、②弹匣、③枪口）与零件列表，每个
 * {@link UsecAttachmentAction} 都有对应按钮，并通过服务端同样运行的 {@link UsecAttachmentRules} 预测每个按钮（空位按客户端
 * 背包近似）。预测只决定按钮可点或变暗，结果由服务端决定。不含客户端类型，可直接单测。栏位为玩家背包下标（0-35，副手 40）。
 */
public final class UsecAttachmentModel {
    /** No second slot (single-stack actions, or a missing round type). / 无第二栏位（单物品动作或缺少该弹种）。 */
    public static final int NO_SLOT = -1;

    private UsecAttachmentModel() {
    }

    /** The three rifle slots in callout order: ① 弹膛, ② 弹匣, ③ 枪口. / 按引出编号排列的三个步枪槽位。 */
    public enum Card {
        CHAMBER("chamber"),
        MAGAZINE("magazine"),
        MUZZLE("muzzle");

        private final String id;

        Card(String id) {
            this.id = id;
        }

        public String labelKey() {
            return "gui.sparkwitch.usec_attachment.slot." + id;
        }

        /** Stencil-free drawing code (CHMBR / MAG / MUZZLE). 图纸代号。 */
        public String codeKey() {
            return "gui.sparkwitch.usec_attachment.slot." + id + ".code";
        }
    }

    public enum PartKind {
        MAGAZINE,
        ROUNDS,
        SUPPRESSOR
    }

    /**
     * One usable inventory part. Rounds are one part per type: {@code slot} is the first slot holding that type and
     * {@code count} the total. / 一个可用背包零件。子弹按弹种合并为一个零件：slot 为第一个存放该弹种的栏位，count 为总数。
     */
    public record Part(PartKind kind, int slot, @Nullable UsecMagazineContents contents, @Nullable UsecAmmoType ammo,
                       int count) {
        public static Part magazine(int slot, UsecMagazineContents contents) {
            return new Part(PartKind.MAGAZINE, slot, contents == null ? UsecMagazineContents.EMPTY : contents, null, 1);
        }

        public static Part rounds(int slot, UsecAmmoType ammo, int count) {
            return new Part(PartKind.ROUNDS, slot, null, ammo, count);
        }

        public static Part suppressor(int slot) {
            return new Part(PartKind.SUPPRESSOR, slot, null, null, 1);
        }
    }

    /**
     * Client approximation of the server's shown-slot room check ({@code UsecAttachmentRules.releaseSlot}, A7): a free
     * shown slot (hotbar, the second row while shown) or an empty offhand, and the round types that still fit on a
     * shown stack. / 服务端显示栏位空位检查的客户端近似（{@code UsecAttachmentRules.releaseSlot}，A7）：是否有空的显示栏位
     * （快捷栏、显示时的第二行）或空副手，以及仍能叠入显示中物品堆的弹种。
     */
    public record Room(boolean freeSlot, Set<UsecAmmoType> roundStacksWithRoom) {
        public static final Room NONE = new Room(false, Set.of());
        public static final Room ANY = new Room(true, Set.of());

        public Room {
            roundStacksWithRoom = roundStacksWithRoom == null ? Set.of() : Set.copyOf(roundStacksWithRoom);
        }

        public boolean fitsItem() {
            return freeSlot;
        }

        public boolean fitsRound(@Nullable UsecAmmoType type) {
            return type != null && (freeSlot || roundStacksWithRoom.contains(type));
        }
    }

    /** Button labels (mockup U2); each is one lang key. / 按钮标签（U2 样稿），各对应一个语言键。 */
    public enum Label {
        /** 装入: insert a loose magazine. */
        INSERT("insert"),
        /** 装上: fit the suppressor. */
        ATTACH("attach"),
        /** 卸下: remove the magazine or the suppressor. */
        REMOVE("remove"),
        /** 退弹: pop the top round of a magazine. */
        UNLOAD("unload"),
        LOAD_FMJ("load_fmj"),
        LOAD_AP("load_ap"),
        /** 上膛: single-load a round into an empty chamber. */
        CHAMBER("chamber"),
        /** 退膛: clear the chamber. */
        UNLOAD_CHAMBER("unload_chamber");

        private final String id;

        Label(String id) {
            this.id = id;
        }

        public String key() {
            return "gui.sparkwitch.usec_attachment.button." + id;
        }

        static Label load(UsecAmmoType type) {
            return type == UsecAmmoType.AP ? LOAD_AP : LOAD_FMJ;
        }
    }

    /** One clickable action and its predicted availability. / 一个可点击的动作及其预测可用性。 */
    public record Button(Label label, UsecAttachmentAction action, int primarySlot, int itemSlot, boolean enabled) {
    }

    public record Row(Part part, List<Button> buttons) {
    }

    /** Footer bolt status. / 页脚拉栓状态。 */
    public enum BoltStatus {
        /** A round is chambered and the rifle is not cooling down. / 已上膛且没有冷却。 */
        READY,
        /** The bolt is cycling (a proven bolt, at most one bolt length). / 正在拉栓（已证实的拉栓，不超过一次拉栓时长）。 */
        BOLTING,
        /**
         * Any other lock: round start, Saint Karma, Fiend aura, or a short forced lock (Shriek Gun, AC shell).
         * 其他锁定：开局、圣徒业报、魔人光环，或短暂的强制锁定（尖啸枪、AC 炮弹）。
         */
        LOCKED,
        /** Nothing chambered. / 弹膛为空。 */
        EMPTY
    }

    // ---- opening / 打开 ----

    /**
     * The inventory right-click seam opens the screen only for a plain right press (PICKUP, button 1) with an empty
     * cursor on a rifle in the player's own inventory slot, for a non-spectator connected to a SparkWitch server. A
     * press with rounds on the cursor is never intercepted, so cursor loading still runs as a normal click (on the
     * server in survival, on the client in the creative inventory).
     * 背包右键接缝仅在以下情况打开界面：空光标下普通右键（PICKUP、按键 1），点在玩家自己背包栏位里的步枪上，玩家不是旁观者且
     * 连接的是 SparkWitch 服务端。光标拿着子弹的按下从不拦截，因此光标装填仍按普通点击执行（生存模式在服务端，创造模式物品栏
     * 在客户端）。
     */
    public static boolean opensOnClick(int button, boolean pickup, boolean cursorEmpty, boolean ownInventorySlot,
                                       int inventoryIndex, boolean rifle, boolean spectator, boolean serverReady) {
        return button == 1 && pickup && cursorEmpty && ownInventorySlot
                && UsecAttachmentRules.isActionSlot(inventoryIndex) && rifle && !spectator && serverReady;
    }

    /**
     * A clicked slot as the opener sees it: whether its inventory is the player's own and its {@code getIndex()}.
     * 入口所见的被点击栏位：其物品栏是否为玩家自己的背包，以及它的 {@code getIndex()}。
     */
    public record SlotRef(boolean playerInventory, int index) {
    }

    /**
     * Resolves a clicked slot to the player inventory index (0-35, offhand 40) that the screen and
     * {@code sparkwitch:usec_attachment} use, or {@link #NO_SLOT}.
     * <ul>
     *   <li>{@code creativeWrapped} non-null: a creative inventory-tab slot, which wraps a player screen handler slot.
     *   The wrapper's own index is its handler position (36 for hotbar 0, 45 for the offhand), so only the wrapped slot
     *   is read.</li>
     *   <li>Otherwise only the player's own screen handler (the vanilla survival inventory, Wathe's in-round inventory)
     *   counts, read from the clicked slot itself.</li>
     *   <li>Everything else never opens: containers, the creative item list, and the hotbar row of the other creative
     *   tabs. That row is a plain slot of the creative handler, and while this screen covers the creative screen
     *   vanilla routes the server's non-hotbar slot updates into that handler's item grid, so the shown inventory
     *   would go stale.</li>
     * </ul>
     * 把被点击的栏位解析为界面与 {@code sparkwitch:usec_attachment} 使用的玩家背包下标（0-35，副手 40），否则返回
     * {@link #NO_SLOT}。{@code creativeWrapped} 非空：创造模式背包标签页的栏位，它包装一个玩家界面处理器栏位；包装栏位自身的下标
     * 是其在处理器中的位置（快捷栏 0 为 36，副手为 45），因此只读取被包装的栏位。否则只认玩家自己的界面处理器（原版生存背包、
     * Wathe 局内背包），直接读取被点击的栏位。其余情况一律不打开：容器、创造模式物品列表，以及其他创造标签页的快捷栏行。该行是
     * 创造处理器的普通栏位；本界面覆盖创造界面期间，原版会把服务端发来的非快捷栏栏位更新写进该处理器的物品网格，显示的背包会过期。
     */
    public static int ownInventoryIndex(boolean playerHandler, SlotRef clicked, @Nullable SlotRef creativeWrapped) {
        SlotRef own = creativeWrapped != null ? creativeWrapped : playerHandler ? clicked : null;
        return own != null && own.playerInventory() && UsecAttachmentRules.isActionSlot(own.index())
                ? own.index() : NO_SLOT;
    }

    // ---- parts / 零件 ----

    /**
     * Normalises raw per-slot parts: drops the rifle's own slot and invalid slots, merges rounds per type (first slot
     * in inventory order, total count), and orders magazines, then rounds (FMJ, AP), then suppressors.
     * 规范化逐栏位零件：去掉步枪自身栏位与无效栏位，按弹种合并子弹（背包顺序中的第一个栏位、总数），并按弹匣、子弹（FMJ、AP）、
     * 消音器排序。
     */
    public static List<Part> collect(List<Part> raw, int rifleSlot) {
        List<Part> magazines = new ArrayList<>();
        List<Part> suppressors = new ArrayList<>();
        Map<UsecAmmoType, Part> rounds = new EnumMap<>(UsecAmmoType.class);
        for (Part part : raw) {
            if (part == null || part.slot() == rifleSlot || !UsecAttachmentRules.isActionSlot(part.slot())) {
                continue;
            }
            switch (part.kind()) {
                case MAGAZINE -> magazines.add(part);
                case SUPPRESSOR -> suppressors.add(part);
                case ROUNDS -> {
                    if (part.ammo() == null || part.count() <= 0) {
                        continue;
                    }
                    Part merged = rounds.get(part.ammo());
                    if (merged == null) {
                        rounds.put(part.ammo(), part);
                    } else {
                        int first = order(part.slot()) < order(merged.slot()) ? part.slot() : merged.slot();
                        rounds.put(part.ammo(), Part.rounds(first, part.ammo(), merged.count() + part.count()));
                    }
                }
            }
        }
        Comparator<Part> bySlot = Comparator.comparingInt(part -> order(part.slot()));
        magazines.sort(bySlot);
        suppressors.sort(bySlot);
        List<Part> result = new ArrayList<>(magazines);
        for (UsecAmmoType type : UsecAmmoType.values()) {
            if (rounds.containsKey(type)) {
                result.add(rounds.get(type));
            }
        }
        result.addAll(suppressors);
        return List.copyOf(result);
    }

    /** Inventory order: main 0-35, then the offhand. / 背包顺序：主背包 0-35，然后副手。 */
    static int order(int slot) {
        return slot == UsecAttachmentRules.OFFHAND_SLOT ? UsecAttachmentRules.MAIN_SLOTS : slot;
    }

    /** The slot of the loose rounds of a type, or {@link #NO_SLOT}. / 某弹种散装子弹的栏位，或 NO_SLOT。 */
    static int roundSlot(List<Part> parts, UsecAmmoType type) {
        for (Part part : parts) {
            if (part.kind() == PartKind.ROUNDS && part.ammo() == type && part.count() > 0) {
                return part.slot();
            }
        }
        return NO_SLOT;
    }

    // ---- buttons / 按钮 ----

    /**
     * A detail box's buttons in grid order. ① chamber: 退膛 while a round is chambered. ② magazine (always four, a
     * 2 x 2 grid): +FMJ and +AP need room in the inserted magazine and a loose round of that type (+ pushes on top, so
     * that round fires first), 退弹 needs a round, 卸下 an inserted magazine. ③ muzzle: 卸下 while fitted.
     * 详图框按钮（按格顺序）。①弹膛：膛内有弹时显示退膛。②弹匣（固定四个，2 × 2 格）：+FMJ 与 +AP 需要已装弹匣有空位且有该弹种
     * 散装子弹（+ 压在顶部，这发最先发射）；退弹需要有子弹；卸下需要已装弹匣。③枪口：装有消音器时显示卸下。
     */
    public static List<Button> cardButtons(Card card, UsecRifleState rifle, int rifleSlot, List<Part> parts,
                                           Room room) {
        return switch (card) {
            case CHAMBER -> rifle.chamber() == null ? List.of()
                    : List.of(new Button(Label.UNLOAD_CHAMBER, UsecAttachmentAction.UNLOAD_CHAMBER, rifleSlot, NO_SLOT,
                            room.fitsRound(rifle.chamber())));
            case MAGAZINE -> {
                List<Button> buttons = new ArrayList<>(4);
                for (UsecAmmoType type : UsecAmmoType.values()) {
                    int slot = roundSlot(parts, type);
                    buttons.add(new Button(Label.load(type), UsecAttachmentAction.LOAD_ROUND_ATTACHED, rifleSlot, slot,
                            slot != NO_SLOT && UsecAttachmentRules.loadAttached(rifle, type) != null));
                }
                UsecMagazineContents magazine = rifle.magazine();
                buttons.add(new Button(Label.UNLOAD, UsecAttachmentAction.UNLOAD_ROUND_ATTACHED, rifleSlot, NO_SLOT,
                        UsecAttachmentRules.unloadAttached(rifle) != null && room.fitsRound(magazine.top())));
                buttons.add(new Button(Label.REMOVE, UsecAttachmentAction.REMOVE_MAGAZINE, rifleSlot, NO_SLOT,
                        UsecAttachmentRules.removeMagazine(rifle) != null && room.fitsItem()));
                yield List.copyOf(buttons);
            }
            case MUZZLE -> rifle.suppressor()
                    ? List.of(new Button(Label.REMOVE, UsecAttachmentAction.DETACH_SUPPRESSOR, rifleSlot, NO_SLOT,
                            room.fitsItem()))
                    : List.of();
        };
    }

    /**
     * The parts list with each part's buttons. Magazine rows (2 x 2 grid): 装入, 退弹, +FMJ, +AP. Round rows: 上膛,
     * disabled only when {@link UsecAttachmentRules#chamberRound} refuses (the server allows chambering while the bolt
     * cycles, so the screen never locks it then). Suppressor rows: 装上.
     * 零件列表及各零件的按钮。弹匣行（2 × 2 格）：装入、退弹、+FMJ、+AP。子弹行：上膛，仅在
     * {@link UsecAttachmentRules#chamberRound} 拒绝时不可用（服务端允许在拉栓期间上膛，因此界面此时不锁定）。消音器行：装上。
     */
    public static List<Row> rows(UsecRifleState rifle, int rifleSlot, List<Part> parts, Room room) {
        List<Row> rows = new ArrayList<>(parts.size());
        for (Part part : parts) {
            List<Button> buttons = new ArrayList<>(4);
            switch (part.kind()) {
                case MAGAZINE -> {
                    UsecMagazineContents contents = part.contents() == null
                            ? UsecMagazineContents.EMPTY : part.contents();
                    buttons.add(new Button(Label.INSERT, UsecAttachmentAction.INSERT_MAGAZINE, rifleSlot, part.slot(),
                            true));
                    buttons.add(new Button(Label.UNLOAD, UsecAttachmentAction.UNLOAD_ROUND_LOOSE, part.slot(), NO_SLOT,
                            UsecAttachmentRules.unloadLoose(contents) != null && room.fitsRound(contents.top())));
                    for (UsecAmmoType type : UsecAmmoType.values()) {
                        int slot = roundSlot(parts, type);
                        buttons.add(new Button(Label.load(type), UsecAttachmentAction.LOAD_ROUND_LOOSE, part.slot(),
                                slot, slot != NO_SLOT && UsecAttachmentRules.loadLoose(contents, type) != null));
                    }
                }
                case ROUNDS -> buttons.add(new Button(Label.CHAMBER, UsecAttachmentAction.CHAMBER_ROUND, rifleSlot,
                        part.slot(), UsecAttachmentRules.chamberRound(rifle, part.ammo()) != null));
                case SUPPRESSOR -> buttons.add(new Button(Label.ATTACH, UsecAttachmentAction.ATTACH_SUPPRESSOR,
                        rifleSlot, part.slot(), UsecAttachmentRules.attachSuppressor(rifle) != null));
            }
            rows.add(new Row(part, List.copyOf(buttons)));
        }
        return List.copyOf(rows);
    }

    // ---- status / 状态 ----

    /** No action of this screen is waiting for the inventory's answer. / 本界面没有等待背包回应的动作。 */
    public static final int NO_PENDING_ACTION = -1;

    /**
     * Footer status from the shared classifier ({@code UsecBoltWatchClient.status}): a proven bolt is the bolt cycling,
     * any other cooldown a lock for its whole length, a short forced lock (Shriek Gun, AC shell) included.
     * {@code ownActionAge} is the ticks since this screen sent an action the inventory has not answered yet, else
     * {@link #NO_PENDING_ACTION}. While it waits, a bolt-length entry that started after the press already reads as the
     * bolt: its proof (the rifle state change) comes with that answer, a tick or two after the cooldown, so the pill
     * does not flash 锁定 first (unless the answer takes longer than the screen's 10-tick wait, or an unrelated
     * inventory change ends the wait early).
     * 依据共用分类器（{@code UsecBoltWatchClient.status}）得出的页脚状态：已证实的拉栓即为拉栓，其余冷却在整个时长内都是锁定，
     * 包括短暂的强制锁定（尖啸枪、AC 炮弹）。{@code ownActionAge} 为本界面发出、背包尚未回应的动作至今的刻数，否则为
     * {@link #NO_PENDING_ACTION}。等待期间，按下之后才开始的拉栓长度条目直接显示为拉栓：它的证据（步枪状态变化）随该回应到达，
     * 比冷却晚一两刻，因此状态标签不会先闪一下“锁定”（除非回应超过界面 10 刻的等待，或无关的背包变化提前结束等待）。
     */
    public static BoltStatus boltStatus(UsecRifleState rifle, UsecCooldowns.Status cooldown, int ownActionAge) {
        if (cooldown.bolt() || awaitsOwnBolt(cooldown, ownActionAge)) {
            return BoltStatus.BOLTING;
        }
        if (cooldown.otherLock()) {
            return BoltStatus.LOCKED;
        }
        return rifle.chamber() != null ? BoltStatus.READY : BoltStatus.EMPTY;
    }

    /**
     * A bolt-length entry that started no earlier than this screen's unanswered action (elapsed ticks within its age).
     * 不早于本界面未获回应动作开始的拉栓长度条目（已经过的刻数不超过该动作的时长）。
     */
    static boolean awaitsOwnBolt(UsecCooldowns.Status cooldown, int ownActionAge) {
        return ownActionAge >= 0 && cooldown.coolingDown() && cooldown.total() <= UsecRules.BOLT_TICKS
                && cooldown.total() - cooldown.remaining() <= ownActionAge;
    }

    /**
     * Gauge fill for a running cooldown. / 冷却进度条的填充比例。
     */
    public static float progress(int remainingTicks, int totalTicks) {
        if (remainingTicks <= 0 || totalTicks <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - (float) remainingTicks / totalTicks));
    }

    /**
     * Gauge scale: the cooldown's exact total from its entry (start to end), 0 when none; no longer estimated from
     * the remainders seen since the screen opened. / 冷却进度条的满刻度：取自条目（开始到结束）的精确总长，无冷却时为 0；
     * 不再根据界面打开以来见到的剩余时间估算。
     */
    public static int gaugeTotal(UsecCooldowns.Status cooldown) {
        return cooldown.coolingDown() ? cooldown.total() : 0;
    }

    /** Whole seconds, rounded up. / 向上取整的秒数。 */
    public static int secondsCeil(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    /** Tenths of a second, rounded up ("1.2 s"). / 十分之一秒，向上取整（「1.2 s」）。 */
    public static int tenthsCeil(int ticks) {
        return Math.max(0, (ticks + 1) / 2);
    }
}
