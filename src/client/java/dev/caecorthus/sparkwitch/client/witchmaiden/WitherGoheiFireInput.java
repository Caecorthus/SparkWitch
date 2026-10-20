package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.client.input.MacControlClickRules;
import dev.caecorthus.sparkwitch.client.mixin.input.SelectedSlotFlushInvoker;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.WitherGoheiInventoryRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net.FireCrimsonSkullC2SPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Wither Gohei left-click, client side (Potion Gunner launcher pattern): the held Gohei swallows the vanilla attack and
 * block breaking, and each fresh attack press sends one {@code fire_crimson_skull} with the click-time aim. A
 * keyboard-bound attack key held down never auto-repeats a shot.
 * 凋灵御币左键的客户端部分（沿用药炮筒的做法）：手持御币时吞掉原版攻击与挖掘，每次新的攻击按下都发送一次带点击瞬间朝向的
 * {@code fire_crimson_skull}。按住绑定在键盘上的攻击键不会自动重复射击。
 */
public final class WitherGoheiFireInput {
    /** Armed at every tick end unless a repeating keyboard attack key is held. / 每刻末尾解除闸门，除非按住会重复的键盘攻击键。 */
    private static boolean armed;

    private WitherGoheiFireInput() {
    }

    public static boolean holdsGohei(ClientPlayerEntity player) {
        return WitherGoheiInventoryRules.isGohei(player.getMainHandStack());
    }

    /** {@code doAttack} HEAD: true swallows the attack. Never fires. / {@code doAttack} HEAD：返回 true 表示吞掉攻击，从不射击。 */
    public static boolean onAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && holdsGohei(player);
    }

    /** A drained attack press: the only fire path. / 被取出的攻击按键：唯一的射击路径。 */
    public static void onPressEdge(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !holdsGohei(player) || !armed) {
            return;
        }
        armed = false;
        requestFire(client, player);
    }

    /** {@code handleBlockBreaking} HEAD: true cancels it. / {@code handleBlockBreaking} HEAD：返回 true 表示取消。 */
    public static boolean onBlockBreaking(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && holdsGohei(player);
    }

    /** macOS only: keeps Ctrl + left-click an attack while the Gohei is held. / 仅 macOS：手持御币时 Ctrl + 左键仍是攻击。 */
    public static boolean keepsMacLeftPress(MinecraftClient client, int button, int action,
                                            int pendingRemappedReleases) {
        ClientPlayerEntity player = client.player;
        return MacControlClickRules.keepsLeftPress(
                button == GLFW.GLFW_MOUSE_BUTTON_LEFT,
                action == GLFW.GLFW_PRESS,
                client.currentScreen != null,
                player != null && holdsGohei(player),
                pendingRemappedReleases);
    }

    /** End of every client tick. / 每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        armed = !repeatingAttackKeyHeld(client);
    }

    public static void reset() {
        armed = false;
    }

    private static boolean repeatingAttackKeyHeld(MinecraftClient client) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(client.options.attackKey);
        return switch (key.getCategory()) {
            case KEYSYM -> key.getCode() != InputUtil.UNKNOWN_KEY.getCode()
                    && InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode());
            case SCANCODE -> client.options.attackKey.isPressed();
            case MOUSE -> false;
        };
    }

    private static void requestFire(MinecraftClient client, ClientPlayerEntity player) {
        if (client.currentScreen != null || player.isSpectator() || client.getCameraEntity() != player
                || player.getItemCooldownManager().isCoolingDown(player.getMainHandStack().getItem())
                || !SparkWitchServerConnection.isConfirmedServer() || client.getNetworkHandler() == null
                || !ClientPlayNetworking.canSend(FireCrimsonSkullC2SPayload.ID)) {
            return;
        }
        // The server fires only with the Gohei in the main hand: flush a hotbar change made earlier this tick first.
        // 服务端仅在主手持御币时射击：先同步本刻早先的快捷栏变化。
        if (client.interactionManager != null) {
            ((SelectedSlotFlushInvoker) client.interactionManager).sparkwitch$flushSelectedSlot();
        }
        ClientPlayNetworking.send(new FireCrimsonSkullC2SPayload(player.getYaw(), player.getPitch()));
    }
}
