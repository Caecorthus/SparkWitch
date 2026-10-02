package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleRequestC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam over Fabric's {@code UseItemCallback} (default phase, so the Control Expert stun lock, the Seeker and Rift
 * session locks and the Wraith checks decide first). A Riftwalker-owned copy of the Seeker console opener — never an
 * edit or generalisation of it: a live Riftwalker (RAW role) using the SparkStrength tablet (registry id only) from the
 * hotbar asks the server to open the Rift Gate console ({@code rift_gate_console_request(OPEN)}) and returns CONSUME.
 * CONSUME cancels vanilla use without sending the use packet or swinging, so SparkStrength's server {@code use()} never
 * runs; the screen opens when the server's snapshot arrives. Everyone else, the offhand and an armed bypass PASS to
 * SparkStrength's normal behaviour. The "Witch network" button re-issues the vanilla use once through
 * {@link #bypassOnce(Hand)}. The integrated server shares this JVM, so the listener passes on every server world.
 * 基于 Fabric {@code UseItemCallback} 的客户端接缝（默认阶段，让控场专家眩晕锁、搜寻者与裂隙会话锁以及冤魂检查先决定）。
 * 这是搜寻者控制台拦截器的隙行者自有副本——绝不修改或泛化原拦截器：存活的隙行者（原始职业）从快捷栏使用 SparkStrength
 * 平板（仅按注册 id）时，请求服务端打开裂隙门控制台（{@code rift_gate_console_request(OPEN)}）并返回 CONSUME。CONSUME
 * 取消原版使用、不发使用包也不挥手，因此 SparkStrength 服务端的 {@code use()} 不会执行；界面在服务端快照到达时打开。
 * 其他玩家、副手使用与已设置的旁路一律 PASS，交给 SparkStrength 的正常行为。「魔女网络」按钮通过 {@link #bypassOnce(Hand)}
 * 重发一次原版使用。集成服务端与客户端同处一个 JVM，因此监听器对所有服务端世界一律放行。
 */
public final class RiftGateConsoleOpener {
    private static volatile boolean registered;
    /** Client tick counter shared by the opener and the console. / 拦截器与控制台共用的客户端刻计数。 */
    private static volatile long clientTicks;
    private static volatile long lastInterceptTick = -1L;
    /** One-shot bypass: the hand whose next use is passed through, or null. / 一次性旁路：下一次放行的手，或 null。 */
    @Nullable
    private static volatile Hand bypassHand;

    private RiftGateConsoleOpener() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.register(RiftGateConsoleOpener::onUseItem);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTicks++;
            // The bypass is consumed synchronously inside interactItem; a leftover never survives a tick.
            // 旁路在 interactItem 内同步消费；残留的旁路不会跨过一个刻。
            bypassHand = null;
        });
    }

    /**
     * Lets the next use with {@code hand} through exactly once (the console's "Witch network" re-issue).
     * 让 {@code hand} 的下一次使用恰好放行一次（控制台「魔女网络」的重发）。
     */
    public static void bypassOnce(Hand hand) {
        bypassHand = hand;
    }

    static long clientTicks() {
        return clientTicks;
    }

    /** Connection edges; may run off the client thread, so it only clears fields. / 连接节点；可能不在客户端线程执行，只清字段。 */
    static void reset() {
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
        RiftGateConsoleClientRules.InterceptAction action = RiftGateConsoleClientRules.interceptAction(
                isServerReady(),
                isLiveRiftwalker(player),
                RiftSessionService.isInside(player),
                client.currentScreen != null,
                isHotbarTabletUse(player, hand, stack),
                bypassMatches,
                RiftGateConsoleClientRules.throttleElapsed(now, lastInterceptTick,
                        RiftGateConsoleClientRules.INTERCEPT_THROTTLE_TICKS),
                RiftGateConsoleClient.isOpenPending(now));
        if (action == RiftGateConsoleClientRules.InterceptAction.PASS) {
            return TypedActionResult.pass(stack);
        }
        if (action == RiftGateConsoleClientRules.InterceptAction.OPEN) {
            lastInterceptTick = now;
            RiftGateConsoleClient.requestOpen(hand, now);
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
     * A confirmed SparkWitch server that registered the console request payload.
     * 已确认的 SparkWitch 服务器，且注册了控制台请求数据包。
     */
    static boolean isServerReady() {
        return SparkWitchServerConnection.isConfirmedServer()
                && ClientPlayNetworking.canSend(RiftGateConsoleRequestC2SPacket.ID);
    }

    /**
     * SparkStrength opens its tablet only from hotbar slots 0–8: the used stack must be the main-hand (selected hotbar)
     * tablet, matched by registry id.
     * SparkStrength 只从快捷栏 0–8 格打开平板：使用的物品必须是主手（当前选中的快捷栏格）上的平板，按注册 id 识别。
     */
    static boolean isHotbarTabletUse(PlayerEntity player, Hand hand, ItemStack stack) {
        return hand == Hand.MAIN_HAND
                && PlayerInventory.isValidHotbarIndex(player.getInventory().selectedSlot)
                && SparkStrengthTabletCompat.isTablet(stack);
    }

    /**
     * Client view of "live Riftwalker": in a running round, alive, not spectator/creative, and the RAW role (never the
     * Black Raven acting role) is exactly the Riftwalker. Presentation only; the server re-checks every request.
     * 客户端视角的“存活隙行者”：对局进行中、存活、非旁观/创造，且原始职业（绝非黑羽鸦扮演职业）恰为隙行者。
     * 仅用于展示；每个请求都由服务端重新校验。
     */
    public static boolean isLiveRiftwalker(@Nullable PlayerEntity player) {
        if (player == null || !player.isAlive() || !GameFunctions.isPlayerAliveAndSurvival(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        return RiftwalkerRules.isRiftwalker(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
