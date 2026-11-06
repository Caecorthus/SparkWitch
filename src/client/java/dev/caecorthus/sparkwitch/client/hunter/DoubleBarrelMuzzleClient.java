package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Installs the double-barrel shotgun's client presentation: the local first-person hand flash and its tick. World
 * flashes for the shooter in third person and for nearby observers ride Wathe's existing muzzle packet, sent by the
 * shotgun's server path; the held-item mixin stores the muzzle position for it.
 * 安装双管猎枪的客户端表现：本地第一人称手部火焰及其推进。射手第三人称与附近观察者的世界火焰沿用 Wathe 现有的
 * 枪口数据包（由猎枪服务端路径发送），手持物品 mixin 会为其写入枪口位置。
 */
public final class DoubleBarrelMuzzleClient {
    private DoubleBarrelMuzzleClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> DoubleBarrelMuzzleFlash.tick());
        DoubleBarrelShotgunItem.installClientShot((player, stack) -> DoubleBarrelMuzzleFlash.spawn());
    }
}
