package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.net.OpenProphetNecrologyS2CPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Prophet's bound Necrology ({@code sparkwitch:prophet_necrology}). The stack is secret-free (no components);
 * using it only asks the server, which alone decides, to authorize the client book built from the owner component.
 * 先知的绑定物品「亡者名录」（{@code sparkwitch:prophet_necrology}）。物品堆不含任何秘密（无组件数据）；使用时只请求服务端
 * 授权，由服务端独自判定后，客户端再根据所有者组件构建书页。
 */
public final class ProphetNecrologyItem extends Item {
    public ProphetNecrologyItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)
                || !ProphetNecrologyRules.isNecrology(stack)
                || !GameFunctions.isPlayerPlayingAndAlive(serverUser)
                || !ProphetRules.isProphet(GameWorldComponent.KEY.get(world).getRole(serverUser))
                || !ServerPlayNetworking.canSend(serverUser, OpenProphetNecrologyS2CPacket.ID)) {
            return TypedActionResult.fail(stack);
        }
        ServerPlayNetworking.send(serverUser, new OpenProphetNecrologyS2CPacket());
        return TypedActionResult.success(stack);
    }
}
