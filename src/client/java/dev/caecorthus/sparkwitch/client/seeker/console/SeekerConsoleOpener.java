package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.console.SeekerConsoleDevices;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam over Fabric's {@code UseItemCallback} (default phase, so the Control Expert stun lock, the Seeker session
 * lock and the Wraith checks decide first): the Seeker's right-click on a console device (the SparkStrength tablet by
 * registry id, or the car-item fallback without SparkStrength) opens {@link SeekerConsoleScreen} and returns CONSUME.
 * CONSUME cancels vanilla use without sending the use packet or swinging, so SparkStrength's server {@code use()} never
 * runs. Sneaking is deliberately ignored. The "Police network" button re-issues the vanilla use once through
 * {@link #bypassOnce(Hand)}. The integrated server shares this JVM, so the listener passes on every server world.
 * 基于 Fabric {@code UseItemCallback} 的客户端接缝（默认阶段，让控场专家眩晕锁、搜寻者会话锁与冤魂检查先决定）：
 * 搜寻者右键控制台设备（按注册 id 识别的 SparkStrength 平板，或无 SparkStrength 时的小车兜底）会打开
 * {@link SeekerConsoleScreen} 并返回 CONSUME。CONSUME 取消原版使用、不发使用包也不挥手，因此 SparkStrength
 * 服务端的 {@code use()} 不会执行。刻意不判断潜行。“警察网络”按钮通过 {@link #bypassOnce(Hand)} 重发一次原版使用。
 * 集成服务端与客户端同处一个 JVM，因此监听器对所有服务端世界一律放行。
 */
public final class SeekerConsoleOpener {
    private static volatile boolean registered;
    /** Client tick counter used for the intercept throttle. / 用于拦截节流的客户端刻计数。 */
    private static volatile long clientTicks;
    private static volatile long lastInterceptTick = -1L;
    /** One-shot bypass: the hand whose next use is passed through, or null. / 一次性旁路：下一次放行的手，或 null。 */
    @Nullable
    private static volatile Hand bypassHand;

    private SeekerConsoleOpener() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.register(SeekerConsoleOpener::onUseItem);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTicks++;
            // The bypass is consumed synchronously inside interactItem; a leftover never survives a tick.
            // 旁路在 interactItem 内同步消费；残留的旁路不会跨过一个刻。
            bypassHand = null;
        });
    }

    /**
     * Lets the next use with {@code hand} through exactly once (the console's "Police network" re-issue).
     * 让 {@code hand} 的下一次使用恰好放行一次（控制台“警察网络”的重发）。
     */
    public static void bypassOnce(Hand hand) {
        bypassHand = hand;
    }

    /** Connection edges; may run off the client thread, so it only clears fields. / 连接节点；可能不在客户端线程执行，只清字段。 */
    public static void reset() {
        bypassHand = null;
        lastInterceptTick = -1L;
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!world.isClient) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        ItemStack stack = player.getStackInHand(hand);
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player) {
            return TypedActionResult.pass(stack);
        }
        boolean bypassMatches = consumeBypass(hand);
        long now = clientTicks;
        SeekerConsoleRules.InterceptAction action = SeekerConsoleRules.interceptAction(
                SparkWitchServerConnection.isConfirmedServer(),
                isLiveSeeker(player),
                isInSession(),
                client.currentScreen != null,
                SeekerConsoleDevices.isConsoleDevice(player, stack),
                bypassMatches,
                SeekerConsoleRules.throttleElapsed(now, lastInterceptTick, SeekerConsoleRules.INTERCEPT_THROTTLE_TICKS));
        if (action == SeekerConsoleRules.InterceptAction.PASS) {
            return TypedActionResult.pass(stack);
        }
        if (action == SeekerConsoleRules.InterceptAction.OPEN) {
            lastInterceptTick = now;
            client.setScreen(new SeekerConsoleScreen(hand));
        }
        // CONSUME, never SUCCESS: only SUCCESS makes Fabric send the use packet, so a throttled console use is swallowed
        // here instead of reaching SparkStrength's own tablet.
        // 只有 SUCCESS 才会发包，所以用 CONSUME；被节流的控制台使用也在此吞掉，不会落到 SparkStrength 自己的平板。
        return TypedActionResult.consume(stack);
    }

    private static boolean consumeBypass(Hand hand) {
        if (bypassHand != hand) {
            return false;
        }
        bypassHand = null;
        return true;
    }

    /**
     * Client view of "live Seeker participant": in a running round, holds a role, not dead, not spectator/creative,
     * and the current role is exactly the Seeker. Presentation only; the server re-checks every action.
     * 客户端视角的“存活的搜寻者参与者”：对局进行中、拥有职业、未死亡、非旁观/创造，且当前职业恰为搜寻者。
     * 仅用于展示；每个动作都由服务端重新校验。
     */
    public static boolean isLiveSeeker(@Nullable PlayerEntity player) {
        if (player == null || !player.isAlive() || !GameFunctions.isPlayerAliveAndSurvival(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        return SeekerRules.isSeeker(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Synced session mode or the local remote view, whichever is active. / 同步的会话模式或本地遥控视角，任一处于活动即为真。 */
    public static boolean isInSession() {
        return SeekerClientState.isInSession() || SeekerRemoteViewClient.isActive();
    }
}
