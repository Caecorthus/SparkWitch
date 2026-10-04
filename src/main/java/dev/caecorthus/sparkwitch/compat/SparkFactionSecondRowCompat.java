package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Version gate for SparkFactionAPI's two-row limited inventory (0.1.5.13+). With it, Wathe's in-round inventory also
 * shows main slots 27-35 as a second row directly above the hotbar, and {@code PlayerInventory#getEmptySlot} fills
 * hotbar 0-8, then 27-35, then hidden 9-26. Older versions show only the hotbar, so 9-35 are all hidden. SparkWitch
 * bundles its SparkFactionAPI floor, so either can be loaded; SparkFactionAPI requires client and server versions to
 * match exactly, so the local version answers for both sides. Hotbar-only rules for bound items are unchanged: 27-35
 * stays outside the hotbar. An unknown or unparsable version fails closed (one row, the old behavior).
 * SparkFactionAPI 两行受限物品栏（0.1.5.13+）的版本开关。启用后，Wathe 局内物品栏还会在快捷栏正上方把主背包 27-35
 * 显示为第二行，且 {@code PlayerInventory#getEmptySlot} 按快捷栏 0-8、27-35、隐藏的 9-26 的顺序填充。旧版只显示快捷栏，
 * 9-35 全部隐藏。SparkWitch 内置其 SparkFactionAPI 下限版本，因此两者都可能被加载；SparkFactionAPI 要求客户端与服务端
 * 版本完全一致，所以本地版本即可代表双端。绑定物品的“仅快捷栏”规则不变：27-35 仍不属于快捷栏。版本未知或无法解析时
 * 按失败关闭处理（单行，即旧行为）。
 */
public final class SparkFactionSecondRowCompat {
    static final String MOD_ID = "sparkfactionapi";
    static final String FIRST_VERSION = "0.1.5.13";
    /** First main slot of the second row (vanilla's bottom main row). / 第二行的第一个主背包栏位（原版主背包最下一行）。 */
    public static final int SECOND_ROW_START = PlayerInventory.MAIN_SIZE - PlayerInventory.getHotbarSize();
    /** Exclusive end of the second row. / 第二行的结束下标（不含）。 */
    public static final int SECOND_ROW_END = PlayerInventory.MAIN_SIZE;

    private SparkFactionSecondRowCompat() {
    }

    /** Whether Wathe's limited inventory shows the second row. / Wathe 受限物品栏是否显示第二行。 */
    public static boolean isShown() {
        return Detected.SHOWN;
    }

    public static boolean isSecondRowSlot(int slot) {
        return slot >= SECOND_ROW_START && slot < SECOND_ROW_END;
    }

    /**
     * Spark-style numeric comparison, the same rule as the build's SparkFactionAPI floor check: dot-separated parts,
     * a non-numeric part counts as 0, missing parts count as 0.
     * Spark 风格的数字版本比较，与构建脚本检查 SparkFactionAPI 下限的规则相同：按点分段，非数字段与缺失段都按 0 计。
     */
    static boolean showsSecondRow(@Nullable String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String[] actual = version.split("\\.");
        String[] minimum = FIRST_VERSION.split("\\.");
        for (int index = 0; index < Math.max(actual.length, minimum.length); index++) {
            int actualPart = part(actual, index);
            int minimumPart = part(minimum, index);
            if (actualPart != minimumPart) {
                return actualPart > minimumPart;
            }
        }
        return true;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length || !parts[index].matches("\\d{1,9}")) {
            return 0;
        }
        return Integer.parseInt(parts[index]);
    }

    /** Read once on first use, so pure callers and tests never touch the loader. / 首次使用时读取一次，纯函数调用方与测试不会触及加载器。 */
    private static final class Detected {
        private static final boolean SHOWN = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> showsSecondRow(container.getMetadata().getVersion().getFriendlyString()))
                .orElse(false);
    }
}
