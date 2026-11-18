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
 * Springfield .30-06 service round: used from the main hand with the rifle in the off hand it loads one round into the
 * M1903A4 through {@link M1903A4Item#tryReload}, and it can also be right-clicked onto the rifle in the inventory. The
 * rifle itself only charges from the main hand, so a failed load never falls through into a shot.
 * 春田 .30-06 制式步枪弹：主手持弹、副手持枪时使用，可经 {@link M1903A4Item#tryReload} 装一发；也可在背包中右键步枪装填。
 * 步枪本身只能在主手蓄力，因此装填失败不会转为开火。
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
