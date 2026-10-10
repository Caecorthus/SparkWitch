package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.input.MacControlClickRules;
import dev.caecorthus.sparkwitch.client.mixin.input.SelectedSlotFlushInvoker;
import dev.caecorthus.sparkwitch.net.FirePotionLauncherC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Left-click bridge for the launcher (scoped or not). Client-only intent: it sends
 * {@link FirePotionLauncherC2SPacket} with the crosshair at press time and the server validates everything. While the
 * main hand holds the launcher, attack presses never attack, mine or swing. Exactly one path fires: an attack press
 * edge that vanilla drains in {@code handleInputEvents}, in either branch (the item-in-use drain, or the
 * {@code doAttack} loop), through {@link PotionFireLatch}. It is not gated on {@code isUsingItem()}, because
 * vanilla's in-use branch runs {@code stopUsingItem} before it drains the press, so releasing right-click and
 * clicking left in the same tick still fires. {@code doAttack} only swallows, and held-attack block breaking is only
 * cancelled. A held key never fires (D-R3): a mouse button makes no edges while held, and the latch ignores a
 * keyboard key's auto-repeat edges until a tick end sees it physically up. The Seeker remote view, the Control Expert
 * stun and the Kidnapper control make the attack key read as released and consume its queued presses, so they win
 * before this path sees an edge. Two input gaps are closed here (2026-10-09, AXMC parity): on macOS a Ctrl + left
 * press with the launcher stays a left press ({@link #keepsMacLeftPress}), and a fire request first flushes a hotbar
 * slot picked earlier in the same tick.
 * 炮筒的左键桥接（开镜与否均可）。仅为客户端意图：携带按下时的准星朝向发送 {@link FirePotionLauncherC2SPacket}，
 * 由服务端复核一切。主手持炮筒时，攻击键永远不会攻击、挖掘或挥动。只有一条路径会发射：原版在
 * {@code handleInputEvents} 两个分支之一（使用物品时的丢弃循环，或 {@code doAttack} 循环）中取出的攻击按下沿，经
 * {@link PotionFireLatch} 判定。它不以 {@code isUsingItem()} 为条件，因为原版的使用中分支会先执行
 * {@code stopUsingItem} 再取出按键，所以同一刻松开右键并点击左键仍会发射。{@code doAttack} 只吞掉攻击，按住攻击的
 * 方块挖掘只会被取消。按住不放永远不会发射（D-R3）：按住鼠标键不产生按下沿，而键盘键的自动重复按下沿会被闩锁忽略，
 * 直到某个刻末尾观察到该键物理松开。搜寻者遥控视角、控场专家眩晕与绑匪控制会让攻击键读作未按下并消耗其排队按键，
 * 因此它们先于这条路径生效。此处补上两个输入缺口（2026-10-09，与 AXMC 一致）：macOS 上持炮筒时 Ctrl + 左键按下仍是左键
 * （{@link #keepsMacLeftPress}），发射请求会先同步同一刻早先选中的快捷栏位。
 */
public final class PotionFireInput {
    private static final PotionFireLatch LATCH = new PotionFireLatch();

    private PotionFireInput() {
    }

    /**
     * {@code doAttack} HEAD: true swallows the attack (and returns false to vanilla). It never fires: the press that
     * led here was already seen as an edge by {@link #onPressEdge}.
     * {@code doAttack} HEAD：返回 true 表示吞掉本次攻击。它从不发射：引出此调用的按键已由 {@link #onPressEdge} 作为
     * 按下沿处理。
     */
    public static boolean onAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && PotionScopeClient.holdsLauncherInMainHand(player);
    }

    /**
     * An attack press that vanilla's {@code handleInputEvents} just drained, in either branch. The only fire path.
     * 原版 {@code handleInputEvents} 刚在任一分支取出的攻击按键。唯一的发射路径。
     */
    public static void onPressEdge(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && LATCH.onPressEdge(PotionScopeClient.holdsLauncherInMainHand(player))) {
            requestFire(client, player);
        }
    }

    /**
     * {@code Mouse.onMouseButton}, at its only {@code IS_SYSTEM_MAC} read (macOS only): true keeps this left press as
     * button 0, uncounted, so Ctrl + left-click (sprint is Left Ctrl) reaches the attack key and fires instead of
     * opening the scope. The launcher test is the fire path's own main-hand check. Never fires by itself and never
     * touches a release; see {@link MacControlClickRules#keepsLeftPress}.
     * {@code Mouse.onMouseButton} 中唯一一次读取 {@code IS_SYSTEM_MAC} 处（仅 macOS）：返回 true 时本次左键按下保持为按键 0
     * 且不计数，于是 Ctrl + 左键（疾跑是左 Ctrl）会到达攻击键并发射，而不是开镜。炮筒判断与发射路径使用同一主手检查。它自身
     * 从不发射，也从不改动松开；见 {@link MacControlClickRules#keepsLeftPress}。
     */
    public static boolean keepsMacLeftPress(MinecraftClient client, int button, int action,
                                            int pendingRemappedReleases) {
        ClientPlayerEntity player = client.player;
        return MacControlClickRules.keepsLeftPress(
                button == GLFW.GLFW_MOUSE_BUTTON_LEFT,
                action == GLFW.GLFW_PRESS,
                client.currentScreen != null,
                player != null && PotionScopeClient.holdsLauncherInMainHand(player),
                pendingRemappedReleases);
    }

    /**
     * {@code handleBlockBreaking} HEAD: true cancels it (no mining or swinging). Never fires, whatever the key state.
     * {@code handleBlockBreaking} HEAD：返回 true 表示取消（不挖掘、不挥动）。无论按键状态如何都不发射。
     */
    public static boolean onBlockBreaking(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && PotionScopeClient.holdsLauncherInMainHand(player);
    }

    /** End of every client tick. / 每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        LATCH.endTick(repeatingAttackKeyHeld(client));
    }

    public static void reset() {
        LATCH.reset();
    }

    /**
     * True when the attack key is bound to a keyboard key that is physically down. Keyboard keys auto-repeat while held
     * and every repeat queues a press. GLFW reports the physical state, which no key lock (they hook
     * {@code isPressed}), screen ({@code unpressAll}) or SparkTraits Last Escape ({@code setPressed(false)}) hides.
     * Mouse buttons never repeat, so they answer false. GLFW cannot poll a scancode-only key, which falls back to
     * {@code isPressed()}.
     * 攻击键绑定在键盘键上且该键物理按下时为 true。键盘键按住时会自动重复，每次重复都会排入一次按键。GLFW 报告物理
     * 状态，任何按键锁（它们钩住 {@code isPressed}）、界面（{@code unpressAll}）或 SparkTraits 最后逃亡
     * （{@code setPressed(false)}）都无法隐藏它。鼠标键从不重复，因此返回 false。GLFW 无法查询仅有扫描码的按键，
     * 此时退回 {@code isPressed()}。
     */
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
                && ClientPlayNetworking.canSend(FirePotionLauncherC2SPacket.ID);
        if (!PotionScopeRules.sendsFire(
                client.currentScreen != null,
                player.isSpectator(),
                client.getCameraEntity() == player,
                player.getItemCooldownManager().isCoolingDown(player.getMainHandStack().getItem()),
                canSend)) {
            return;
        }
        // The server fires only with the launcher in its main hand, but a hotbar key handled earlier in this tick changed
        // only the client's slot (vanilla sends it at the next tick), so the shot was refused as not holding. Flush it
        // first, as interactItem does, so the server reads the slot before the shot. It sends nothing when unchanged.
        // 服务端仅在其主手持炮筒时发射，但本刻早先处理的快捷栏按键只改变了客户端栏位（原版在下一刻才发送），发射因此被判为
        // 未持炮筒。像 interactItem 一样先同步，服务端便会先读到栏位再处理发射。栏位未变时不发送任何内容。
        if (client.interactionManager != null) {
            ((SelectedSlotFlushInvoker) client.interactionManager).sparkwitch$flushSelectedSlot();
        }
        // Rotation at press time: the attack key is handled before this tick's rotation packet (Death Ray precedent).
        // 按下瞬间的朝向：攻击键先于本刻的朝向数据包处理（参照死亡射线）。
        ClientPlayNetworking.send(new FirePotionLauncherC2SPacket(player.getYaw(), player.getPitch()));
    }
}
