package dev.caecorthus.sparkwitch.client.riftwalker.session;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * Pure layout and state rules of the in-gate arrow bar (plan §6.4, C2): a two-slot bar in the hotbar's place, the
 * "#gate · n/m" label on its left, the stay countdown on its right (red for the last 5 s), and the greyed arrows when
 * there is no other gate. Unit-tested without a game.
 * 门内箭头栏的纯布局与状态规则（plan §6.4、C2）：在快捷栏位置画两格栏，左侧为「#门号 · n/m」标签，右侧为停留倒计时
 * （最后 5 秒变红），没有其他门时箭头变灰。可脱离游戏单元测试。
 */
public final class RiftSessionHudRules {
    /** Plain textures (not gui sprites), drawn with {@code DrawContext#drawTexture}. / 普通贴图（不是 GUI 精灵）。 */
    public static final Identifier ARROW_LEFT = SparkWitch.id("textures/gui/riftwalker/rift_arrow_left.png");
    public static final Identifier ARROW_RIGHT = SparkWitch.id("textures/gui/riftwalker/rift_arrow_right.png");
    public static final Identifier ARROW_LEFT_DISABLED =
            SparkWitch.id("textures/gui/riftwalker/rift_arrow_left_disabled.png");
    public static final Identifier ARROW_RIGHT_DISABLED =
            SparkWitch.id("textures/gui/riftwalker/rift_arrow_right_disabled.png");
    /** Wathe's hotbar sprites (182×22 and 24×23), so the bar matches the in-match hotbar. / Wathe 的快捷栏精灵。 */
    public static final Identifier HOTBAR_SPRITE = Identifier.of("wathe", "hud/hotbar");
    public static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.of("wathe", "hud/hotbar_selection");
    public static final int HOTBAR_SPRITE_WIDTH = 182;
    public static final int HOTBAR_SPRITE_HEIGHT = 22;
    public static final int SELECTION_WIDTH = 24;
    public static final int SELECTION_HEIGHT = 23;
    public static final int SLOT_SIZE = 20;
    public static final int ICON_SIZE = 16;
    /** Left border + two slots + the right end cap. / 左边框 + 两格 + 右端盖。 */
    public static final int BAR_WIDTH = 1 + SLOT_SIZE * RiftSessionInputRules.SLOT_COUNT + 1;
    public static final int BAR_HEIGHT = HOTBAR_SPRITE_HEIGHT;
    /** Gap between the bar and its side labels. / 栏与两侧文字的间距。 */
    public static final int LABEL_GAP = 6;
    public static final int TICKS_PER_SECOND = 20;
    /** C2: the countdown turns red for the last 5 seconds. / C2：最后 5 秒倒计时变红。 */
    public static final int URGENT_TICKS = 5 * TICKS_PER_SECOND;
    public static final int TEXT_COLOR = 0xFFE6E6F0;
    public static final int URGENT_COLOR = 0xFFFF5555;
    public static final int HINT_COLOR = 0xFFA8A8C0;
    /** Fallback (no post shader): translucent grey over the world, under the HUD. / 回退：世界之上、HUD 之下的半透明灰。 */
    public static final int FALLBACK_TINT = 0x66808088;
    /** Fallback vignette strength (vanilla darkening blend). / 回退暗角强度（原版变暗混合）。 */
    public static final float FALLBACK_VIGNETTE = 0.75F;

    private RiftSessionHudRules() {
    }

    public static int barX(int screenWidth) {
        return screenWidth / 2 - BAR_WIDTH / 2;
    }

    public static int barY(int screenHeight) {
        return screenHeight - BAR_HEIGHT;
    }

    /** Same offsets as vanilla hotbar items (+3, +3). / 与原版快捷栏物品相同的偏移。 */
    public static int iconX(int barX, int slot) {
        return barX + 3 + RiftSessionInputRules.normalizeSlot(slot) * SLOT_SIZE;
    }

    public static int iconY(int barY) {
        return barY + 3;
    }

    /** Same offsets as the vanilla selection frame (−1, −1). / 与原版选中框相同的偏移。 */
    public static int selectionX(int barX, int slot) {
        return barX - 1 + RiftSessionInputRules.normalizeSlot(slot) * SLOT_SIZE;
    }

    public static int selectionY(int barY) {
        return barY - 1;
    }

    /** Text baseline row of the side labels (vertically centred on the bar). / 两侧文字所在行。 */
    public static int labelY(int barY) {
        return barY + (BAR_HEIGHT - 8) / 2;
    }

    /**
     * Key-hint row: two text lines above the bar, so it never collides with Wathe's item-cooldown line, which sits one
     * line above the hotbar.
     * 按键提示所在行：位于栏上方两行，避免与 Wathe 位于快捷栏上方一行的物品冷却文字重叠。
     */
    public static int hintY(int barY, int fontHeight) {
        return barY - 2 * (fontHeight + 4);
    }

    /** The countdown appears once the server synced a stay limit (never a bogus "0s"). / 服务端同步停留上限后才显示倒计时。 */
    public static boolean showsTimer(int stayLimitTicks) {
        return stayLimitTicks > 0;
    }

    /** Arrows are live only when another gate exists. / 只有存在其他门时箭头才可用。 */
    public static boolean arrowsEnabled(int ringSize) {
        return ringSize > 1;
    }

    public static Identifier arrowTexture(int slot, boolean enabled) {
        boolean previous = RiftSessionInputRules.normalizeSlot(slot) == RiftSessionInputRules.PREVIOUS_SLOT;
        if (enabled) {
            return previous ? ARROW_LEFT : ARROW_RIGHT;
        }
        return previous ? ARROW_LEFT_DISABLED : ARROW_RIGHT_DISABLED;
    }

    /** Whole seconds shown, rounded up so "1" lasts until the very end. / 显示的整秒数，向上取整。 */
    public static int seconds(int remainingTicks) {
        return remainingTicks <= 0 ? 0 : (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    public static boolean urgent(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks <= URGENT_TICKS;
    }

    public static int timerColor(int remainingTicks) {
        return urgent(remainingTicks) ? URGENT_COLOR : TEXT_COLOR;
    }

    /** "n/m" is shown only when the server sent a ring position. / 仅当服务端发送了环位置时显示「n/m」。 */
    public static boolean showsRing(int ringIndex, int ringSize) {
        return ringIndex > 0 && ringSize > 0 && ringIndex <= ringSize;
    }
}
