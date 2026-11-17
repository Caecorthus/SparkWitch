package dev.caecorthus.sparkwitch.item.defense;

import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * The defence vial: a 2.5 s wind-up, one shield layer when it finishes, then a 30 s cooldown on the item. Letting go
 * early cancels it, and the vial is consumed only on a completed drink.
 * <p>
 * 防御药剂：2.5 秒前摇，完成后给一层护盾，随后物品进入 30 秒冷却。提前松手即取消，只有真正喝完才会消耗。
 */
public class DefenseVialItem extends Item {
    /** Wind-up: 2.5 s, which is also the stack's max use time, so finishing it fires on its own. / 前摇：2.5 秒，同时也是最大使用时长，蓄满即自动生效。 */
    public static final int WINDUP_TICKS = 50;
    /** Cooldown after a completed drink: 30 s. / 喝完之后冷却：30 秒。 */
    public static final int COOLDOWN_TICKS = 30 * 20;

    public DefenseVialItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return WINDUP_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient || !(user instanceof ServerPlayerEntity drinker)) {
            return;
        }
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (used < WINDUP_TICKS) {
            drinker.sendMessage(net.minecraft.text.Text.translatable("message.sparkwitch.defense_vial.interrupted"), true);
            return;
        }
        DefenseVialService.applyShield(drinker);
        drinker.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        stack.decrement(1);
        NbtCompound extra = new NbtCompound();
        extra.putString("action", "drink");
        GameRecordManager.recordItemUse(drinker, Registries.ITEM.getId(this), null, extra);
    }
}
