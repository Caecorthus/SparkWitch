package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteOpenC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Quick connect on SparkWitch's generic secondary-ability key, dispatched by role id through
 * {@code SecondaryAbilityRegistry.register(SeekerRules.ROLE_ID, ...)}. Outside a session it opens the car if usable,
 * otherwise the default camera; inside a session it toggles car ↔ camera (the camera is the server's default choice;
 * the server treats a second open as an atomic switch). Usability comes from {@link SeekerDeviceAvailability}, the
 * console's own prediction. It only sends {@code seeker_remote_open} without a target; the server still requires a
 * console device and every other open check and answers a refusal with its own message.
 * SparkWitch 通用第二技能键上的快速连接，经 {@code SecondaryAbilityRegistry.register(SeekerRules.ROLE_ID, ...)}
 * 按职业 id 分发。会话外：小车可用则连小车，否则连默认摄像头；会话内：在小车与摄像头之间切换（摄像头由服务端选择
 * 默认那台；服务端把第二次打开视为原子切换）。可用性来自 {@link SeekerDeviceAvailability}，即控制台自身的预测。
 * 它只发送不指定目标的 {@code seeker_remote_open}；服务端仍要求持有控制台设备并执行其余打开检查，拒绝时由服务端发送提示。
 */
public final class SeekerQuickConnectHandler {
    private static boolean registered;
    private static volatile long ticks;
    private static volatile long lastSendTick = -1L;

    private SeekerQuickConnectHandler() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SecondaryAbilityRegistry.register(SeekerRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                SeekerQuickConnectHandler.onPressed(client);
            }

            @Override
            public void tick(MinecraftClient client) {
                ticks++;
            }

            @Override
            public void reset() {
                SeekerQuickConnectHandler.reset();
            }
        });
    }

    private static void onPressed(MinecraftClient client) {
        if (!SparkWitchServerConnection.isConfirmedServer() || client.currentScreen != null
                || !SeekerConsoleOpener.isLiveSeeker(client.player)
                || !SeekerConsoleRules.throttleElapsed(ticks, lastSendTick,
                SeekerConsoleRules.QUICK_CONNECT_THROTTLE_TICKS)
                || !ClientPlayNetworking.canSend(SeekerRemoteOpenC2SPacket.ID)) {
            return;
        }
        SeekerSessionMode current = SeekerClientState.sessionMode();
        if (current == SeekerSessionMode.NONE && SeekerRemoteViewClient.isActive()) {
            current = SeekerRemoteViewClient.mode();
        }
        boolean carUsable = SeekerDeviceAvailability.car(client) == SeekerConsoleRules.Availability.AVAILABLE;
        boolean cameraUsable = SeekerDeviceAvailability.camera(client) == SeekerConsoleRules.Availability.AVAILABLE;
        SeekerSessionMode target = SeekerConsoleRules.quickConnectTarget(current, SeekerClientState.carState(),
                carUsable, cameraUsable);
        lastSendTick = ticks;
        ClientPlayNetworking.send(SeekerRemoteOpenC2SPacket.of(target));
    }

    private static void reset() {
        lastSendTick = -1L;
    }
}
