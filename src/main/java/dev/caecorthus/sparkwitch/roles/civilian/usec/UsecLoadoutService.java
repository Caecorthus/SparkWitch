package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
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
     * Server thread only; cleared per player by {@link #forget} and wholesale at round start, finalize and server stop.
     * A role change away from USEC keeps the record, so a second assignment in the same round never re-grants or
     * re-locks the rifle.
     * 本回合已发放过的玩家；重复的 USEC {@code RoleAssigned} 不会再次发放。仅服务端线程；由 {@link #forget} 逐个清除，
     * 并在开局、收尾与服务器停止时整体清空。转出 USEC 时保留记录，因此同一回合再次分配既不重发也不重新锁定步枪。
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
     * Terminal cleanup for one player: strip every bound item, then {@link #forget}. Used only where the holder's items
     * end anyway: a terminal death ({@code UsecDeathDrops}), Wathe's {@code ResetPlayer} (it clears the inventory; this
     * also clears the cursor and open slots) and a disconnect that Wathe turns into an ESCAPED death. A role change, the
     * round end and every other moment never strip (owner, 2026-11-05).
     * 单名玩家的终局清理：清除所有绑定物品，然后 {@link #forget}。只用于持有者的物品本就结束的时刻：最终死亡
     * （{@code UsecDeathDrops}）、Wathe 的 {@code ResetPlayer}（它会清空背包；这里还清理光标与已打开界面的栏位），以及被 Wathe
     * 转为 ESCAPED 死亡的断线。换职业、回合结束及其他任何时刻都不收走（所有者 2026-11-05）。
     */
    public static void cleanUp(ServerPlayerEntity player) {
        stripAll(player);
        forget(player);
    }

    /**
     * Forgets one player without touching their items: unscope and drop the grant record. / 忘记单名玩家而不触碰其物品：
     * 取消开镜并移除发放记录。
     */
    public static void forget(ServerPlayerEntity player) {
        UsecPlayerComponent.KEY.get(player).setScoped(false);
        GRANTED.remove(player.getUuid());
    }

    /** Forgets every grant (round start, finalize, server stop). / 忘记所有发放记录（开局、收尾、服务器停止）。 */
    static void forgetRound() {
        GRANTED.clear();
    }

    /**
     * Removes every bound item the player owns: inventory 0..40 (offhand and armor included), the cursor and the
     * player's own screen ({@code playerScreenHandler}: the 2x2 crafting grid). A container the player has open keeps
     * its contents: since 2026-11-05 bound items may be stored there, and a copy in a chest belongs to nobody's death.
     * 移除玩家自己拥有的所有绑定物品：背包 0..40（含副手与盔甲栏）、光标以及玩家自己的界面（{@code playerScreenHandler}：
     * 2x2 合成格）。玩家打开着的容器保留其内容：自 2026-11-05 起绑定物品可以存放在容器中，箱子里的物品不属于任何人的死亡。
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
        ScreenHandler own = player.playerScreenHandler;
        for (Slot slot : own.slots) {
            if (UsecInventoryRules.isBound(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
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
     * HEAD of the player drop method ({@code PlayerEntityUsecItemMixin}), server only: true cancels the drop of a bound
     * item by a dead player, so it vanishes (the callers empty the slot or cursor after the call). A living player's
     * drop is never touched: it follows Wathe's own rules (owner, 2026-11-05).
     * 玩家丢弃方法的 HEAD（{@code PlayerEntityUsecItemMixin}），仅服务端：已死亡玩家丢出绑定物品时返回 true 取消丢弃，使其消失
     * （调用方在调用后清空栏位或光标）。存活玩家的丢弃从不干预，遵循 Wathe 自身规则（所有者 2026-11-05）。
     */
    public static boolean vanishesOnDeath(PlayerEntity player, ItemStack stack) {
        return player instanceof ServerPlayerEntity
                && UsecInventoryRules.vanishesOnDrop(UsecInventoryRules.isBound(stack), player.isDead());
    }

    private static boolean holdsRifle(ServerPlayerEntity player) {
        return player.getInventory().contains(UsecInventoryRules::isRifle)
                || UsecInventoryRules.isRifle(player.currentScreenHandler.getCursorStack());
    }
}
