package dev.caecorthus.sparkwitch.item;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Toggleable flashlight item.
 * 可开关的手电筒物品。
 */
public final class FlashlightItem extends Item {
    public static final int ON_MODEL_DATA = 1;

    public FlashlightItem(Settings settings) {
        super(settings);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.getItem() instanceof FlashlightItem && hasOnModelData(stack);
    }

    public static boolean isHeldOn(PlayerEntity player) {
        return isOn(player.getMainHandStack()) || isOn(player.getOffHandStack());
    }

    public static boolean hasOnModelData(ItemStack stack) {
        CustomModelDataComponent component = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return component != null && component.value() == ON_MODEL_DATA;
    }

    public static void setOn(ItemStack stack, boolean on) {
        if (on) {
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(ON_MODEL_DATA));
        } else {
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        if (!GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            serverPlayer.sendMessage(Text.translatable("message.sparkwitch.flashlight.unavailable"), true);
            return TypedActionResult.fail(stack);
        }

        setOn(stack, !isOn(stack));
        serverPlayer.sendMessage(Text.translatable(
                isOn(stack)
                        ? "message.sparkwitch.flashlight.on"
                        : "message.sparkwitch.flashlight.off"
        ), true);
        return TypedActionResult.success(stack);
    }
}
