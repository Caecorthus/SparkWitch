package dev.caecorthus.sparkwitch.headset;

import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Headset-slot round edges, registered once from {@code SparkWitchEvents}. Wathe's {@code ResetPlayer} (fired from
 * its round initialisation and player reset) empties only {@code PlayerInventory}, so the headset slot is emptied here
 * too: nothing worn there outlives a round. Wathe's join-time wipe ({@code GameFunctions.teleportPlayer}) fires no
 * event, so {@code mixin.bear.HeadsetWatheInventoryWipeMixin} calls {@link #onWatheInventoryWipe} right after that
 * wipe's own {@code PlayerInventory.clear()}. Server only.
 * 耳机槽的回合节点，由 {@code SparkWitchEvents} 注册一次。Wathe 的 {@code ResetPlayer}（在其回合初始化与玩家重置时触发）只清空
 * {@code PlayerInventory}，因此这里同时清空耳机槽：戴在其中的东西不会延续到下一回合。Wathe 的进服清空
 * （{@code GameFunctions.teleportPlayer}）不触发任何事件，因此由 {@code mixin.bear.HeadsetWatheInventoryWipeMixin} 紧跟在该
 * 清空自身的 {@code PlayerInventory.clear()} 之后调用 {@link #onWatheInventoryWipe}。仅服务端。
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

    /**
     * Wathe just emptied {@code player}'s {@code PlayerInventory} in its join-time teleport (a rejoin after a server
     * crash, a spectator or creative join into a running round, the post-vote move): the headset slot follows, whatever
     * it holds (earplugs or ComTac VIII), exactly like the reset above. Never reached by a living survival player who
     * reconnects into a running round. Server thread.
     * Wathe 刚在其进服传送中清空了 {@code player} 的 {@code PlayerInventory}（服务器崩溃后重进、以旁观/创造模式进入进行中的
     * 对局、投票后的转移）：耳机槽随之清空，无论其中是耳塞还是 ComTac VIII，与上面的重置完全一致。重连进入进行中对局的存活
     * 生存玩家不会到达这里。服务端线程。
     */
    public static void onWatheInventoryWipe(ServerPlayerEntity player) {
        HeadsetSlots.set(player, null);
    }
}
