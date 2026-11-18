package dev.caecorthus.sparkwitch.item.defense;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * The Defense Vial ({@code sparkwitch:defense_vial}): hold right-click for {@link DefenseVialRules#DRINK_TICKS} to
 * drink it. Vanilla calls {@link #finishUsing} when the full hold elapses ({@link #onStoppedUsing} runs only on an
 * early release), so the drink lands there, on the server: one shield layer, the item cooldown, the replay line, and
 * the vial is used up. Releasing early costs nothing. Only a living participant of an ACTIVE round can drink.
 * Right-clicking a Wathe tray pours it instead ({@code FoodPlatterBlockDefenseVialPourMixin}), which shares the cooldown.
 * 防御药剂（{@code sparkwitch:defense_vial}）：按住右键 {@link DefenseVialRules#DRINK_TICKS} 饮用。按满时原版调用
 * {@link #finishUsing}（{@link #onStoppedUsing} 只在提前松手时执行），因此饮用在服务端于此结算：一层护盾、物品冷却、回放记录，
 * 并消耗药剂。提前松手不消耗任何东西。只有 ACTIVE 回合中存活的参与者能饮用。右键 Wathe 托盘则改为倒入
 * （{@code FoodPlatterBlockDefenseVialPourMixin}），倒入与饮用共用冷却。
 */
public final class DefenseVialItem extends Item {

    public DefenseVialItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this) || !DefenseVialService.inActiveRound(user)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return DefenseVialRules.DRINK_TICKS;
    }

    /**
     * Reached only when the full hold elapsed; vanilla puts the returned stack back in the hand. The client returns
     * the stack untouched (the server syncs the slot). The gate is checked again because the round may have ended,
     * or the drinker died or became a spectator, during the hold; so is the cooldown, because vanilla keeps a hold
     * going when something else puts the vial on cooldown mid-drink (a Knight duel seal, a pour of another vial).
     * 只有按满时间才会到达这里；原版会把返回的物品堆放回手中。客户端原样返回（由服务端同步槽位）。再次检查门槛，因为按住
     * 期间对局可能已结束，饮用者也可能已死亡或变为旁观者；也再次检查冷却，因为饮用中途有别的东西让药剂进入冷却时（骑士决斗
     * 封印、倒入另一瓶药剂），原版不会打断按住。
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity drinker) || !DefenseVialService.inActiveRound(drinker)
                || drinker.getItemCooldownManager().isCoolingDown(this)) {
            return stack;
        }
        DefenseVialService.drink(drinker);
        drinker.getItemCooldownManager().set(this, DefenseVialRules.COOLDOWN_TICKS);
        stack.decrementUnlessCreative(1, drinker);
        return stack;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity drinker) {
            drinker.sendMessage(Text.translatable("message.sparkwitch.defense_vial.interrupted"), true);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable("item.sparkwitch.defense_vial.tooltip.drink",
                DefenseVialRules.DRINK_TICKS / 20F, DefenseVialRules.COOLDOWN_TICKS / 20).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.defense_vial.tooltip.pour").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
