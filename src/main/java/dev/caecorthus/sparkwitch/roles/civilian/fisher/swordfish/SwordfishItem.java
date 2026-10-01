package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherItemTooltips;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Independent item with knife-like charging. Inheriting the knife would also inherit other mods' instant-use
 * replacements; only the target selector is shared. Visible in hand, with ordinary prepare/stab sounds.
 * 独立物品，手感同刀；继承刀还会继承其他模组的瞬刺替换，因此仅共享选靶。手持可见，正常播放蓄力与刺击声。
 */
public final class SwordfishItem extends Item {
    private static final int TOOLTIP_LINES = 2;

    public SwordfishItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity player) {
            SwordfishStabService.clearPlayer(player);
        }
        if (hand != Hand.MAIN_HAND || user.isSpectator()
                || user.getItemCooldownManager().isCoolingDown(this)
                || SparkTraitsKillerBridge.blocksWeaponAction(user, stack)
                || (user instanceof ServerPlayerEntity player && !SwordfishStabService.canUse(player, stack))) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        user.playSound(WatheSounds.ITEM_KNIFE_PREPARE, 1.0F, 1.0F);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return SwordfishRules.MAX_USE_TICKS;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (player.getActiveHand() == Hand.MAIN_HAND && player.getMainHandStack() == stack
                    && player.getActiveItem() == stack) {
                SwordfishStabService.recordServerRelease(player, getMaxUseTime(stack, user) - remainingUseTicks);
            } else {
                SwordfishStabService.clearPlayer(player);
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        FisherItemTooltips.append(tooltip, FisherRules.SWORDFISH_ID, TOOLTIP_LINES);
    }
}
