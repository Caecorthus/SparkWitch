package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The black Rys-T helmet with face shield ({@code sparkwitch:rys_t}), worn in the head slot. Its ricochet (H1-H6)
 * applies to whoever wears it (D1); its visor state and glass cracks live in
 * {@link SparkWitchDataComponents#RYS_T_VISOR} (default {@link RysTVisor#DEFAULT}: visor down, no cracks). WP0 stub:
 * head-slot equipping, the visor toggle, the worn model and the overlay are WP2; the ricochet is WP3.
 * 黑色 Rys-T 头盔与面罩（{@code sparkwitch:rys_t}），戴在头部槽。其跳弹（H1-H6）作用于佩戴者（D1）；面罩状态与玻璃裂纹存于
 * {@link SparkWitchDataComponents#RYS_T_VISOR}（默认 {@link RysTVisor#DEFAULT}：面罩放下、无裂纹）。WP0 桩：头部槽穿戴、
 * 面罩开合、穿戴模型与遮罩画面归 WP2；跳弹归 WP3。
 */
public class RysTHelmetItem extends Item {
    public RysTHelmetItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1).component(SparkWitchDataComponents.RYS_T_VISOR, RysTVisor.DEFAULT);
    }

    /** The stack's visor, {@link RysTVisor#DEFAULT} when absent. / 物品堆的面罩状态；缺失时为 {@link RysTVisor#DEFAULT}。 */
    public static RysTVisor visor(ItemStack stack) {
        return stack.getOrDefault(SparkWitchDataComponents.RYS_T_VISOR, RysTVisor.DEFAULT);
    }
}
