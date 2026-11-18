package dev.caecorthus.sparkwitch.item.m1903a4;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Springfield .30-06 service round: held in the other hand it loads one round into the M1903A4 through
 * {@link M1903A4Item#tryReload}, and it can also be right-clicked onto the rifle in the inventory.
 * 春田 .30-06 制式步枪弹：另一只手持枪时使用可经 {@link M1903A4Item#tryReload} 装一发，也可在背包中右键步枪装填。
 */
public class Ammo3006Item extends Item {

    public Ammo3006Item(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(64);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack ammo = user.getStackInHand(hand);
        ItemStack rifle = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        if (!(rifle.getItem() instanceof M1903A4Item)) {
            return TypedActionResult.pass(ammo);
        }
        if (!M1903A4Item.tryReload(user, rifle, ammo)) {
            return TypedActionResult.fail(ammo);
        }
        return TypedActionResult.success(ammo, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(getTranslationKey() + ".tooltip").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
