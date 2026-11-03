package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 乘务员钥匙 — the Discoverer's multi-use train-door key.
 * <p>Borrows NoellesRoles' master-key texture and the same “open like a master key” behaviour, but keeps its own
 * durability: a train door costs {@link DiscovererRules#CONDUCTOR_KEY_TRAIN_DOOR_COST} points and a room-locked small
 * door costs {@link DiscovererRules#CONDUCTOR_KEY_ROOM_DOOR_COST}, an attempt with fewer
 * remaining points does nothing at all (no door, no durability), and a key at zero vanishes instead of lingering as a
 * broken item. Door handling lives in {@link ConductorKeyDoors} on Wathe's {@code DoorInteraction.EVENT}.
 * 乘务员钥匙——探索者的可多次使用列车门钥匙。
 * <p>借用诺艾尔万能钥匙的贴图与“像万能钥匙一样开门”的行为，但耐久独立：每开一次花
 * 列车门 3 点、被房间钥匙锁住的小木门 1 点；剩余不足这扇门的价格时这次尝试完全不生效（不开门也不扣耐久）；
 * 耐久归零则直接消失，不会留下损坏的钥匙。开门逻辑在 {@link ConductorKeyDoors}（Wathe {@code DoorInteraction.EVENT}）。
 */
public final class ConductorKeyItem extends Item {

    public ConductorKeyItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings()
                .maxCount(1)
                .maxDamage(DiscovererRules.CONDUCTOR_KEY_DURABILITY);
    }

    /** Remaining points on this stack. 这叠钥匙的剩余耐久点。 */
    public static int remaining(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamage();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(
                "item.sparkwitch.conductor_key.tooltip.remaining",
                remaining(stack),
                stack.getMaxDamage()
        ).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(
                "item.sparkwitch.conductor_key.tooltip.cost",
                DiscovererRules.CONDUCTOR_KEY_TRAIN_DOOR_COST,
                DiscovererRules.CONDUCTOR_KEY_ROOM_DOOR_COST
        ).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
