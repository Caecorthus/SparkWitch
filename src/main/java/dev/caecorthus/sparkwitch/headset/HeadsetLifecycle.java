package dev.caecorthus.sparkwitch.headset;

import dev.doctor4t.wathe.api.event.ResetPlayer;

/**
 * Headset-slot round edges, registered once from {@code SparkWitchEvents}. Wathe's {@code ResetPlayer} (fired from
 * its round initialisation and player reset) empties only {@code PlayerInventory}, so the headset slot is emptied here
 * too: nothing worn there outlives a round. Server only.
 * 耳机槽的回合节点，由 {@code SparkWitchEvents} 注册一次。Wathe 的 {@code ResetPlayer}（在其回合初始化与玩家重置时触发）只清空
 * {@code PlayerInventory}，因此这里同时清空耳机槽：戴在其中的东西不会延续到下一回合。仅服务端。
 */
public final class HeadsetLifecycle {
    private static boolean registered;

    private HeadsetLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ResetPlayer.EVENT.register(player -> HeadsetSlots.set(player, null));
    }
}
