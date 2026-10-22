package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only. Wiring of {@link UsecBoltWatch}, registered once, last, by {@code UsecClientModule}: one end-of-tick
 * listener feeds the local player's carried rifle states and synced rifle cooldown to the tracker, and a disconnect or
 * a new player entity (a new cooldown clock) resets it. {@link #status} is the one rifle cooldown read of the ammo HUD,
 * the attachment screen and the bolt sway; it asks live, so the listener's place in the tick order matters at most one
 * tick (the bolt sway, registered earlier, sees a change counted one clock tick later inside the window).
 * Presentation only: nothing is sent or synced, and it never touches the witch skill inventory panel.
 * 仅客户端。{@link UsecBoltWatch} 的接线，由 {@code UsecClientModule} 最后注册一次：一个刻末监听器把本地玩家携带的步枪状态与
 * 已同步的步枪冷却喂给追踪器；断线或新的玩家实体（新的冷却时钟）会重置它。{@link #status} 是弹药 HUD、配件界面与拉栓晃动
 * 唯一的步枪冷却读取；它实时查询，因此监听器在刻内的顺序最多影响一刻（先注册的拉栓晃动会在窗口内晚一个时钟刻计入变化）。仅为表现：不发送也不同步任何内容，从不触及魔女技能背包面板。
 */
public final class UsecBoltWatchClient {
    private static final UsecBoltWatch.Tracker TRACKER = new UsecBoltWatch.Tracker();
    private static @Nullable PlayerEntity watched;
    // True once the tracker was ticked since its last reset and every one of those ticks carried no rifle: it then holds
    // the empty baseline with no recorded change and no latch, which another rifle-less tick leaves exactly as it is, so
    // that tick is skipped (no list, no multiset) for the many players who never carry a rifle.
    // 自上次重置后追踪器已推进过、且每一刻都没有携带步枪时为真：此时它保持空基准、没有记录的变化也没有锁存，再推进一个
    // 没有步枪的刻不会改变其任何状态，因此跳过该刻（不建列表、不建多重集），惠及从不携带步枪的大多数玩家。
    private static boolean idleEmptyBaseline;
    private static boolean trackerTickedSinceReset;
    private static boolean registered;

    private UsecBoltWatchClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(UsecBoltWatchClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /**
     * The player's rifle cooldown, with {@code bolt} only for an entry the watch has evidence for. Only the local
     * player is watched; anyone else reads as a lock.
     * 玩家的步枪冷却；只有观察器有证据的条目才标记为 {@code bolt}。只观察本地玩家，其他人一律视为锁定。
     */
    public static UsecCooldowns.Status status(@Nullable PlayerEntity player) {
        UsecCooldowns.Reading reading = UsecCooldowns.read(player);
        if (reading == null || !reading.hasEntry()) {
            return UsecCooldowns.Status.NONE;
        }
        boolean bolt = player == watched && TRACKER.isBolt(reading.tick(), rifles(player), reading.startTick());
        return reading.status(bolt);
    }

    private static void tick(MinecraftClient client) {
        PlayerEntity player = client.player;
        UsecCooldowns.Reading reading = UsecCooldowns.read(player);
        if (player == null || reading == null) {
            reset();
            return;
        }
        if (player != watched) {
            // Respawn and dimension changes make a new player with a new cooldown clock. / 重生与换维度会产生新的冷却时钟。
            TRACKER.reset();
            idleEmptyBaseline = false;
            trackerTickedSinceReset = false;
            watched = player;
        }
        List<UsecRifleState> rifles = rifles(player);
        if (idleEmptyBaseline && rifles.isEmpty()) {
            return;
        }
        // This tick's cooldown update (PlayerEntity.tick) already ran, and packets are handled between ticks, so what
        // is seen here was handled one clock tick earlier: where an entry created with it starts. (A paused
        // singleplayer game or an unloaded chunk skips that update; one tick off, well inside the window.)
        // 本刻的冷却更新（PlayerEntity.tick）已执行，而数据包在两刻之间处理，因此此处所见的内容是在早一个时钟刻处理的，即随之
        // 创建的条目的起点。（单人游戏暂停或区块未加载时会跳过该更新；偏差一刻，远在窗口之内。）
        TRACKER.tick(reading.tick() - 1, rifles, reading.startTick());
        // Set by an empty first tick after a reset, kept by empty ticks, and lost for good with the first rifle.
        // 由重置后的首个空刻置位，空刻保持，出现第一把步枪后即永久失效（直到下次重置）。
        idleEmptyBaseline = rifles.isEmpty() && (idleEmptyBaseline || !trackerTickedSinceReset);
        trackerTickedSinceReset = true;
    }

    private static void reset() {
        TRACKER.reset();
        idleEmptyBaseline = false;
        trackerTickedSinceReset = false;
        watched = null;
    }

    /**
     * The synced state of every rifle the player carries: main inventory, offhand and the cursor (so picking the
     * rifle up to move it is no change).
     * 玩家携带的每把步枪的同步状态：主背包、副手与光标（因此拿起步枪移动不算变化）。
     */
    private static List<UsecRifleState> rifles(PlayerEntity player) {
        // Read-only for its callers; the shared empty list spares the common rifle-less case an allocation.
        // 调用方只读；无步枪的常见情形使用共享空列表，免去一次分配。
        List<UsecRifleState> rifles = List.of();
        PlayerInventory inventory = player.getInventory();
        rifles = addRifles(rifles, inventory.main);
        rifles = addRifles(rifles, inventory.offHand);
        if (player.currentScreenHandler != null) {
            rifles = addRifle(rifles, player.currentScreenHandler.getCursorStack());
        }
        return rifles;
    }

    private static List<UsecRifleState> addRifles(List<UsecRifleState> rifles, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            rifles = addRifle(rifles, stack);
        }
        return rifles;
    }

    private static List<UsecRifleState> addRifle(List<UsecRifleState> rifles, ItemStack stack) {
        if (!UsecRifleClient.isRifle(stack)) {
            return rifles;
        }
        if (rifles.isEmpty()) {
            rifles = new ArrayList<>(1);
        }
        rifles.add(UsecRifleState.read(stack));
        return rifles;
    }
}
