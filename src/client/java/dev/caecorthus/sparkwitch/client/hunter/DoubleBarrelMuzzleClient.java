package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;

/**
 * The double-barrel shotgun's client muzzle flashes. The shooter's first-person hand flash is predicted on the
 * client branch of {@code use} like the revolver's, then checked against the server's muzzle packet sent to the
 * shooter, so a shot the client did not predict (the held-trigger repeat) still flashes, once. Observers, and the
 * shooter in third person, get Wathe's world flash from that same packet at the barrel position Wathe's
 * {@code wathe:guns} held-item hook records.
 * 双管猎枪的客户端枪口火焰。射手的第一人称手部火焰与左轮一样在 {@code use} 客户端分支预测，再用服务端发给射手的枪口数据包
 * 核对，因此客户端未预测的一枪（按住扳机的重复使用）仍会恰好闪一次。观察者与第三人称射手则由同一数据包得到 Wathe 的世界
 * 火焰，位置是 Wathe {@code wathe:guns} 手持物品钩子记录的枪管位置。
 */
public final class DoubleBarrelMuzzleClient {
    private static final DoubleBarrelMuzzlePredictions PREDICTIONS = new DoubleBarrelMuzzlePredictions();
    private static long clientTicks;

    private DoubleBarrelMuzzleClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTicks++;
            PREDICTIONS.expire(clientTicks);
            DoubleBarrelMuzzleFlash.tick();
        });
        DoubleBarrelShotgunItem.installClientShot((player, stack) -> {
            DoubleBarrelMuzzleFlash.spawn();
            PREDICTIONS.predicted(clientTicks);
        });
    }

    /**
     * Connection edges: drops pending predictions and flashes on the client thread (login events run on the network
     * thread). / 连接节点：在客户端线程丢弃待确认预测与火焰（登录事件在网络线程触发）。
     */
    public static void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(DoubleBarrelMuzzleClient::reset);
            return;
        }
        PREDICTIONS.clear();
        DoubleBarrelMuzzleFlash.reset();
    }

    /**
     * Client thread, for every Wathe muzzle packet (from {@code DoubleBarrelOwnMuzzlePacketMixin}). Only the local
     * player's own shotgun shots count: Wathe's revolver family sends the same packet from the main hand and keeps
     * Wathe's own flash, so a packet counts only while the shotgun is in the main hand, or in the off hand with no other
     * {@code wathe:guns} item in the main hand (a main-hand gun's use consumes the click, so the off hand never fires).
     * 客户端线程，每个 Wathe 枪口数据包都会调用（来自 {@code DoubleBarrelOwnMuzzlePacketMixin}）。只统计本地玩家自己的猎枪
     * 射击：Wathe 左轮系从主手发送同一数据包并保留 Wathe 自己的火焰，因此只有猎枪在主手，或猎枪在副手且主手不是其他
     * {@code wathe:guns} 物品时才计数（主手枪械的使用会吃掉这次点击，副手不会开火）。
     */
    public static void onMuzzlePacket(String shooterId) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !player.getUuidAsString().equals(shooterId)) {
            return;
        }
        ItemStack mainHand = player.getMainHandStack();
        boolean shotgunShot = mainHand.isOf(SparkWitchItems.doubleBarrelShotgun())
                || (player.getOffHandStack().isOf(SparkWitchItems.doubleBarrelShotgun())
                && !mainHand.isIn(WatheItemTags.GUNS));
        if (!shotgunShot) {
            return;
        }
        if (PREDICTIONS.confirm(clientTicks) && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            DoubleBarrelMuzzleFlash.spawn();
        }
    }
}
