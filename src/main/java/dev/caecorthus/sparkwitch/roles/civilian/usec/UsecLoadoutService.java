package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * USEC loadout and bound-item upkeep. Server only: one empty rifle plus the 60 s round-start lock per assignment
 * (round start from {@code UsecLifecycleService}, or a mid-round assignment while ACTIVE), never a periodic re-grant,
 * so a confiscated rifle (WP3 simply removes the stack) stays gone. Strips are explicit slot writes and never create
 * item entities.
 * USEC 开局装备与绑定物品维护。仅服务端：每次分配发放一把空步枪并写入 60 秒开局锁定（开局由 {@code UsecLifecycleService}
 * 驱动，或在 ACTIVE 对局中途分配时发放），绝不定期补发，因此被没收的步枪（WP3 直接移除物品堆）不会再出现。清除通过显式
 * 栏位写入完成，从不生成物品实体。
 */
public final class UsecLoadoutService {
    /**
     * Players already granted in the current round; a repeated {@code RoleAssigned} to USEC never grants again.
     * Server thread only; cleared per player by {@link #cleanUp} and wholesale at round start, finalize and server stop.
     * 本回合已发放过的玩家；重复的 USEC {@code RoleAssigned} 不会再次发放。仅服务端线程；由 {@link #cleanUp} 逐个清除，
     * 并在开局、收尾与服务器停止时整体清空。
     */
    private static final Set<UUID> GRANTED = new HashSet<>();

    private UsecLoadoutService() {
    }

    /**
     * Grants once per assignment: an empty {@code usec_rifle} (a fresh stack has no CUSTOM_DATA, i.e.
     * {@link UsecRifleState#EMPTY}: no magazine, empty chamber, no suppressor) unless one is already held, then the
     * 60 s round-start lock. The rifle goes through {@code insertStack} like the Seeker's revolver, so any SparkTraits
     * insert veto (the Impostor gun rule targets {@code wathe:guns} only, which the rifle is outside of) is never
     * bypassed.
     * 每次分配发放一次：一把空 {@code usec_rifle}（新物品堆没有 CUSTOM_DATA，即 {@link UsecRifleState#EMPTY}：无弹匣、空膛、
     * 无消音器；已持有时不再发放），然后写入 60 秒开局锁定。步枪与搜寻者的左轮一样经由 {@code insertStack} 放入，因此绝不绕过
     * SparkTraits 的任何入包否决（内鬼枪械规则只针对 {@code wathe:guns}，步枪不在其中）。
     */
    public static void grant(ServerPlayerEntity player) {
        GRANTED.add(player.getUuid());
        PlayerInventory inventory = player.getInventory();
        if (needsRifle(holdsRifle(player))) {
            inventory.insertStack(new ItemStack(SparkWitchItems.usecRifle()));
        }
        UsecCooldowns.roundStart(player);
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /** Final roles only, still STARTING (no running-state helper). / 仅按最终身份，仍处于 STARTING（不使用运行态判定）。 */
    static boolean receivesRoundStartGrant(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && UsecRules.isUsec(finalRole);
    }

    /**
     * A playing, alive player who becomes USEC while the round is ACTIVE (forced or swapped role) and was not granted
     * this round. / 在 ACTIVE 对局中成为 USEC（强制或换职业）、存活且在局、并且本回合尚未发放过的玩家。
     */
    static boolean receivesMidRoundGrant(boolean roundActive, boolean playingAndAlive, boolean alreadyGranted) {
        return roundActive && playingAndAlive && !alreadyGranted;
    }

    /** One rifle per grant. / 每次发放只有一把步枪。 */
    static boolean needsRifle(boolean holdsRifle) {
        return !holdsRifle;
    }

    public static boolean isGranted(PlayerEntity player) {
        return GRANTED.contains(player.getUuid());
    }

    /**
     * Terminal cleanup for one player: strip every bound item, clear {@link UsecPlayerComponent} (unscope) and forget
     * the grant. Used for role change away from USEC, terminal death, reset, finalize and disconnect.
     * 单名玩家的终局清理：清除所有绑定物品、清空 {@link UsecPlayerComponent}（取消开镜）并忘记发放记录。用于转出 USEC、
     * 最终死亡、重置、收尾与断线。
     */
    public static void cleanUp(ServerPlayerEntity player) {
        stripAll(player);
        UsecPlayerComponent.KEY.get(player).setScoped(false);
        GRANTED.remove(player.getUuid());
    }

    /** Forgets every grant (round start, finalize, server stop). / 忘记所有发放记录（开局、收尾、服务器停止）。 */
    static void forgetRound() {
        GRANTED.clear();
    }

    /**
     * Removes every bound item from inventory 0..40 (offhand and armor included), the cursor and every open handler slot
     * (crafting grid or foreign container included). / 从背包 0..40（含副手与盔甲栏）、光标及已打开界面的所有栏位
     * （含合成格与外部容器）移除所有绑定物品。
     */
    public static void stripAll(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (UsecInventoryRules.isBound(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (UsecInventoryRules.isBound(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (UsecInventoryRules.isBound(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /** Sweep test: only a match participant who is not USEC is stripped. / 清扫判定：只收走非 USEC 的对局参与者的物品。 */
    static boolean sweepStrips(boolean matchParticipant, boolean usec) {
        return matchParticipant && !usec;
    }

    /**
     * Whether a living player's refused drop goes back into their inventory: a USEC, or a free holder whom the bound
     * rules do not bind ({@link OffMatchUse#isMatchParticipant}). Any other participant's copy is destroyed; the sweep
     * strips them anyway.
     * 存活玩家被拒绝的丢弃是否放回其背包：USEC，或不受绑定规则约束的自由持有者（{@link OffMatchUse#isMatchParticipant}）。
     * 其他参与者的副本直接销毁；清扫本来也会收走。
     */
    static boolean keepsRefusedDrop(boolean matchParticipant, boolean usec) {
        return usec || !matchParticipant;
    }

    /**
     * Called at the HEAD of the player drop method on both sides; true cancels the drop, so a bound item never becomes
     * an item entity (cursor on close with a full inventory, a disconnect, creative throws, offer fallbacks). Move
     * semantics on the server: the passed stack is emptied first (some callers already removed it from its slot,
     * others still reference it), then a copy returns to a living, connected keeper ({@link #keepsRefusedDrop}) by
     * explicit slot writes; whatever does not fit goes back into the passed stack. On the client only the prediction
     * is cancelled; the server decides.
     * 在双端的玩家丢弃方法 HEAD 处调用；返回 true 即取消丢弃，因此绑定物品永不成为物品实体（背包已满时关闭界面的光标、断线、
     * 创造模式丢弃、放入失败的回退）。服务端采用移动语义：先清空传入的物品堆（有些调用方已把它移出栏位，另一些仍在栏位中
     * 引用它），再以显式栏位写入把副本放回存活且在线的保留者（{@link #keepsRefusedDrop}）；放不下的部分放回传入的物品堆。
     * 客户端只取消预测，由服务端裁定。
     */
    public static boolean interceptDrop(PlayerEntity player, ItemStack stack) {
        if (!UsecInventoryRules.isBound(stack)) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ItemStack copy = stack.copy();
            stack.setCount(0);
            boolean usec = UsecRules.isUsec(GameWorldComponent.KEY.get(serverPlayer.getWorld()).getRole(serverPlayer));
            if (serverPlayer.isAlive() && !serverPlayer.isDisconnected()
                    && keepsRefusedDrop(OffMatchUse.isMatchParticipant(serverPlayer), usec)) {
                if (!returnToInventory(serverPlayer.getInventory(), copy)) {
                    stack.setCount(copy.getCount());
                }
                serverPlayer.getInventory().markDirty();
            }
        }
        return true;
    }

    /**
     * Moves as much of {@code stack} as fits: same-item stacks with room first, then empty slots in
     * {@code getEmptySlot} order (hotbar first; with SparkFactionAPI 0.1.5.13+ the shown second row before hidden
     * storage). Explicit slot writes, never {@code insertStack}. True when all of it was placed.
     * 尽量放入 {@code stack}：先并入有空余的同种物品堆，再按 {@code getEmptySlot} 顺序放入空栏位（快捷栏优先；SparkFactionAPI
     * 0.1.5.13+ 时显示中的第二行先于隐藏栏位）。使用显式栏位写入，从不调用 {@code insertStack}。全部放入时返回 true。
     */
    static boolean returnToInventory(PlayerInventory inventory, ItemStack stack) {
        while (!stack.isEmpty()) {
            int slot = inventory.getOccupiedSlotWithRoomForStack(stack);
            if (slot < 0) {
                slot = inventory.getEmptySlot();
            }
            if (slot < 0) {
                return false;
            }
            ItemStack target = inventory.getStack(slot);
            if (target.isEmpty()) {
                inventory.setStack(slot, stack.split(Math.min(stack.getCount(), inventory.getMaxCount(stack))));
                continue;
            }
            int moved = Math.min(stack.getCount(),
                    Math.min(target.getMaxCount(), inventory.getMaxCount(target)) - target.getCount());
            if (moved <= 0) {
                return false;
            }
            target.increment(moved);
            stack.decrement(moved);
        }
        return true;
    }

    private static boolean holdsRifle(ServerPlayerEntity player) {
        return player.getInventory().contains(UsecInventoryRules::isRifle)
                || UsecInventoryRules.isRifle(player.currentScreenHandler.getCursorStack());
    }
}
