package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerInventory;

/**
 * SparkFactionAPI's two-row limited inventory (first shipped in 0.1.5.13, so present in every admitted SparkFactionAPI,
 * whose floor is 2.0.0). Wathe's in-round inventory also shows main slots 27-35 as a second row directly above the
 * hotbar, and {@code PlayerInventory#getEmptySlot} fills hotbar 0-8, then 27-35, then hidden 9-26. Hotbar-only rules
 * for bound items are unchanged: 27-35 stays outside the hotbar.
 * SparkFactionAPI 两行受限物品栏（首次随 0.1.5.13 提供，因此下限为 2.0.0 的所有可接受 SparkFactionAPI 都具备）。Wathe 局内
 * 物品栏还会在快捷栏正上方把主背包 27-35 显示为第二行，且 {@code PlayerInventory#getEmptySlot} 按快捷栏 0-8、27-35、
 * 隐藏的 9-26 的顺序填充。绑定物品的“仅快捷栏”规则不变：27-35 仍不属于快捷栏。
 */
public final class SparkFactionSecondRowCompat {
    /** First main slot of the second row (vanilla's bottom main row). / 第二行的第一个主背包栏位（原版主背包最下一行）。 */
    public static final int SECOND_ROW_START = PlayerInventory.MAIN_SIZE - PlayerInventory.getHotbarSize();
    /** Exclusive end of the second row. / 第二行的结束下标（不含）。 */
    public static final int SECOND_ROW_END = PlayerInventory.MAIN_SIZE;

    private SparkFactionSecondRowCompat() {
    }

    /**
     * Whether Wathe's limited inventory shows the second row: always, because the SparkFactionAPI floor is past the
     * first two-row version. Kept as the single wiring point its many callers and their pure rules share; the old
     * one-row version detection is gone.
     * Wathe 受限物品栏是否显示第二行：恒为是，因为 SparkFactionAPI 下限已高于首个两行版本。保留为众多调用方及其纯规则
     * 共用的唯一接线点；旧的单行版本检测已移除。
     */
    public static boolean isShown() {
        return true;
    }

    public static boolean isSecondRowSlot(int slot) {
        return slot >= SECOND_ROW_START && slot < SECOND_ROW_END;
    }
}
