package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.FireUsecRifleC2SPacket;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

/**
 * Left-click bridge for the USEC rifle (scoped or hip fire, Q15). Client-only intent: it sends
 * {@link FireUsecRifleC2SPacket} with the crosshair at press time and the server validates everything (D3). The
 * layer is a copy of the launcher's ({@code PotionFireInput}): while the main hand holds the rifle, attack presses
 * never attack, mine or swing; exactly one path fires, an attack press edge that vanilla drains in
 * {@code handleInputEvents} in either branch (the item-in-use drain while scoped, or the {@code doAttack} loop),
 * through {@link UsecFireLatch}. It is not gated on {@code isUsingItem()}, because vanilla's in-use branch runs
 * {@code stopUsingItem} before it drains the press. A held key never fires: a mouse button makes no edges while held,
 * and the latch ignores a keyboard key's auto-repeat edges. The Seeker remote view, the Control Expert stun and the
 * Kidnapper control make the attack key read as released and consume its queued presses, so they win first.
 * USEC 狙击步枪的左键桥接（开镜或腰射，Q15）。仅为客户端意图：携带按下时的准星朝向发送 {@link FireUsecRifleC2SPacket}，
 * 由服务端复核一切（D3）。该层复制自炮筒（{@code PotionFireInput}）：主手持步枪时攻击键永远不会攻击、挖掘或挥动；只有一条
 * 路径会发射，即原版在 {@code handleInputEvents} 两个分支之一（开镜时的使用中丢弃循环，或 {@code doAttack} 循环）中取出的
 * 攻击按下沿，经 {@link UsecFireLatch} 判定。它不以 {@code isUsingItem()} 为条件，因为原版使用中分支会先执行
 * {@code stopUsingItem} 再取出按键。按住不放永不发射：按住鼠标键不产生按下沿，闩锁忽略键盘自动重复。搜寻者遥控视角、
 * 控场专家眩晕与绑匪控制会让攻击键读作未按下并消耗排队按键，因此它们先生效。
 */
public final class UsecFireInput {
    private static final UsecFireLatch LATCH = new UsecFireLatch();

    private UsecFireInput() {
    }

    /**
     * {@code doAttack} HEAD: true swallows the attack (vanilla gets false). It never fires; the press that led here was
     * already seen as an edge by {@link #onPressEdge}.
     * {@code doAttack} HEAD：返回 true 表示吞掉本次攻击。它从不发射：引出此调用的按键已由 {@link #onPressEdge} 处理。
     */
    public static boolean onAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && UsecRifleClient.holdsRifleInMainHand(player);
    }

    /**
     * An attack press that vanilla's {@code handleInputEvents} just drained, in either branch. The only fire path.
     * 原版 {@code handleInputEvents} 刚在任一分支取出的攻击按键。唯一的发射路径。
     */
    public static void onPressEdge(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && LATCH.onPressEdge(UsecRifleClient.holdsRifleInMainHand(player))) {
            requestFire(client, player);
        }
    }

    /**
     * {@code handleBlockBreaking} HEAD: true cancels it (no mining or swinging). Never fires.
     * {@code handleBlockBreaking} HEAD：返回 true 表示取消（不挖掘、不挥动）。从不发射。
     */
    public static boolean onBlockBreaking(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && UsecRifleClient.holdsRifleInMainHand(player);
    }

    /** End of every client tick on a confirmed server. / 已确认服务器上的每个客户端刻末尾。 */
    public static void tick(MinecraftClient client) {
        LATCH.endTick(repeatingAttackKeyHeld(client));
    }

    public static void reset() {
        LATCH.reset();
    }

    /**
     * True when the attack key is bound to a keyboard key that is physically down (GLFW state, which no key lock or
     * screen hides). Mouse buttons never repeat; a scancode-only key falls back to {@code isPressed()}.
     * 攻击键绑定在键盘键上且该键物理按下时为 true（GLFW 状态，任何按键锁或界面都无法隐藏）。鼠标键从不重复；仅有扫描码的
     * 按键退回 {@code isPressed()}。
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
        ItemStack rifle = player.getMainHandStack();
        boolean canSend = SparkWitchServerConnection.isConfirmedServer()
                && client.getNetworkHandler() != null
                && ClientPlayNetworking.canSend(FireUsecRifleC2SPacket.ID);
        if (!UsecInputRules.sendsFire(
                client.currentScreen != null,
                player.isSpectator(),
                client.getCameraEntity() == player,
                player.getItemCooldownManager().isCoolingDown(rifle.getItem()),
                canSend)) {
            return;
        }
        // Rotation at press time: the attack key is handled before this tick's rotation packet (Death Ray precedent).
        // 按下瞬间的朝向：攻击键先于本刻的朝向数据包处理（参照死亡射线）。
        ClientPlayNetworking.send(new FireUsecRifleC2SPacket(player.getYaw(), player.getPitch()));
        if (UsecInputRules.kicksOnFire(true, UsecRifleState.read(rifle).chamber() != null)) {
            UsecRecoil.onShot();
        }
    }

    /** FOV multiplier the recoil scales with: the USEC profile's while scoped, else 1. / 后坐缩放所用的视野倍率。 */
    public static float recoilFovMultiplier() {
        return UsecScopeProfile.isActive() ? UsecZoomState.fovMultiplier() : 1.0F;
    }
}
