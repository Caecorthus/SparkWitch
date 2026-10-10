package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.client.input.MacControlClickRules;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.net.FireDeathRayC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only left-click bridge for Death Ray; the server still owns all hit validation. On macOS a Ctrl + left press
 * while the ray owns the attack stays a left press ({@link #keepsMacLeftPress}, 2026-10-09). The server never checks
 * the held item for a ray, so a same-tick hotbar key needs no slot flush here.
 * 死亡射线的客户端左键桥接；命中判定仍完全由服务端负责。macOS 上射线接管攻击时，Ctrl + 左键按下仍是左键
 * （{@link #keepsMacLeftPress}，2026-10-09）。服务端发射射线从不检查手持物品，因此同一刻的快捷栏按键无需在此同步栏位。
 */
public final class DeathRayClientHooks {
    private static boolean attackHeld;

    private DeathRayClientHooks() {
    }

    public static void tick(MinecraftClient client) {
        if (client.options == null || !client.options.attackKey.isPressed()) {
            attackHeld = false;
        }
    }

    public static void reset() {
        attackHeld = false;
    }

    public static boolean tryFire(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!ownsAttack(client, player)) {
            return false;
        }
        if (SparkTraitsKillerBridge.isKillerInteractionBlocked(player)) {
            attackHeld = true;
            return true;
        }
        if (attackHeld) {
            return true;
        }
        attackHeld = true;
        ClientPlayNetworking.send(new FireDeathRayC2SPacket(player.getYaw(), player.getPitch()));
        return true;
    }

    /**
     * {@code Mouse.onMouseButton}, at its only {@code IS_SYSTEM_MAC} read (macOS only): true keeps this left press as
     * button 0, uncounted, so Ctrl + left-click (sprint is Left Ctrl) reaches the attack key and {@link #tryFire} (via
     * {@code doAttack} or held-attack block breaking) instead of using the held item. The ray owns the press exactly when {@link #tryFire} would take it; a SparkTraits
     * block or a held latch then still swallows it there, as for a plain left click. Never fires by itself and never
     * touches a release; see {@link MacControlClickRules#keepsLeftPress}.
     * {@code Mouse.onMouseButton} 中唯一一次读取 {@code IS_SYSTEM_MAC} 处（仅 macOS）：返回 true 时本次左键按下保持为按键 0
     * 且不计数，于是 Ctrl + 左键（疾跑是左 Ctrl）会到达攻击键与 {@link #tryFire}（经由 {@code doAttack} 或按住攻击的方块
     * 挖掘），而不是使用手持物品。射线恰好在
     * {@link #tryFire} 会接管按键时占有它；SparkTraits 封锁或按住闩锁随后仍会在那里吞掉它，与普通左键相同。它自身从不发射，
     * 也从不改动松开；见 {@link MacControlClickRules#keepsLeftPress}。
     */
    public static boolean keepsMacLeftPress(MinecraftClient client, int button, int action,
                                            int pendingRemappedReleases) {
        return MacControlClickRules.keepsLeftPress(
                button == GLFW.GLFW_MOUSE_BUTTON_LEFT,
                action == GLFW.GLFW_PRESS,
                client.currentScreen != null,
                ownsAttack(client, client.player),
                pendingRemappedReleases);
    }

    /**
     * The single claim gate of {@link #tryFire} (and so of the Mac press): a confirmed SparkWitch server, a local
     * player and network handler, and an active Death Ray window.
     * {@link #tryFire} 的唯一接管条件（也是 Mac 按下的条件）：已确认的 SparkWitch 服务器、本地玩家与网络处理器，以及生效中的
     * 死亡射线窗口。
     */
    private static boolean ownsAttack(MinecraftClient client, @Nullable ClientPlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && client.getNetworkHandler() != null
                && hasActiveDeathRay(player);
    }

    private static boolean hasActiveDeathRay(ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        return MurderousWitchDeathRayRules.isDeathRaySkill(component.getActiveSkillId())
                && component.hasActiveDeathRay();
    }
}
