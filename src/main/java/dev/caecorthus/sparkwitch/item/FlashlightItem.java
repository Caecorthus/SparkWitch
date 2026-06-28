package dev.caecorthus.sparkwitch.item;

import dev.caecorthus.sparkwitch.component.RoleEnhancementPlayerComponent;
import dev.caecorthus.sparkwitch.impl.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Attendant flashlight toggle item.
 * 乘务员手电筒开关道具。
 */
public final class FlashlightItem extends Item {
    public FlashlightItem(Settings settings) {
        super(settings);
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

        Role role = GameWorldComponent.KEY.get(serverPlayer.getServerWorld()).getRole(serverPlayer);
        if (!NoellesRoleIds.isAttendant(role) || !GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            serverPlayer.sendMessage(Text.translatable("message.sparkwitch.flashlight.unavailable"), true);
            return TypedActionResult.fail(stack);
        }

        RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(serverPlayer);
        component.setFlashlightOn(!component.isFlashlightOn());
        serverPlayer.sendMessage(Text.translatable(
                component.isFlashlightOn()
                        ? "message.sparkwitch.flashlight.on"
                        : "message.sparkwitch.flashlight.off"
        ), true);
        return TypedActionResult.success(stack);
    }
}
