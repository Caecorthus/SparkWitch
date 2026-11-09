package dev.caecorthus.sparkwitch.roles.killer.specter;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;

/**
 * The anti-Specter peachwood sword, given while the Otherworld is open to non-killer players who are cowardly or at
 * effective real sanity 25 or below. It has no durability, so it can never break, and a permanent glint. Its attack
 * attribute is cosmetic: only the four-stab hit resolved by {@link SpecterPeachwoodService} deals damage, and only to
 * the Specter.
 * 反制幽魂的桃木剑。里世界开启期间发给怯懦或有效真实理智不高于 25 的非杀手玩家。没有耐久，永不损坏，并带常驻附魔光效。
 * 攻击属性只是摆设：真正造成伤害的只有 {@link SpecterPeachwoodService} 结算的 4 次刀刺，且只对幽魂有效。
 */
public final class PeachwoodSwordItem extends Item {
    public PeachwoodSwordItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings()
                .maxCount(1)
                .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
                .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.WOOD, 3, -2.4F));
    }
}
