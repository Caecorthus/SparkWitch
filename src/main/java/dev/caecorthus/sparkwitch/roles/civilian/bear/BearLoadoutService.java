package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import dev.doctor4t.wathe.api.Role;
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
 * BEAR loadout and the death deletion of BEAR items (the AXMC #199 model, owner 2026-11-05). Server only. One EMPTY
 * M32A1 per assignment (round start from {@code BearLifecycleService}, or a mid-round assignment while ACTIVE), never a
 * periodic re-grant. BEAR items follow Wathe's own item rules while their holder lives (no sweep, no transfer guard, no
 * role-change strip); they vanish only when the holder's items end: the holder's death (any holder, not only BEAR),
 * Wathe's reset and a disconnect from a live round. Strips are explicit slot writes and never create item entities.
 * BEAR 开局装备与 BEAR 物品的死亡删除（沿用 AXMC #199 模式，所有者 2026-11-05）。仅服务端。每次分配发放一具空的 M32A1
 * （开局由 {@code BearLifecycleService} 驱动，或在 ACTIVE 对局中途分配时发放），绝不定期补发。持有者存活期间 BEAR 物品遵循
 * Wathe 自身物品规则（没有清扫、没有转移防护、换职业不收走）；只在持有者的物品本就结束时消失：持有者死亡（任何持有者，
 * 不只是 BEAR）、Wathe 重置，以及在进行中的对局里断线。清除通过显式栏位写入完成，从不生成物品实体。
 */
public final class BearLoadoutService {
    /**
     * Players granted in the current round; a repeated {@code RoleAssigned} to BEAR never grants again. A role change
     * away keeps the record. Server thread only; cleared per player by {@link #forget} and wholesale at round start,
     * finalize and server stop.
     * 本回合已发放过的玩家；重复的 BEAR {@code RoleAssigned} 不会再次发放。转出 BEAR 时保留记录。仅服务端线程；由
     * {@link #forget} 逐个清除，并在开局、收尾与服务器停止时整体清空。
     */
    private static final Set<UUID> GRANTED = new HashSet<>();

    private BearLoadoutService() {
    }

    /**
     * Grants once per assignment: a fresh M32A1 (its default {@code sparkwitch:m32_cylinder} is empty) unless one is
     * already held. It goes through {@code insertStack}, so no insert veto is bypassed.
     * 每次分配发放一次：一具新的 M32A1（默认 {@code sparkwitch:m32_cylinder} 为空），已持有时不再发放。经由
     * {@code insertStack} 放入，因此不绕过任何入包否决。
     */
    public static void grant(ServerPlayerEntity player) {
        GRANTED.add(player.getUuid());
        PlayerInventory inventory = player.getInventory();
        if (needsLauncher(holdsLauncher(player))) {
            inventory.insertStack(new ItemStack(BearItems.M32A1));
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /** Final roles only, still STARTING. / 仅按最终职业，仍处于 STARTING。 */
    static boolean receivesRoundStartGrant(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && BearRules.isBear(finalRole);
    }

    /** Becomes BEAR while ACTIVE, playing and alive, not yet granted. / 在 ACTIVE 对局中成为 BEAR、存活在局且尚未发放。 */
    static boolean receivesMidRoundGrant(boolean roundActive, boolean playingAndAlive, boolean alreadyGranted) {
        return roundActive && playingAndAlive && !alreadyGranted;
    }

    /** One launcher per grant. / 每次发放只有一具发射器。 */
    static boolean needsLauncher(boolean holdsLauncher) {
        return !holdsLauncher;
    }

    public static boolean isGranted(PlayerEntity player) {
        return GRANTED.contains(player.getUuid());
    }

    /** Strip every BEAR item, then {@link #forget}. / 清除所有 BEAR 物品，然后 {@link #forget}。 */
    public static void cleanUp(ServerPlayerEntity player) {
        stripAll(player);
        forget(player);
    }

    /** Drops the grant record without touching items. / 移除发放记录而不触碰物品。 */
    public static void forget(ServerPlayerEntity player) {
        GRANTED.remove(player.getUuid());
    }

    static void forgetRound() {
        GRANTED.clear();
    }

    /**
     * Removes every BEAR item the player owns, in four steps: inventory 0..40 (offhand and armor included, so a worn
     * Rys-T), the cursor, the player's own screen ({@code playerScreenHandler}: the 2x2 crafting grid) and the headset
     * slot (earplugs; a ComTac there keeps its own Blind rule). A container the player has open keeps its contents: a
     * copy in a chest belongs to nobody's death.
     * 分四步移除玩家拥有的所有 BEAR 物品：背包 0..40（含副手与盔甲栏，因此包括戴着的 Rys-T）、光标、玩家自己的界面
     * （{@code playerScreenHandler}：2x2 合成格）以及耳机槽（耳塞；其中的 ComTac 沿用盲人自身规则）。玩家打开着的容器保留其内容：
     * 箱子里的物品不属于任何人的死亡。
     */
    public static void stripAll(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (BearItems.isBearItem(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (BearItems.isBearItem(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        ScreenHandler own = player.playerScreenHandler;
        for (Slot slot : own.slots) {
            if (BearItems.isBearItem(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (BearItems.isBearItem(HeadsetSlots.get(player))) {
            HeadsetSlots.set(player, ItemStack.EMPTY);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
            if (own != handler) {
                own.sendContentUpdates();
            }
        }
    }

    /**
     * HEAD of the player drop method ({@code mixin.bear.PlayerEntityBearItemMixin}), server only: true cancels the drop
     * of a BEAR item by a dead player (vanilla's inventory drop on death), so it vanishes (the callers empty the slot
     * after the call). A living player's drop follows Wathe's own rules. The guard only cancels and never copies,
     * restores or empties a stack, so vanilla {@code /give}'s cosmetic template needs no {@code GiveCommandDropScope}
     * check (the AXMC #199 rule): cancelling it only skips the pickup animation, the given count stays exact.
     * 玩家丢弃方法的 HEAD（{@code mixin.bear.PlayerEntityBearItemMixin}），仅服务端：已死亡玩家丢出 BEAR 物品（原版死亡时的背包
     * 掉落）时返回 true 取消丢弃，使其消失（调用方在调用后清空栏位）。存活玩家的丢弃遵循 Wathe 自身规则。该防护只取消、从不
     * 复制、放回或清空物品堆，因此原版 {@code /give} 的装饰性模板无需检查 {@code GiveCommandDropScope}（沿用 AXMC #199 规则）：
     * 取消它只是跳过拾取动画，给予数量仍然准确。
     */
    public static boolean vanishesOnDeath(PlayerEntity player, ItemStack stack) {
        return player instanceof ServerPlayerEntity && vanishesOnDrop(BearItems.isBearItem(stack), player.isDead());
    }

    /** Pure: only a dead holder's BEAR item vanishes. / 纯函数：只有已死亡持有者的 BEAR 物品会消失。 */
    static boolean vanishesOnDrop(boolean bearItem, boolean dead) {
        return bearItem && dead;
    }

    /** Excludes BEAR items from Wathe's death-drop loop. / 将 BEAR 物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return BearItems.isBearItem(stack);
    }

    private static boolean holdsLauncher(ServerPlayerEntity player) {
        return player.getInventory().contains(stack -> stack.isOf(BearItems.M32A1))
                || player.currentScreenHandler.getCursorStack().isOf(BearItems.M32A1);
    }
}
