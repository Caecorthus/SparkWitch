package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.client.input.MacControlClickRules;
import dev.caecorthus.sparkwitch.client.mixin.input.SelectedSlotFlushInvoker;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionFireLatch;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.bear.net.FireM32A1C2SPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Left-click bridge for the M32A1 (aiming or not), the anti-tank launcher's scheme ({@code PotionFireInput}) for a
 * different item. Client-only intent: it sends {@link FireM32A1C2SPayload} with the crosshair at press time and the
 * server validates everything. While the main hand holds the M32A1, attack presses never attack, mine or swing.
 * Exactly one path fires: an attack press edge that vanilla drains in {@code handleInputEvents} (either branch),
 * through the role-neutral {@link PotionFireLatch} (a held key never fires; keyboard auto-repeat is ignored until the
 * key is seen physically up). The Seeker remote view, the Control Expert stun and the Kidnapper control make the attack
 * key read as released first. On macOS a Ctrl + left press with the M32A1 stays a left press
 * ({@link #keepsMacLeftPress}), and a fire request first flushes a hotbar slot picked earlier in the same tick.
 * M32A1 的左键桥接（瞄准与否均可），即反坦克炮筒的方案（{@code PotionFireInput}）用于另一件物品。仅为客户端意图：携带按下时的
 * 准星朝向发送 {@link FireM32A1C2SPayload}，由服务端复核一切。主手持 M32A1 时，攻击键永远不会攻击、挖掘或挥动。只有一条路径会
 * 发射：原版在 {@code handleInputEvents}（任一分支）中取出的攻击按下沿，经与职业无关的 {@link PotionFireLatch} 判定（按住不放永不
 * 发射；键盘自动重复在观察到按键物理松开前被忽略）。搜寻者遥控视角、控场专家眩晕与绑匪控制会先让攻击键读作未按下。macOS 上持
 * M32A1 时 Ctrl + 左键按下仍是左键（{@link #keepsMacLeftPress}），发射请求会先同步同一刻早先选中的快捷栏位。
 */
public final class M32FireInput {
    private static final PotionFireLatch LATCH = new PotionFireLatch();

    private M32FireInput() {
    }

    /** {@code doAttack} HEAD: true swallows the attack. Never fires. / 返回 true 表示吞掉本次攻击。从不发射。 */
    public static boolean onAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && M32ClientState.holdsLauncherInMainHand(player);
    }

    /** An attack press vanilla just drained; the only fire path. / 原版刚取出的攻击按键；唯一的发射路径。 */
    public static void onPressEdge(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && LATCH.onPressEdge(M32ClientState.holdsLauncherInMainHand(player))) {
            requestFire(client, player);
        }
    }

    /**
     * {@code Mouse.onMouseButton} at its only {@code IS_SYSTEM_MAC} read (macOS only): true keeps this left press as
     * button 0, so Ctrl + left-click (sprint is Left Ctrl) fires instead of aiming. Never fires by itself; see
     * {@link MacControlClickRules#keepsLeftPress}.
     * {@code Mouse.onMouseButton} 中唯一一次读取 {@code IS_SYSTEM_MAC} 处（仅 macOS）：返回 true 时本次左键按下保持为按键 0，于是
     * Ctrl + 左键（疾跑是左 Ctrl）会发射而不是瞄准。自身从不发射；见 {@link MacControlClickRules#keepsLeftPress}。
     */
    public static boolean keepsMacLeftPress(MinecraftClient client, int button, int action,
                                            int pendingRemappedReleases) {
        ClientPlayerEntity player = client.player;
        return MacControlClickRules.keepsLeftPress(
                button == GLFW.GLFW_MOUSE_BUTTON_LEFT,
                action == GLFW.GLFW_PRESS,
                client.currentScreen != null,
                player != null && M32ClientState.holdsLauncherInMainHand(player),
                pendingRemappedReleases);
    }

    /** {@code handleBlockBreaking} HEAD: true cancels it. Never fires. / 返回 true 表示取消。从不发射。 */
    public static boolean onBlockBreaking(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && M32ClientState.holdsLauncherInMainHand(player);
    }

    /** End of every client tick. / 每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        LATCH.endTick(repeatingAttackKeyHeld(client));
    }

    public static void reset() {
        LATCH.reset();
    }

    /**
     * Whether a drained press edge becomes a fire request; a refused press is still swallowed. Skipping during the
     * synced cooldown only saves a packet; the server re-checks everything.
     * 取出的按下沿是否变成发射请求；被拒绝的按键仍会被吞掉。在已同步的冷却期间跳过只是为了省一个数据包；服务端会复核一切。
     */
    static boolean sendsFire(boolean screenOpen, boolean spectator, boolean cameraIsPlayer, boolean coolingDown,
                             boolean canSend) {
        return !screenOpen && !spectator && cameraIsPlayer && !coolingDown && canSend;
    }

    /** A keyboard-bound attack key that is physically down (it auto-repeats). / 绑定在键盘上且物理按下的攻击键。 */
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
        boolean canSend = SparkWitchServerConnection.isConfirmedServer()
                && client.getNetworkHandler() != null
                && ClientPlayNetworking.canSend(FireM32A1C2SPayload.ID);
        if (!sendsFire(client.currentScreen != null, player.isSpectator(), client.getCameraEntity() == player,
                player.getItemCooldownManager().isCoolingDown(player.getMainHandStack().getItem()), canSend)) {
            return;
        }
        // The server fires only with the M32A1 in its main hand; a hotbar key handled earlier this tick changed only the
        // client's slot, so flush it first (it sends nothing when unchanged). / 服务端仅在主手持 M32A1 时发射；本刻早先处理
        // 的快捷栏按键只改变了客户端栏位，因此先同步（未变时不发送）。
        if (client.interactionManager != null) {
            ((SelectedSlotFlushInvoker) client.interactionManager).sparkwitch$flushSelectedSlot();
        }
        // Rotation at press time: the attack key is handled before this tick's rotation packet.
        // 按下瞬间的朝向：攻击键先于本刻的朝向数据包处理。
        ClientPlayNetworking.send(new FireM32A1C2SPayload(player.getYaw(), player.getPitch()));
    }
}
