package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 存折: right-click stores coins, left-click (attack) releases them. Coins only come out of a passbook that was
 * filled first, and the passbook disappears once the stored amount has been taken back.
 * 存折：右键存入金币，左键（攻击）取出。只有先存过钱才能取，取完后存折消失。
 */
public final class AccountantPassbookItem extends Item {

    private static final String STORED = "sparkwitch:stored_coins";

    public AccountantPassbookItem(Settings settings) {
        super(settings);
    }

    private static int stored(ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return nbt.contains(STORED) ? nbt.getInt(STORED) : 0;
    }

    private static void setStored(ItemStack stack, int amount) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt(STORED, Math.max(0, amount));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        if (player.isSneaking()) {
            // Sneak + right-click releases the stored coins (the plain left-click is consumed by attacking).
            // 潜行 + 右键取出（普通左键会被攻击动作吃掉）。
            withdraw(player, stack);
            return TypedActionResult.success(stack);
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        int balance = shop.getBalance();
        if (balance <= 0) {
            player.sendMessage(Text.translatable("message.sparkwitch.accountant.no_coins")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }
        setStored(stack, stored(stack) + balance);
        shop.setBalance(0);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENDER_CHEST_OPEN,
                SoundCategory.PLAYERS, 0.6F, 1.4F);
        player.sendMessage(Text.translatable("message.sparkwitch.accountant.deposited", balance)
                .formatted(Formatting.GOLD), true);
        return TypedActionResult.success(stack);
    }

    /** Left-click releases everything that was stored and the passbook is used up. 左键取出全部并存折消失。 */
    public static void withdraw(ServerPlayerEntity player, ItemStack stack) {
        int amount = stored(stack);
        if (amount <= 0) {
            player.sendMessage(Text.translatable("message.sparkwitch.accountant.empty")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        shop.setBalance(shop.getBalance() + amount);
        stack.decrement(1);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.8F, 1.0F);
        player.sendMessage(Text.translatable("message.sparkwitch.accountant.withdrew", amount)
                .formatted(Formatting.GOLD), true);
        var replay = new NbtCompound();
        replay.putString("action", "withdraw");
        replay.putInt("amount", amount);
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                player, AccountantRules.ROLE_ID, null, replay);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int amount = stored(stack);
        tooltip.add(Text.translatable("item.sparkwitch.accountant_passbook.tooltip.stored", amount)
                .formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.sparkwitch.accountant_passbook.tooltip.usage")
                .formatted(Formatting.GRAY));
    }
}
