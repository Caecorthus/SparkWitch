package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Random;

/**
 * Client only. Wiring of the scoped AXMC bolt sway ({@link UsecBoltSway}), registered once by
 * {@code UsecClientModule}: one end-of-tick listener feeds the local player's scope state, interruptions and synced
 * rifle cooldown (as classified by {@link UsecBoltWatchClient#status}, so a short forced lock is never a bolt) to the
 * tracker, and a disconnect resets it. {@code UsecFireInput} reports each sent fire request, and
 * {@code UsecBoltSwayCameraMixin} reads {@link #currentAngles} per frame. Presentation only: nothing is sent or synced,
 * and it never touches the witch skill inventory panel.
 * 仅客户端。开镜 AXMC 拉栓晃动（{@link UsecBoltSway}）的接线，由 {@code UsecClientModule} 注册一次：一个刻末监听器把本地
 * 玩家的开镜状态、打断条件与已同步的步枪冷却（经 {@link UsecBoltWatchClient#status} 分类，短暂的强制锁定永远不算拉栓）喂给
 * 追踪器，断线时重置。{@code UsecFireInput} 报告每个已发出的开火请求，
 * {@code UsecBoltSwayCameraMixin} 每帧读取 {@link #currentAngles}。仅为表现：不发送也不同步任何内容，从不触及魔女技能背包面板。
 */
public final class UsecBoltSwayClient {
    private static final UsecBoltSway.Tracker TRACKER = new UsecBoltSway.Tracker(new Random());
    private static boolean registered;

    private UsecBoltSwayClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(UsecBoltSwayClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRACKER.reset());
    }

    /**
     * {@code UsecFireInput}: a fire request was just sent with this synced rifle state. Arms the sway only when it
     * chambers a new round; the synced bolt cooldown that follows confirms it.
     * {@code UsecFireInput}：刚以该同步步枪状态发出开火请求。只有会使新子弹上膛时才预备晃动；随后同步的拉栓冷却负责确认。
     */
    public static void onShotSent(UsecRifleState stateAtSend) {
        TRACKER.onShotSent(UsecBoltSway.chambersRound(stateAtSend));
    }

    /**
     * Camera mixin: the sway to add this frame, in world degrees, scaled by the player's accessibility sliders.
     * 镜头 mixin：本帧要追加的晃动（世界角度，度），按玩家的无障碍滑块缩放。
     */
    public static UsecBoltSway.Angles currentAngles(float tickDelta) {
        UsecBoltSway.Angles angles = TRACKER.angles(tickDelta);
        if (angles.isZero()) {
            return angles;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return angles.scaled(UsecBoltSway.accessibilityScale(client.options.getDistortionEffectScale().getValue(),
                client.options.getDamageTiltStrength().getValue()));
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean interrupted = !SparkWitchServerConnection.isConfirmedServer() || player == null || !player.isAlive()
                || player.isSpectator() || client.currentScreen != null
                || !UsecRifleClient.holdsRifleInMainHand(player);
        TRACKER.tick(!interrupted && UsecScopeProfile.isActive(), interrupted, UsecBoltWatchClient.status(player));
    }
}
