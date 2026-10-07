package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecScopeC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * End-of-tick scope intent of the local player. Right-click (hold) raises the rifle ({@code UsecRifleItem.use}); a
 * raise that starts while sneaking toggles 4x/8x first (S3). Each change of the raised state is sent once as
 * {@code sparkwitch:usec_scope}, the server-validated input of the glint others see. Client intent only.
 * 本地玩家的刻末开镜意图。按住右键举枪（{@code UsecRifleItem.use}）；潜行时开始举枪会先切换 4 倍/8 倍（S3）。举枪状态的
 * 每次变化都只发送一次 {@code sparkwitch:usec_scope}，即他人所见反光的、经服务端校验的输入。仅为客户端意图。
 */
public final class UsecScopeInput {
    private static final UsecScopeTracker TRACKER = new UsecScopeTracker();

    private UsecScopeInput() {
    }

    /** End of every client tick on a confirmed server. / 已确认服务器上的每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean raised = player != null && !player.isSpectator() && UsecRifleClient.isUsingRifleInMainHand(player);
        // Sneak pose or the raw key: either means Shift is held for the toggle. / 潜行姿态或原始按键均表示按住 Shift。
        boolean sneaking = player != null && (player.isSneaking() || client.options.sneakKey.isPressed());
        boolean canSend = client.getNetworkHandler() != null && ClientPlayNetworking.canSend(UsecScopeC2SPacket.ID);
        UsecScopeTracker.Step step = TRACKER.tick(raised, sneaking, canSend);
        if (step.toggleZoom()) {
            UsecZoomState.toggle();
        }
        if (step.send() != null) {
            ClientPlayNetworking.send(new UsecScopeC2SPacket(step.send()));
        }
    }

    public static void reset() {
        TRACKER.reset();
    }
}
