package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules;
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
     * Client approximation of the server's room check: a free main slot, and the round types that still fit on an
     * existing stack. / 服务端空位检查的客户端近似：是否有空的主背包栏位，以及仍能叠入现有堆的弹种。
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
        /** The bolt is cycling (at most one bolt length left). / 正在拉栓（剩余不超过一次拉栓时长）。 */
        BOLTING,
        /** A longer lock (round start, Saint Karma, Fiend aura). / 更长的锁定（开局、圣徒业报、魔人光环）。 */
        LOCKED,
        /** Nothing chambered. / 弹膛为空。 */
        EMPTY
    }

    // ---- opening / 打开 ----

    /**
     * The inventory right-click seam opens the screen only for a plain right press (PICKUP, button 1) with an empty
     * cursor on a rifle in the player's own inventory slot, for a non-spectator connected to a SparkWitch server. A
     * press with rounds on the cursor is never intercepted, so cursor loading still reaches the server as a click.
     * 背包右键接缝仅在以下情况打开界面：空光标下普通右键（PICKUP、按键 1），点在玩家自己背包栏位里的步枪上，玩家不是旁观者且
     * 连接的是 SparkWitch 服务端。光标拿着子弹的按下从不拦截，因此光标装填仍以普通点击到达服务端。
     */
    public static boolean opensOnClick(int button, boolean pickup, boolean cursorEmpty, boolean ownInventorySlot,
                                       int inventoryIndex, boolean rifle, boolean spectator, boolean serverReady) {
        return button == 1 && pickup && cursorEmpty && ownInventorySlot
                && UsecAttachmentRules.isActionSlot(inventoryIndex) && rifle && !spectator && serverReady;
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
     * disabled while the chamber is occupied or the bolt is cycling. Suppressor rows: 装上.
     * 零件列表及各零件的按钮。弹匣行（2 × 2 格）：装入、退弹、+FMJ、+AP。子弹行：上膛，弹膛有弹或正在拉栓时不可用。消音器行：装上。
     */
    public static List<Row> rows(UsecRifleState rifle, int rifleSlot, List<Part> parts, Room room, boolean bolting) {
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
                        part.slot(), !bolting && UsecAttachmentRules.chamberRound(rifle, part.ammo()) != null));
                case SUPPRESSOR -> buttons.add(new Button(Label.ATTACH, UsecAttachmentAction.ATTACH_SUPPRESSOR,
                        rifleSlot, part.slot(), UsecAttachmentRules.attachSuppressor(rifle) != null));
            }
            rows.add(new Row(part, List.copyOf(buttons)));
        }
        return List.copyOf(rows);
    }

    // ---- status / 状态 ----

    /**
     * Footer status from the synced rifle cooldown ({@code UsecCooldowns.remainingTicks}): longer than one bolt is a
     * lock, otherwise any remainder is the bolt cycling. / 依据已同步的步枪冷却得出的页脚状态：超过一次拉栓时长视为锁定，
     * 否则剩余时间即为拉栓。
     */
    public static BoltStatus boltStatus(UsecRifleState rifle, int remainingTicks) {
        if (remainingTicks > UsecRules.BOLT_TICKS) {
            return BoltStatus.LOCKED;
        }
        if (remainingTicks > 0) {
            return BoltStatus.BOLTING;
        }
        return rifle.chamber() != null ? BoltStatus.READY : BoltStatus.EMPTY;
    }

    /**
     * Gauge fill for a running cooldown: {@code total} is the longest remainder seen since it started.
     * 冷却进度条：{@code total} 为本次冷却开始以来见到的最长剩余时间。
     */
    public static float progress(int remainingTicks, int totalTicks) {
        if (remainingTicks <= 0 || totalTicks <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - (float) remainingTicks / totalTicks));
    }

    /**
     * Gauge scale for the running cooldown. A remainder that grew since the last tick is a fresh cooldown seen from its
     * start; the first sighting (screen opened mid-cooldown, {@code previousRemaining < 0}) assumes a whole bolt, or the
     * round-start lock when longer. / 冷却进度条的满刻度。剩余时间比上一刻长即为从头看到的新冷却；首次看到（冷却中途打开界面，
     * {@code previousRemaining < 0}）时按一次完整拉栓估计，更长时按开局锁定估计。
     */
    public static int gaugeTotal(int previousTotal, int previousRemaining, int remaining) {
        if (remaining <= 0) {
            return 0;
        }
        if (previousRemaining < 0) {
            if (remaining <= UsecRules.BOLT_TICKS) {
                return UsecRules.BOLT_TICKS;
            }
            return Math.max(remaining, UsecRules.ROUND_START_COOLDOWN_TICKS);
        }
        if (remaining > previousRemaining) {
            return remaining;
        }
        return Math.max(previousTotal, remaining);
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
