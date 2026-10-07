package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineContents;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation rules for the owner-only USEC ammo line (S2, WP6 mockup {@code s2_ammo_hud_states.png}):
 * {@code <chamber> » [<magazine in firing order>] · 消音}, a tan zoom tag left of it while scoped, and a 2-px progress
 * bar under the chamber token while the rifle cools down. The chamber token is the round label in its own colour,
 * {@code 空膛}, or {@code 拉栓中} while the bolt cycles; without a magazine the feed reads {@code · 无弹匣}. It is
 * role-owned, bottom-right, and never part of the witch skill inventory panel (AGENTS.md). Colours are opaque ARGB.
 * 仅拥有者可见的 USEC 弹药行的纯展示规则（S2，WP6 样稿 {@code s2_ammo_hud_states.png}）：
 * {@code <弹膛> » [<按发射顺序的弹匣>] · 消音}，开镜时左侧有沙色倍率标签，步枪冷却期间弹膛标记下方有 2 像素进度条。弹膛标记为
 * 自身颜色的弹种标签、{@code 空膛}，或拉栓时的 {@code 拉栓中}；没有弹匣时显示 {@code · 无弹匣}。属于职业自有的右下角展示，
 * 从不进入魔女技能背包面板（AGENTS.md）。颜色为不透明 ARGB。
 */
public final class UsecAmmoHudRules {
    public static final String ZOOM_KEY = "hud.sparkwitch.usec.zoom";
    public static final String CHAMBER_EMPTY_KEY = "hud.sparkwitch.usec.chamber_empty";
    public static final String CYCLING_KEY = "hud.sparkwitch.usec.cycling";
    public static final String MAGAZINE_KEY = "hud.sparkwitch.usec.magazine";
    public static final String NO_MAGAZINE_KEY = "hud.sparkwitch.usec.no_magazine";
    public static final String SUPPRESSED_KEY = "hud.sparkwitch.usec.suppressed";
    /** {@code " » "}: {@code ▸} would fall back to Unifont and render as a dot. / {@code ▸} 会退回 Unifont 显示为点。 */
    public static final String FEED_SEPARATOR_KEY = "hud.sparkwitch.usec.separator.feed";
    public static final String SEPARATOR_KEY = "hud.sparkwitch.usec.separator";
    /** The {@code 空} inside {@code [空]}. / {@code [空]} 中的 {@code 空}。 */
    public static final String EMPTY_KEY = "item.sparkwitch.usec.empty";
    public static final List<String> KEYS = List.of(ZOOM_KEY, CHAMBER_EMPTY_KEY, CYCLING_KEY, MAGAZINE_KEY,
            NO_MAGAZINE_KEY, SUPPRESSED_KEY, FEED_SEPARATOR_KEY, SEPARATOR_KEY, EMPTY_KEY);

    /** Brackets, dots and {@code »}. / 方括号、点与 {@code »}。 */
    public static final int TEXT_COLOR = 0xFFE0E0E0;
    public static final int MUTED_COLOR = 0xFFAAAAAA;
    public static final int TAN_COLOR = 0xFF000000 | UsecRules.COLOR;
    public static final int BAR_TRACK_COLOR = 0x96000000;
    public static final int BAR_FILL_COLOR = TAN_COLOR;
    /** Lower fill row: tan 40% darker. / 下行填充：沙色加深 40%。 */
    public static final int BAR_FILL_SHADE_COLOR = 0xFF78653F;
    public static final int BAR_HEIGHT = 2;
    public static final int ZOOM_TAG_GAP = 6;
    public static final int RIGHT_PADDING = 6;
    /** Line top sits 16 GUI px above the bottom edge (9-px font). / 行顶位于底边上方 16 GUI 像素（9 像素字体）。 */
    public static final int BOTTOM_PADDING = 7;
    public static final int ROW_GAP = 2;
    /** Half the vanilla hotbar width (182 / 2). / 原版快捷栏宽度的一半（182 / 2）。 */
    public static final int HOTBAR_HALF_WIDTH = 91;
    /** Horizontal gap kept from the hotbar. / 与快捷栏保持的水平间距。 */
    public static final int HOTBAR_GAP = 4;
    /**
     * Bottom offset of the lifted line: above Wathe's stamina row (top at height - 39) and its cooldown number, + 2 px.
     * 抬升后该行的底部偏移：位于 Wathe 体力行（顶端为 height - 39）及其冷却数字之上，再留 2 像素。
     */
    public static final int LIFTED_BOTTOM_OFFSET = 41;

    private UsecAmmoHudRules() {
    }

    /**
     * HUD gates: a confirmed SparkWitch server, HUD not hidden (F1), Wathe's own HUD showing, an ACTIVE round, a living
     * participant whose exact role is USEC, not spectating, holding the rifle in the main hand. Owner-facing choice:
     * the line describes the stack that fires, so it shows only while that stack is in hand; the hotbar cooldown
     * overlay still covers the rifle when it is not held.
     * HUD 门槛：已确认的 SparkWitch 服务端、HUD 未隐藏（F1）、Wathe 自身 HUD 正在显示、ACTIVE 对局、真实职业为 USEC 的存活
     * 参与者、非旁观、主手持步枪。面向所有者的取舍：该行描述的是实际开火的那把枪，因此只在它在手中时显示；不手持时快捷栏
     * 冷却遮罩仍会显示步枪冷却。
     */
    public static boolean visible(boolean confirmedServer, boolean hudHidden, boolean watheHud, boolean gameActive,
                                  boolean playingAndAlive, boolean usec, boolean spectator, boolean holdingRifle) {
        return confirmedServer && !hudHidden && watheHud && gameActive && playingAndAlive && usec && !spectator
                && holdingRifle;
    }

    /**
     * The line's parts, left to right, separators included. The first part is always the chamber token (the bar sits
     * under it).
     * 该行自左向右的片段（含分隔符）。第一个片段总是弹膛标记（进度条位于其下方）。
     */
    public static List<Part> line(Snapshot snapshot) {
        List<Part> parts = new ArrayList<>(6);
        UsecAmmoType chamber = snapshot.chamber();
        if (isBolt(snapshot.cooldownTicks(), snapshot.cooldownProgress())) {
            parts.add(new Part(CYCLING_KEY, TAN_COLOR, List.of()));
        } else if (chamber != null) {
            parts.add(new Part(chamber.labelKey(), 0xFF000000 | chamber.color(), List.of()));
        } else {
            parts.add(new Part(CHAMBER_EMPTY_KEY, MUTED_COLOR, List.of()));
        }
        UsecMagazineContents magazine = snapshot.magazine();
        if (magazine == null) {
            parts.add(new Part(SEPARATOR_KEY, MUTED_COLOR, List.of()));
            parts.add(new Part(NO_MAGAZINE_KEY, MUTED_COLOR, List.of()));
        } else {
            parts.add(new Part(FEED_SEPARATOR_KEY, TEXT_COLOR, List.of()));
            parts.add(new Part(MAGAZINE_KEY, TEXT_COLOR, List.of(magazine.isEmpty()
                    ? new Empty() : new Rounds(magazine.firingOrder()))));
        }
        if (snapshot.suppressor()) {
            parts.add(new Part(SEPARATOR_KEY, MUTED_COLOR, List.of()));
            parts.add(new Part(SUPPRESSED_KEY, TAN_COLOR, List.of()));
        }
        return List.copyOf(parts);
    }

    /** The tan {@code 4×}/{@code 8×} tag while scoped, else null. / 开镜时的沙色倍率标签，否则为 null。 */
    public static @Nullable Part zoomTag(Snapshot snapshot) {
        if (!snapshot.scoped()) {
            return null;
        }
        return new Part(ZOOM_KEY, TAN_COLOR,
                List.of(new Literal(Integer.toString(UsecInputRules.magnification(snapshot.zoomLevel())))));
    }

    /**
     * A bolt cycle when the whole cooldown (remaining / remaining share) is no longer than a bolt; the round-start lock
     * or any longer forced cooldown keeps the chamber token (the bar still runs). Without a usable share the remaining
     * ticks decide.
     * 整段冷却（剩余 / 剩余比例）不长于一次拉栓时视为拉栓；开局锁定或任何更长的强制冷却保留弹膛标记（进度条照常显示）。
     * 比例不可用时按剩余刻数判断。
     */
    public static boolean isBolt(int remainingTicks, float remainingShare) {
        if (remainingTicks <= 0) {
            return false;
        }
        double total = remainingShare > 0.0F && remainingShare <= 1.0F && Float.isFinite(remainingShare)
                ? remainingTicks / (double) remainingShare : remainingTicks;
        return total <= UsecRules.BOLT_TICKS + 1.0;
    }

    /** The bar shows during any rifle cooldown. / 步枪任何冷却期间都显示进度条。 */
    public static boolean showsBar(int remainingTicks) {
        return remainingTicks > 0;
    }

    /**
     * Filled pixels of a bar as wide as the chamber token, growing left to right as the cooldown runs out
     * ({@code remainingShare} 1 = just started); at least 1 px while shown.
     * 与弹膛标记等宽的进度条的填充像素，随冷却结束自左向右增长（{@code remainingShare} 1 = 刚开始）；显示时至少 1 像素。
     */
    public static int barFill(float remainingShare, int width) {
        if (width <= 0) {
            return 0;
        }
        float share = Float.isFinite(remainingShare) ? Math.max(0.0F, Math.min(1.0F, remainingShare)) : 1.0F;
        return Math.max(1, Math.round((1.0F - share) * width));
    }

    /** Left x of the right-aligned row; never negative. / 右对齐行的左侧 x；不为负。 */
    public static int rowX(int scaledWidth, int rowWidth) {
        return Math.max(0, scaledWidth - RIGHT_PADDING - Math.max(0, rowWidth));
    }

    /** Whether a row starting at {@code rowX} reaches the centred hotbar column. / 该行是否伸入居中的快捷栏列。 */
    public static boolean overlapsHotbar(int scaledWidth, int rowX) {
        return rowX < scaledWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
    }

    /**
     * Top y of the row: {@code height - 16} in the corner; one row higher when the shared corner line (a witch skill
     * line or the Saint's Karma line, which any killer of a Hellfire Saint can carry) is drawn there; above the hotbar
     * and Wathe's stamina row when the row is wide enough to reach the hotbar column.
     * 该行的顶部 y：角落中为 {@code height - 16}；若共享角落行（魔女技能行，或任何在地狱火期间击杀圣徒的人都可能带有的圣徒
     * 业报行）正在显示则上移一行；行宽到会伸入快捷栏列时抬升到快捷栏与 Wathe 体力行之上。
     */
    public static int rowY(int scaledWidth, int scaledHeight, int rowWidth, int fontHeight,
                           boolean sharedLineOccupied) {
        int bottomOffset;
        if (overlapsHotbar(scaledWidth, rowX(scaledWidth, rowWidth))) {
            bottomOffset = LIFTED_BOTTOM_OFFSET;
        } else {
            bottomOffset = BOTTOM_PADDING + (sharedLineOccupied ? fontHeight + ROW_GAP : 0);
        }
        return scaledHeight - bottomOffset - fontHeight;
    }

    /**
     * Local snapshot of the held rifle. {@code cooldownProgress} is vanilla's remaining share (1 just started, 0 done).
     * 手持步枪的本地快照。{@code cooldownProgress} 为原版剩余比例（1 为刚开始，0 为结束）。
     */
    public record Snapshot(@Nullable UsecAmmoType chamber, @Nullable UsecMagazineContents magazine,
                           boolean suppressor, int cooldownTicks, float cooldownProgress, boolean scoped,
                           int zoomLevel) {
    }

    /** One coloured, translated part of the line. / 该行中一个着色的翻译片段。 */
    public record Part(String key, int color, List<Arg> args) {
    }

    /** A part argument. / 片段参数。 */
    public sealed interface Arg permits Literal, Rounds, Empty {
    }

    /** Plain text argument. / 纯文字参数。 */
    public record Literal(String value) implements Arg {
    }

    /** Round labels in firing order, each in its own colour, joined by {@code ·}. / 按发射顺序、各自着色的子弹标签。 */
    public record Rounds(List<UsecAmmoType> rounds) implements Arg {
        public Rounds {
            rounds = List.copyOf(rounds);
        }
    }

    /** The grey {@code 空} of an attached empty magazine. / 已装空弹匣的灰色 {@code 空}。 */
    public record Empty() implements Arg {
    }
}
