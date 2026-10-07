package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * The Blind's loadout: the round-start and mid-round grant (one White Cane plus the initial cane and Attune cooldowns,
 * bound to the match), the per-tick upkeep that keeps exactly one cane in a living Blind's hotbar, and the strip that
 * removes the cane and ComTac from anyone else. Server-authoritative; it never creates item entities and every cane
 * placement is an explicit slot write (Time Stealer Clock pattern, duplicated rather than shared).
 * 盲人装备：开局与中途发放（一根盲杖，加上盲杖与凝神的初始冷却，并绑定对局），每 tick 保证存活盲人的快捷栏恰好有一根
 * 盲杖，以及从其他所有人身上清除盲杖与 ComTac。由服务端裁定；从不生成物品实体，每次放置盲杖都是显式栏位写入
 * （窃时者时钟模式，复制而非共享）。
 */
public final class BlindLoadoutService {
    private BlindLoadoutService() {
    }

    /**
     * Round-start grant from the after-default {@code ON_FINISH_INITIALIZE} phase (still STARTING, so no running-state
     * helper): every final, living Blind gets a fresh grant.
     * 开局发放，在默认阶段之后的 {@code ON_FINISH_INITIALIZE} 阶段执行（仍是 STARTING，因此不使用运行态判定）：
     * 每名最终存活的盲人都获得一次全新发放。
     */
    public static void grantRoundStart(ServerWorld world, GameWorldComponent game) {
        UUID match = BlindParticipants.currentMatchId();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (receivesRoundStartKit(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                grantFresh(player, match);
            }
        }
    }

    static boolean receivesRoundStartKit(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && BlindRules.isBlind(finalRole);
    }

    /**
     * Every RoleAssigned. Losing the role strips the kit and clears the state at once; a playing, alive player who
     * becomes the Blind while the round is ACTIVE (forced role, C11) is granted immediately, keeping cooldowns on a
     * same-match re-assignment (a dead or spectating player gets nothing). Round-start assignments (STARTING) wait for
     * the final-role phase.
     * 每次 RoleAssigned。失去该职业立即清除道具与状态；在 ACTIVE 回合中成为盲人（强制职业，C11）的在局存活玩家立即获得
     * 发放，同局重复分配保留冷却（死亡或旁观的玩家不获得任何东西）。开局分配（STARTING）等待最终身份阶段。
     */
    public static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        if (!BlindRules.isBlind(role)) {
            strip(player);
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)) {
            ensureGranted(player);
        }
    }

    /**
     * Sweep test for a player who is not an active Blind ({@link BlindKitRules#keepsKit}): a real Blind in a running
     * round keeps the kit while alive or inside a SparkTraits-intercepted (Last Stand) death; the intercept query runs
     * only for such a dead Blind and fails closed (an absent, older or failing SparkTraits means not intercepted).
     * 对非激活盲人的玩家的清理判定（{@link BlindKitRules#keepsKit}）：运行中对局里的真实盲人在存活或处于 SparkTraits
     * 拦截的（背水一战）死亡期间保留道具；仅对这样的已死盲人查询拦截状态，且保守失败（SparkTraits 缺失、过旧或出错时视为
     * 未拦截）。
     */
    public static boolean keepsKit(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        boolean running = game.isRunning();
        boolean realBlind = BlindRules.isBlind(game.getRole(player));
        boolean playingAndAlive = GameFunctions.isPlayerPlayingAndAlive(player);
        return BlindKitRules.keepsKit(running, realBlind, playingAndAlive, running && realBlind && !playingAndAlive
                && SparkTraitsKillerBridge.isLastStandDeathIntercepted(player));
    }

    /**
     * Per-tick upkeep for an active Blind: self-heals a missing or stale grant (bound to another match), then keeps
     * exactly one cane in the hotbar, except in psycho mode. Cheap when nothing is wrong: a slot scan with no writes.
     * 激活盲人的每 tick 维护：自愈缺失或过期（属于其他对局）的发放，然后保证快捷栏恰好有一根盲杖（疯魔模式除外）。
     * 一切正常时开销很低：只扫描栏位而不写入。
     */
    public static void tickHolder(ServerPlayerEntity player) {
        // STOPPING still counts as running, but the match record may already be closed: never re-grant then.
        // STOPPING 仍算运行中，但对局记录可能已关闭：此时绝不重新发放。
        if (GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        ensureGranted(player);
    }

    /** Whether this Blind's kit state belongs to the current match. / 该盲人的道具状态是否属于当前对局。 */
    public static boolean isGranted(ServerPlayerEntity player) {
        BlindComponent state = BlindComponent.KEY.get(player);
        return BlindKitRules.continuesRound(state.caneReadyTick(), state.matchId(), BlindParticipants.currentMatchId());
    }

    /**
     * Removes every cane and ComTac (inventory 0..40 including the head slot, cursor, open handler slots), clears the
     * component and forgets the cane window. Used for role loss, death, reset, finalize, disconnect and the sweep.
     * 移除所有盲杖与 ComTac（背包 0..40 含头部槽、光标、已打开界面栏位），清空组件并忘记盲杖窗口。用于失去职业、死亡、
     * 重置、局末、断线与定期清理。
     */
    public static void strip(ServerPlayerEntity player) {
        removeAll(player, BlindInventoryRules::isBound);
        BlindComponent.KEY.get(player).clear();
        BlindCaneService.forget(player.getUuid());
    }

    /**
     * Puts a ComTac that a refused drop emptied back: the head slot when empty, else the first empty hotbar or hidden
     * main slot. With no room at all it is lost (the inventory is completely full).
     * 把被拒绝丢弃而清空的 ComTac 放回：头部槽为空则放回头部，否则放入第一个空的快捷栏或隐藏主背包栏位。
     * 完全没有空间时它会丢失（背包已全满）。
     */
    static void redeliverComTac(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        int target = inventory.getStack(BlindKitRules.HEAD_SLOT).isEmpty() ? BlindKitRules.HEAD_SLOT
                : inventory.getEmptySlot();
        if (target < 0) {
            return;
        }
        inventory.setStack(target, stack);
        inventory.markDirty();
    }

    /** Whether the player holds a ComTac anywhere (inventory incl. head slot, or cursor). / 玩家是否在任意位置持有 ComTac。 */
    public static boolean ownsComTac(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (BlindInventoryRules.isComTac(inventory.getStack(slot))) {
                return true;
            }
        }
        return BlindInventoryRules.isComTac(player.currentScreenHandler.getCursorStack());
    }

    private static void ensureGranted(ServerPlayerEntity player) {
        if (!isGranted(player)) {
            grantFresh(player, BlindParticipants.currentMatchId());
            return;
        }
        placeCaneUnlessPsycho(player);
    }

    /**
     * SparkTraits keeps a psycho-mode player's inventory bat-only every tick, so the cane waits until psycho mode ends
     * instead of being re-granted against it each tick.
     * SparkTraits 每 tick 都让疯魔模式玩家的背包只剩球棒，因此盲杖等到疯魔模式结束后再放回，而不是每 tick 与之反复争夺。
     */
    private static void placeCaneUnlessPsycho(ServerPlayerEntity player) {
        if (!BlindParticipants.isInPsychoMode(player)) {
            ensureCaneInHotbar(player);
        }
    }

    /**
     * Fresh grant (new Blind or state from another match): forget any old window, bind the match, write the initial
     * cane and Attune cooldowns (C2/C11) and the matching hotbar cooldown, and make sure one cane is in the hotbar
     * (after psycho mode, if it runs).
     * 全新发放（新盲人或其他对局的状态）：忘记旧窗口，绑定对局，写入盲杖与凝神的初始冷却（C2/C11）及对应的快捷栏冷却，
     * 并确保快捷栏中有一根盲杖（若处于疯魔模式则在其结束后）。
     */
    private static void grantFresh(ServerPlayerEntity player, @Nullable UUID match) {
        long now = player.getServerWorld().getTime();
        BlindComponent state = BlindComponent.KEY.get(player);
        BlindCaneService.forget(player.getUuid());
        // Both setters overwrite every window, so no clear() (and no extra owner sync) is needed first.
        // 两个设置方法会覆盖所有窗口，因此无需先 clear()（也不会多发一次同步）。
        state.bindMatch(match);
        state.setCane(0L, BlindKitRules.initialCaneReadyTick(now));
        state.setAttune(0L, BlindKitRules.initialAttuneReadyTick(now));
        BlindCaneService.writeItemCooldown(player, BlindRules.CANE_ROUND_START_COOLDOWN_TICKS);
        placeCaneUnlessPsycho(player);
    }

    /**
     * Keeps exactly one cane and forces it into the hotbar: the leftmost hotbar cane is kept, every other copy (main
     * slots 9-35, including the second row 27-35 that SparkFactionAPI 0.1.5.13+ shows, armor, offhand, cursor, crafting
     * grid or open container) is removed, and a missing hotbar cane is recreated there. A cane parked in the second row
     * therefore returns to the hotbar; the cane has no per-stack data and its cooldowns live on the component and the
     * per-Item cooldown, so nothing is lost.
     * 保持恰好一根盲杖并强制其位于快捷栏：保留最左侧的快捷栏盲杖，移除其他所有副本（主背包 9-35，包括 SparkFactionAPI
     * 0.1.5.13+ 显示的第二行 27-35、盔甲、副手、光标、合成格或已打开的容器），并在快捷栏缺失时重新创建。因此放进第二行的
     * 盲杖会回到快捷栏；盲杖没有逐堆数据，冷却记录在组件与按物品类型的冷却中，不会丢失任何东西。
     */
    private static void ensureCaneInHotbar(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int kept = BlindKitRules.NO_SLOT;
        int vacated = BlindKitRules.NO_SLOT;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!BlindInventoryRules.isCane(stack)) {
                continue;
            }
            if (kept == BlindKitRules.NO_SLOT && PlayerInventory.isValidHotbarIndex(slot)) {
                kept = slot;
                if (stack.getCount() > 1) {
                    stack.setCount(1);
                    changed = true;
                }
                continue;
            }
            inventory.setStack(slot, ItemStack.EMPTY);
            if (vacated == BlindKitRules.NO_SLOT && isStorageSlot(slot)) {
                vacated = slot;
            }
            changed = true;
        }
        if (BlindInventoryRules.isCane(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && BlindInventoryRules.isCane(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (kept == BlindKitRules.NO_SLOT) {
            changed |= placeCaneInHotbar(inventory, vacated);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /**
     * Puts a fresh cane into the hotbar. With a full hotbar the displaced item moves to
     * {@link BlindKitRules#displacementSlot}: the slot a misplaced cane just vacated, kept visible in the shown second
     * row when possible; with no room nothing is moved or destroyed and the next tick retries.
     * 把新盲杖放入快捷栏。快捷栏已满时，被移出的物品移到 {@link BlindKitRules#displacementSlot}：刚被错放盲杖腾出的
     * 栏位，并尽可能留在显示中的第二行使其可见；完全没有空间时不移动也不销毁任何物品，由下一 tick 重试。
     */
    private static boolean placeCaneInHotbar(PlayerInventory inventory, int vacated) {
        int target = BlindKitRules.hotbarTarget(inventory.selectedSlot, slot -> inventory.getStack(slot).isEmpty());
        if (target == BlindKitRules.NO_SLOT) {
            target = BlindKitRules.displacedHotbarSlot(inventory.selectedSlot);
            int destination = BlindKitRules.displacementSlot(SparkFactionSecondRowCompat.isShown(), vacated,
                    slot -> inventory.getStack(slot).isEmpty());
            if (destination == BlindKitRules.NO_SLOT) {
                return false;
            }
            inventory.setStack(destination, inventory.getStack(target));
        }
        inventory.setStack(target, new ItemStack(SparkWitchItems.whiteCane()));
        return true;
    }

    /** Main slots 9-35 or the offhand; armor slots never receive a displaced item. / 主背包 9-35 或副手。 */
    private static boolean isStorageSlot(int slot) {
        return (slot >= PlayerInventory.getHotbarSize() && slot < PlayerInventory.MAIN_SIZE)
                || slot == PlayerInventory.OFF_HAND_SLOT;
    }

    /** Removes every matching stack from inventory 0..40, the cursor and open handler slots. / 移除所有匹配物品。 */
    private static void removeAll(ServerPlayerEntity player, Predicate<ItemStack> bound) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (bound.test(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (bound.test(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        // Also covers the crafting grid and any open foreign container. / 同时覆盖合成格与已打开的外部容器。
        for (Slot slot : handler.slots) {
            if (bound.test(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }
}
