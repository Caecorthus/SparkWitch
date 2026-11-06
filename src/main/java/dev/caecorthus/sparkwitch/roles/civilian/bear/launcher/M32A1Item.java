package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The Milkor M32A1 six-shot 40 mm launcher ({@code sparkwitch:m32a1}). Anyone holding it may use it (owner
 * 2026-11-05); never a Wathe gun. Every stack carries {@link SparkWitchDataComponents#M32_CYLINDER}, empty by default,
 * so a fresh stack is the empty launcher BEAR starts with. WP0 stub: loading, firing, the sight and the HUD are WP1.
 * Milkor M32A1 六发 40 mm 榴弹发射器（{@code sparkwitch:m32a1}）。任何持有者都能使用（所有者 2026-11-05）；从不是 Wathe
 * 枪械。每个物品堆都带有 {@link SparkWitchDataComponents#M32_CYLINDER}，默认为空，因此新物品堆就是 BEAR 开局持有的空发射器。
 * WP0 桩：装填、射击、瞄具与 HUD 归 WP1。
 */
public class M32A1Item extends Item {
    public M32A1Item(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1).component(SparkWitchDataComponents.M32_CYLINDER, M32Cylinder.EMPTY);
    }

    /** The stack's cylinder, {@link M32Cylinder#EMPTY} when absent. / 物品堆的转轮；缺失时为 {@link M32Cylinder#EMPTY}。 */
    public static M32Cylinder cylinder(ItemStack stack) {
        return stack.getOrDefault(SparkWitchDataComponents.M32_CYLINDER, M32Cylinder.EMPTY);
    }
}
