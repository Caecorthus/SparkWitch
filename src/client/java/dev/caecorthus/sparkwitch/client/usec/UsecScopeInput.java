package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecScopeC2SPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Scope intent of the local player. Right-click (hold) raises the rifle ({@code UsecRifleItem.use}); a raise that
 * starts while sneaking jumps the magnification to the farther end of 1x-6x first ({@link #afterInputEvents}, before
 * the tick's first zoom sample). Each change of the actually scoped state is sent once at the tick end as
 * {@code sparkwitch:usec_scope}, the server-validated input of the glint others see; it never carries the
 * magnification. "Scoped" is {@link UsecScopeProfile#isActive()} (A12b): the shared {@code client/scope} view gate
 * (first person, the camera is the player, no screen open, not a spectator) with the USEC profile active, never the raw
 * {@code isUsingItem}, so third person or an open menu shows no glint. The tick end also settles the magnification once
 * scoped out and resets it when a Wathe round begins. Client intent only.
 * 本地玩家的开镜意图。按住右键举枪（{@code UsecRifleItem.use}）；潜行时开始举枪会先把倍率跳到 1-6 倍中较远的一端
 * （{@link #afterInputEvents}，在该刻第一次倍率采样之前）。实际开镜状态的每次变化都在刻末只发送一次
 * {@code sparkwitch:usec_scope}，即他人所见反光的、经服务端校验的输入；它从不携带倍率。“开镜”即
 * {@link UsecScopeProfile#isActive()}（A12b）：共享 {@code client/scope} 的视角条件（第一人称、相机即玩家、未打开界面、
 * 非旁观者）成立且 USEC 配置生效，而不是原始的 {@code isUsingItem}，因此第三人称或打开菜单时不会出现反光。刻末还会在退出开镜
 * 后让倍率落定，并在 Wathe 对局开始时重置倍率。仅为客户端意图。
 */
public final class UsecScopeInput {
    private static final UsecScopeTracker TRACKER = new UsecScopeTracker();

    private UsecScopeInput() {
    }

    /**
     * {@code UsecScopeInputMixin}, right after vanilla's {@code handleInputEvents}: Shift + right-click jumps the zoom.
     * {@code UsecScopeInputMixin}，紧接在原版 {@code handleInputEvents} 之后：Shift + 右键跳转倍率。
     */
    public static void afterInputEvents(MinecraftClient client) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = client.player;
        // Sneak pose or the raw key: either means Shift is held for the jump. / 潜行姿态或原始按键均表示按住 Shift。
        boolean sneaking = player != null && (player.isSneaking() || client.options.sneakKey.isPressed());
        if (TRACKER.afterInput(raised(player), sneaking)) {
            UsecZoomState.jumpOnRaise();
        }
    }

    /** End of every client tick on a confirmed server. / 已确认服务器上的每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        UsecZoomState.observeRound(client.world == null
                || GameWorldComponent.KEY.get(client.world).getGameStatus() == GameWorldComponent.GameStatus.INACTIVE);
        boolean raised = raised(player);
        boolean scoped = raised && UsecScopeProfile.isActive();
        if (!scoped) {
            UsecZoomState.settle();
        }
        boolean canSend = client.getNetworkHandler() != null && ClientPlayNetworking.canSend(UsecScopeC2SPacket.ID);
        Boolean send = TRACKER.endTick(raised, scoped, canSend);
        if (send != null) {
            ClientPlayNetworking.send(new UsecScopeC2SPacket(send));
        }
    }

    /** Disconnect or a non-SparkWitch server. / 断线或非 SparkWitch 服务器。 */
    public static void reset() {
        TRACKER.reset();
        UsecZoomState.reset();
    }

    private static boolean raised(ClientPlayerEntity player) {
        return player != null && !player.isSpectator() && UsecRifleClient.isUsingRifleInMainHand(player);
    }
}
