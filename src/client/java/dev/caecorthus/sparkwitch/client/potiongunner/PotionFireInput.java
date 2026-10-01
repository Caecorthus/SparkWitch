package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.net.FirePotionLauncherC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Left-click bridge for the launcher (scoped or not). Client-only intent: it sends
 * {@link FirePotionLauncherC2SPacket} with the crosshair at press time and the server validates everything. While the
 * main hand holds the launcher, attack presses never attack, mine or swing. Three input paths share one latch:
 * {@code doAttack} (not using an item), the press vanilla drains while an item is in use (scoped), and held-attack
 * block breaking (cancelled always, fires only through an open latch). The Seeker remote view and the Control Expert
 * stun already read the attack key as released, so they win before any of these paths sees a press.
 * 炮筒的左键桥接（开镜与否均可）。仅为客户端意图：携带按下时的准星朝向发送 {@link FirePotionLauncherC2SPacket}，
 * 由服务端复核一切。主手持炮筒时，攻击键永远不会攻击、挖掘或挥动。三条输入路径共用一个闩锁：{@code doAttack}
 * （未使用物品时）、使用物品期间（开镜）原版丢弃的按键，以及按住攻击的方块挖掘（始终取消，仅在闩锁打开时发射）。
 * 搜寻者遥控视角与控场专家眩晕已让攻击键读作未按下，因此它们先于这些路径生效。
 */
public final class PotionFireInput {
    private static final PotionFireLatch LATCH = new PotionFireLatch();

    private PotionFireInput() {
    }

    /** {@code doAttack} HEAD: true swallows the attack (and returns false to vanilla). / 返回 true 表示吞掉本次攻击。 */
    public static boolean onAttack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !PotionScopeClient.holdsLauncherInMainHand(player)) {
            return false;
        }
        requestFire(client, player);
        return true;
    }

    /**
     * A press that vanilla's item-in-use branch of {@code handleInputEvents} just drained (it never reaches
     * {@code doAttack} while scoped).
     * {@code handleInputEvents} 中“正在使用物品”分支刚丢弃的按键（开镜时它不会进入 {@code doAttack}）。
     */
    public static void onPressDrainedWhileUsing(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && player.isUsingItem() && PotionScopeClient.holdsLauncherInMainHand(player)) {
            requestFire(client, player);
        }
    }

    /** {@code handleBlockBreaking} HEAD: true cancels it. / 返回 true 表示取消本次方块挖掘处理。 */
    public static boolean onBlockBreaking(MinecraftClient client, boolean breaking) {
        ClientPlayerEntity player = client.player;
        if (player == null || !PotionScopeClient.holdsLauncherInMainHand(player)) {
            return false;
        }
        if (breaking) {
            requestFire(client, player);
        }
        return true;
    }

    /** End of every client tick: reopen the latch once the attack key is released. / 每个客户端刻末尾：攻击键松开后重新打开闩锁。 */
    public static void tick(MinecraftClient client) {
        LATCH.onTick(client.options != null && client.options.attackKey.isPressed());
    }

    public static void reset() {
        LATCH.reset();
    }

    private static void requestFire(MinecraftClient client, ClientPlayerEntity player) {
        if (!LATCH.tryConsume()) {
            return;
        }
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
        // Rotation at press time: the attack key is handled before this tick's rotation packet (Death Ray precedent).
        // 按下瞬间的朝向：攻击键先于本刻的朝向数据包处理（参照死亡射线）。
        ClientPlayNetworking.send(new FirePotionLauncherC2SPacket(player.getYaw(), player.getPitch()));
    }
}
