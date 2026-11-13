package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.item.BoundItemRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Peachwood sword distribution and its only working attack.
 * <ul>
 *   <li>While the world Otherworld window is open, every alive player who is not effective-killer faction and is
 *       either cowardly or at effective real sanity 25 or below ({@link SpecterRules#isLowSanity}) holds one peachwood
 *       sword. It is placed only in a visible slot (hotbar, then SparkFactionAPI's second row 27-35 when shown); with
 *       both full it waits for the next sweep. A copy in the offhand, on the cursor or in the 2×2 crafting grid counts as
 *       held; a copy in a hidden slot cannot be used and is removed. Everyone else loses every copy (inventory, cursor,
 *       crafting grid, open container) when the window closes or they stop qualifying.</li>
 *   <li>Only the Specter can be hit. A full-charge hit resolves four knife stabs, one after another: each stab spends
 *       one Otherworld shield layer, so fewer than four remaining layers means the extra stabs kill the Specter.</li>
 *   <li>Bound to the sweep: item frames, armor stands and allays refuse it ({@code UseEntityCallback} FAIL) and a
 *       decorated pot never takes it ({@code DecoratedPotBlockPeachwoodSwordMixin}), so parking a copy never mints
 *       another.</li>
 * </ul>
 * 桃木剑的发放与唯一有效的攻击。
 * 世界里世界窗口开启期间，所有存活、不属于有效杀手阵营，且怯懦或有效真实理智不高于 25（{@link SpecterRules#isLowSanity}）的
 * 玩家持有一把桃木剑。剑只放入可见栏位（快捷栏，其次是显示时的 SparkFactionAPI 第二行 27-35）；两者都满时等待下一轮检查。
 * 副手、光标或 2×2 合成格中的剑视为已持有；隐藏栏位中的剑无法使用，会被移除。其他人在窗口结束或不再符合条件时失去所有副本
 * （背包、光标、合成格与已打开的容器）。
 * 只有幽魂能被命中。满蓄力的一次攻击连续结算 4 次刀刺，每刀消耗 1 层里世界护盾；护盾不足 4 层时剩余刀数会击杀幽魂。
 * 与检查绑定：物品展示框、盔甲架与悦灵拒收（{@code UseEntityCallback} FAIL），饰纹陶罐不收
 * （{@code DecoratedPotBlockPeachwoodSwordMixin}），因此存放副本不会换来新剑。
 */
public final class SpecterPeachwoodService {
    /** Four knife stabs per full-charge hit. / 每次满蓄力攻击 4 次刀刺。 */
    private static final int STABS_PER_HIT = 4;
    /** Inventory sweep interval; half a second. / 背包检查间隔：半秒。 */
    private static final int MAINTAIN_INTERVAL_TICKS = 10;
    private static final int NO_SLOT = -1;
    private static boolean registered;

    private SpecterPeachwoodService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SpecterPeachwoodService::tick);
        AttackEntityCallback.EVENT.register(SpecterPeachwoodService::onAttack);
        // Both sides: the client stops before sending the packet, the server refuses a forged one.
        // 双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocksEntityUse(player.getStackInHand(hand), entity) ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean isPeachwoodSword(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PeachwoodSwordItem;
    }

    /** Item frames, armor stands and allays would keep the held sword. / 物品展示框、盔甲架与悦灵会收走手持的剑。 */
    static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isPeachwoodSword(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /** Asked by {@code DecoratedPotBlockPeachwoodSwordMixin}. / 由该 mixin 调用。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isPeachwoodSword(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /** Immediate pass after a window opens or closes; the periodic sweep covers everything else. / 窗口开关时立即刷新一次。 */
    public static void refresh(ServerWorld world) {
        maintain(world.getPlayers());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % MAINTAIN_INTERVAL_TICKS != 0) {
            return;
        }
        maintain(server.getPlayerManager().getPlayerList());
    }

    private static void maintain(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            boolean otherworldActive = SpecterOtherworldComponent.KEY.get(player.getServerWorld()).isActive();
            boolean changed;
            if (otherworldActive && canHoldSword(player)) {
                changed = keepOneUsableSword(player);
            } else if (GameFunctions.isPlayerSpectatingOrCreative(player) || player.isSpectator()) {
                // Leave creative/spectator inventories alone so a manually given sword can be used for testing.
                // 不碰创造/旁观玩家的背包，方便手动给剑测试。
                changed = false;
            } else {
                changed = removeSwords(player);
            }
            if (changed) {
                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
                if (player.currentScreenHandler != player.playerScreenHandler) {
                    player.playerScreenHandler.sendContentUpdates();
                }
            }
        }
    }

    /**
     * Alive and playing, not creative/spectating (a Taotie-swallowed player is a spectator), not the Specter itself,
     * not SparkFactionAPI effective killer faction (an Impostor at low sanity never gets one), and either cowardly or at
     * effective real sanity 25 or below.
     * 存活且参与对局、非创造/旁观（被饕餮吞下的玩家是旁观者）、不是幽魂本身、不属于 SFA 有效杀手阵营（低理智的内鬼也不会获得），
     * 且怯懦或有效真实理智不高于 25。
     */
    private static boolean canHoldSword(ServerPlayerEntity player) {
        if (player.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        if (SpecterRules.isSpecter(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))
                || SpecterRules.isEffectiveKiller(player)) {
            return false;
        }
        return SpecterCowardiceComponent.KEY.get(player).isCoward() || SpecterRules.isLowSanity(player);
    }

    /**
     * Keeps a usable sword: copies in hidden slots (and armor) are removed; when none is in a visible slot, the offhand,
     * on the cursor or in the crafting grid, a new one goes into the first free visible slot, or nowhere when all are
     * full. True when the inventory changed.
     * 保持一把可用的剑：隐藏栏位（及盔甲栏）中的副本被移除；可见栏位、副手、光标与合成格中都没有时，在第一个空的可见栏位放入一把
     * 新剑，全满时不放。背包有变化时返回 true。
     */
    private static boolean keepOneUsableSword(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean secondRow = SparkFactionSecondRowCompat.isShown();
        boolean usable = false;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!isPeachwoodSword(inventory.getStack(slot))) {
                continue;
            }
            if (isUsableSlot(slot, secondRow)) {
                usable = true;
            } else {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (usable
                || isPeachwoodSword(player.currentScreenHandler.getCursorStack())
                || holdsSword(player.playerScreenHandler.getCraftingInput())) {
            return changed;
        }
        int target = firstFreeVisibleSlot(inventory, secondRow);
        if (target == NO_SLOT) {
            return changed;
        }
        inventory.setStack(target, new ItemStack(SparkWitchItems.peachwoodSword()));
        return true;
    }

    /** Hotbar, the shown second row, or the offhand. / 快捷栏、显示中的第二行或副手。 */
    static boolean isUsableSlot(int slot, boolean secondRowShown) {
        return PlayerInventory.isValidHotbarIndex(slot)
                || slot == PlayerInventory.OFF_HAND_SLOT
                || (secondRowShown && SparkFactionSecondRowCompat.isSecondRowSlot(slot));
    }

    /** First empty hotbar slot, then (when shown) the first empty second-row slot; -1 when full. / 第一个空快捷栏位，其次（显示时）第一个空第二行栏位；全满时为 -1。 */
    private static int firstFreeVisibleSlot(PlayerInventory inventory, boolean secondRowShown) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        if (secondRowShown) {
            for (int slot = SparkFactionSecondRowCompat.SECOND_ROW_START;
                 slot < SparkFactionSecondRowCompat.SECOND_ROW_END; slot++) {
                if (inventory.getStack(slot).isEmpty()) {
                    return slot;
                }
            }
        }
        return NO_SLOT;
    }

    private static boolean holdsSword(Inventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isPeachwoodSword(inventory.getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Removes every copy: inventory 0..40, the cursor, the crafting grid and open container slots. / 移除所有副本。 */
    private static boolean removeSwords(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isPeachwoodSword(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                removed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (isPeachwoodSword(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            removed = true;
        }
        Inventory grid = player.playerScreenHandler.getCraftingInput();
        for (int slot = 0; slot < grid.size(); slot++) {
            if (isPeachwoodSword(grid.getStack(slot))) {
                grid.setStack(slot, ItemStack.EMPTY);
                removed = true;
            }
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && isPeachwoodSword(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Only a peachwood sword in the main hand against the Specter does anything. Every other target is cancelled so the
     * sword never hurts players or mobs; a non-full charge, a SparkTraits weapon block and a SparkTraits parry also do
     * nothing. FAIL/SUCCESS skip vanilla {@code attack()}, so the attack charge is reset here on every swing (as Wathe's
     * bat path does): a client spamming attack packets never gets more than one full-charge hit.
     * 只有主手桃木剑攻击幽魂才有效。其他目标一律取消，桃木剑不会伤到玩家或生物；蓄力未满、SparkTraits 武器封锁与 SparkTraits
     * 格挡同样无效。FAIL/SUCCESS 会跳过原版 {@code attack()}，因此每次挥剑都在此重置攻击蓄力（与 Wathe 球棒路径一致）：
     * 狂发攻击包的客户端也拿不到超过一次满蓄力命中。
     */
    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target,
                                         @Nullable EntityHitResult hitResult) {
        if (world.isClient || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!(player instanceof ServerPlayerEntity attacker)) {
            return ActionResult.PASS;
        }
        ItemStack stack = attacker.getStackInHand(hand);
        if (!isPeachwoodSword(stack)) {
            return ActionResult.PASS;
        }
        float charge = attacker.getAttackCooldownProgress(0.5F);
        attacker.resetLastAttackedTicks();
        if (!(target instanceof ServerPlayerEntity victim) || !isActiveSpecter(victim)) {
            return ActionResult.FAIL;
        }
        // Require the wielder to still be an eligible low-sanity player in an open Otherworld, so a stashed or traded
        // sword cannot be used outside the window. Creative is exempt for in-game testing.
        // 要求持有者仍是里世界内符合条件低理智玩家，避免提前藏起来或转手的剑在窗口外使用。创造模式豁免以便测试。
        if (!attacker.isCreative()
                && (!canHoldSword(attacker)
                || !SpecterOtherworldComponent.KEY.get(attacker.getServerWorld()).isActive())) {
            return ActionResult.FAIL;
        }
        if (charge < 1.0F
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, stack)
                || SparkTraitsKillerBridge.shouldCancelMeleeAttack(attacker, victim, stack)) {
            return ActionResult.FAIL;
        }
        GameRecordManager.recordItemUse(attacker, SparkWitchItems.PEACHWOOD_SWORD_ID, victim, null);
        for (int stab = 0; stab < STABS_PER_HIT && GameFunctions.isPlayerPlayingAndAlive(victim); stab++) {
            GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.KNIFE);
        }
        ServerWorld serverWorld = attacker.getServerWorld();
        serverWorld.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                WatheSounds.ITEM_KNIFE_STAB, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }

    private static boolean isActiveSpecter(ServerPlayerEntity player) {
        if (GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return SpecterRules.isSpecter(game.getRole(player));
    }
}
