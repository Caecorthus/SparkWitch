package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.net.OpenNatsumeMenuS2CPacket;
import dev.caecorthus.sparkwitch.net.RequestNatsumeMenuC2SPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 夏目安安 client half: the ability key asks the server for the target menu, and the menu packet opens it. The server
 * decides everything; the client never predicts a cooldown or a hypnosis. Instinct loss and the HUD garble are mixins
 * ({@code mixin/natsume/}) driven by {@link NatsumeClientState}.
 * 夏目安安客户端：技能键向服务端请求目标菜单，菜单数据包打开它。一切由服务端决定；客户端从不预测冷却或催眠。失去本能与
 * HUD 乱码由 {@code mixin/natsume/} 中的 mixin 实现，状态来自 {@link NatsumeClientState}。
 */
public final class NatsumeClientModule {

    private NatsumeClientModule() {
    }

    public static void register() {
        // Fabric 0.116.7 runs play receivers on the render thread, so the screen opens directly.
        // Fabric 0.116.7 在渲染线程执行 play 阶段接收器，因此直接打开界面。
        ClientPlayNetworking.registerGlobalReceiver(OpenNatsumeMenuS2CPacket.ID, (payload, context) ->
                context.client().setScreen(new NatsumeTargetScreen(payload.targets(), payload.names())));
    }

    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (!NatsumeClientState.isNatsume(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        // A hypnotised Natsume (she may pick herself) cannot use her skill; the server refuses it too.
        // 被催眠的夏目安安（她可以选择自己）无法使用技能；服务端同样会拒绝。
        if (NatsumeClientState.isHypnotised(player)) {
            return;
        }
        if (ClientPlayNetworking.canSend(RequestNatsumeMenuC2SPacket.ID)) {
            ClientPlayNetworking.send(new RequestNatsumeMenuC2SPacket());
        }
    }
}
