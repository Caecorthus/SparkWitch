package dev.caecorthus.sparkwitch.client.dominator;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/**
 * Client half of the forced-shot inventory lock: close an open inventory when the packet arrives and refuse to open
 * one for the remaining ticks. Reads only the local timer, so it also clears on disconnect.
 * 强制开枪物品栏锁的客户端部分：收到数据包时关闭已打开的物品栏，并在剩余 tick 内拒绝再次打开。只读取本地计时，
 * 断开连接时也会清空。
 */
public final class DominatorForcedShotClientState {
    private static int lockTicks;

    private DominatorForcedShotClientState() {
    }

    public static void lock(int ticks) {
        lockTicks = Math.max(lockTicks, Math.max(0, ticks));
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        if (screen instanceof InventoryScreen || screen instanceof LimitedInventoryScreen) {
            client.setScreen(null);
        }
    }

    public static void tick() {
        if (lockTicks > 0) {
            lockTicks--;
        }
    }

    public static boolean blocks(Screen screen) {
        return lockTicks > 0
                && (screen instanceof InventoryScreen || screen instanceof LimitedInventoryScreen);
    }

    public static void reset() {
        lockTicks = 0;
    }
}
