package dev.caecorthus.sparkwitch.client.dominator;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

/**
 * Client half of the forced-shot inventory lock: close an open inventory or container screen (any
 * {@link HandledScreen}, Wathe's limited inventory included) when the packet arrives and refuse to open one for the
 * remaining ticks. Presentation only: the server refuses container screens itself
 * ({@code ServerPlayerEntityForcedShotScreenMixin}). Reads only the local timer, so it also clears on disconnect.
 * 强制开枪物品栏锁的客户端部分：收到数据包时关闭已打开的物品栏或容器界面（任意 {@link HandledScreen}，含 Wathe 的受限物品栏），
 * 并在剩余 tick 内拒绝再次打开。仅负责表现：服务端自行拒绝容器界面（{@code ServerPlayerEntityForcedShotScreenMixin}）。
 * 只读取本地计时，断开连接时也会清空。
 */
public final class DominatorForcedShotClientState {
    private static int lockTicks;

    private DominatorForcedShotClientState() {
    }

    public static void lock(int ticks) {
        lockTicks = Math.max(lockTicks, Math.max(0, ticks));
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        if (!isLockedScreen(screen)) {
            return;
        }
        if (client.player != null && screen instanceof HandledScreen<?>) {
            // Sends the close packet too, so the server-side handler (and a crafting grid) is closed with it.
            // 同时发送关闭包，使服务端界面（及合成格）一并关闭。
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }
    }

    /**
     * A container screen the server opened anyway (the client lock outlasts the server sequence by the latency) is
     * refused here and closed on the server too, so the two never disagree about the open handler.
     * 服务端仍打开了的容器界面（客户端锁比服务端序列多出一段延迟）在此被拒绝，并同时在服务端关闭，使两端对打开的界面始终一致。
     */
    public static void refuse(Screen screen) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && screen instanceof HandledScreen<?> handled
                && handled.getScreenHandler() != player.playerScreenHandler) {
            player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(handled.getScreenHandler().syncId));
            player.currentScreenHandler = player.playerScreenHandler;
        }
    }

    public static void tick() {
        if (lockTicks > 0) {
            lockTicks--;
        }
    }

    public static boolean blocks(Screen screen) {
        return lockTicks > 0 && isLockedScreen(screen);
    }

    private static boolean isLockedScreen(Screen screen) {
        return screen instanceof HandledScreen<?> || screen instanceof LimitedInventoryScreen;
    }

    public static void reset() {
        lockTicks = 0;
    }
}
