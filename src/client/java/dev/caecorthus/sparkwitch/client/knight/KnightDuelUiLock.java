package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/**
 * Duel UI lock: while the synced duel variable is 1 for the local player, the inventory (and the skills/shop panel
 * inside it) cannot be opened, and the lock packet closes one that is already open. Enforcement reads the synced
 * {@link KnightDuelWorldComponent}, so pressing E works again the moment the duel ends, even if a packet was missed.
 * 决斗界面锁：本地玩家的同步决斗变量为 1 时不能打开物品栏（以及其中的技能/商店面板），锁定包会关闭已打开的界面。执行
 * 读取同步的 {@link KnightDuelWorldComponent}，因此决斗一结束按 E 即恢复，即使漏收数据包也不会卡死。
 */
public final class KnightDuelUiLock {
    private KnightDuelUiLock() {
    }

    /** Whether this open request is the inventory/skill panel of an active duelist. / 该打开请求是否属于进行中决斗者的物品栏/技能面板。 */
    public static boolean blocks(Screen screen) {
        if (!(screen instanceof InventoryScreen) && !(screen instanceof LimitedInventoryScreen)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return false;
        }
        return KnightDuelWorldComponent.KEY.get(client.world).isDuelist(client.player.getUuid());
    }

    /** Closes an inventory/skill panel that was already open when the duel started. / 关闭决斗开始时已打开的物品栏/技能面板。 */
    public static void closeOpen(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen instanceof InventoryScreen || screen instanceof LimitedInventoryScreen) {
            client.setScreen(null);
        }
    }
}
