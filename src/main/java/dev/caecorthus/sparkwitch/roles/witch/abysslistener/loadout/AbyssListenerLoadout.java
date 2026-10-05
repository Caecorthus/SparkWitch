package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Abyss Listener bound kit: grants the Shriek Gun after a committed Bewitched promotion and at round start for a forced
 * Abyss Listener, and keeps it reconciled every {@link AbyssListenerGunSweep#INTERVAL_TICKS} ticks for match
 * participants (restore a missing gun without touching its cooldown, remove duplicates, revoke it from every other
 * participant); free holders ({@link OffMatchUse}) are never swept and get a refused drop back
 * ({@link #keepRefusedDrop}).
 * Server-only except the world-use veto, which also answers on the client; the gun never becomes an item entity. The
 * gun is never granted from {@code RoleAssigned}: the promotion hook runs once the role, skills and shop are settled.
 * 聆渊者绑定装备：魔化使晋升提交后、以及被强制指定的聆渊者开局时发放啸音铳，并每 {@link AbyssListenerGunSweep#INTERVAL_TICKS}
 * tick 为对局参与者校正一次（补发缺失的枪但不改动冷却、移除重复、从其他所有参与者身上收回）；自由持有者
 * （{@link OffMatchUse}）从不被清扫，被拒绝的丢弃会还给他们（{@link #keepRefusedDrop}）。除世界交互否决在双端生效外
 * 仅服务端；枪从不变成物品实体。永不在 {@code RoleAssigned} 中发枪：晋升回调在身份、技能与商店确定后才运行。
 */
public final class AbyssListenerLoadout {
    /**
     * ON_FINISH_INITIALIZE phase ordered after the default phase, so SparkTraits' default-phase assignment and
     * Conscience compensation have settled final roles and traits (Fast Hands then shortens the first cooldown).
     * 排在默认阶段之后的 ON_FINISH_INITIALIZE 阶段，确保 SparkTraits 默认阶段的词条分配与良知补偿已确定最终身份与词条
     * （「快手」随后会缩短首次冷却）。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("abyss_listener_finish_initialize");

    private static boolean registered;

    private AbyssListenerLoadout() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStart(serverWorld, game);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long time = world.getTime();
            for (ServerPlayerEntity player : world.getPlayers()) {
                // Match participants only: a free holder's copy is never stripped, granted or deduplicated.
                // 只校正对局参与者：自由持有者的枪从不被收走、补发或去重。
                if (AbyssListenerGunSweep.sweeps(time, player.getId(), () -> OffMatchUse.isMatchParticipant(player))) {
                    reconcile(player, mayHold(player));
                }
            }
        });
        // The gun cannot be handed to item frames, armor stands or allays. Both sides: the client stops before
        // sending, the server refuses a forged packet. Decorated pots are handled by
        // mixin/abysslistener/DecoratedPotBlockAbyssListenerGunMixin instead, so the gun still fires there.
        // 枪无法交给物品展示框、盔甲架或悦灵。双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。
        // 饰纹陶罐改由 DecoratedPotBlockAbyssListenerGunMixin 处理，因此在陶罐前枪照常开火。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                AbyssListenerInventoryRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        // A SparkTraits-intercepted death (Last Stand etc.) leaves the player in play, so the gun stays.
        // 被 SparkTraits 拦截的死亡（背水一战等）使玩家仍留在对局中，因此保留枪。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim != null && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                removeAll(victim);
            }
        });
        ResetPlayer.EVENT.register(AbyssListenerLoadout::removeAll);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    removeAll(player);
                }
            }
        });
    }

    /**
     * Called by the accomplice-variant hook once a Bewitched promotion committed (the inventory is kept; a duplicate
     * gun is deduplicated by the reconcile).
     * 魔化使晋升提交后由共犯变体回调调用（背包保持不变；重复的枪会被校正去重）。
     */
    public static void grantAfterPromotion(ServerPlayerEntity player) {
        if (player == null || !mayHold(player)) {
            return;
        }
        grantWithInitialCooldown(player);
    }

    /** Playing, alive and exactly the Abyss Listener. / 正在对局、存活且恰好是聆渊者。 */
    public static boolean mayHold(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return AbyssListenerInventoryRules.isEntitled(GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player));
    }

    /**
     * Forced round-start Abyss Listeners (/wathe:forceRole): the role is assigned before this phase, never through a
     * promotion, so each living one lacking the gun is granted it with the initial cooldown here.
     * 开局被强制指定的聆渊者（/wathe:forceRole）：身份在此阶段之前分配，不经过晋升，因此在此为每名缺枪的存活聆渊者发枪并写入首次冷却。
     */
    private static void grantRoundStart(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (AbyssListenerInventoryRules.receivesRoundStartGun(
                    game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))
                    && !holdsGun(player)) {
                grantWithInitialCooldown(player);
            }
        }
    }

    /**
     * Places exactly one gun, then writes the initial cooldown with the VANILLA {@code set} (owner spec: SparkTraits
     * Fast Hands applies to it). Item cooldowns are per Item, so later re-grants by the sweep keep whatever is left.
     * 放入恰好一把枪，然后用原版 {@code set} 写入首次冷却（所有者要求：SparkTraits「快手」对其生效）。
     * 物品冷却按物品类型记录，因此之后清理补发的枪会保留剩余冷却。
     */
    private static void grantWithInitialCooldown(ServerPlayerEntity player) {
        reconcile(player, true);
        player.getItemCooldownManager().set(SparkWitchItems.shriekGun(), AbyssListenerRules.GUN_INITIAL_COOLDOWN_TICKS);
    }

    /** Terminal death, ResetPlayer and round finalize. / 终结死亡、重置玩家与局末。 */
    static void removeAll(ServerPlayerEntity player) {
        reconcile(player, false);
    }

    /**
     * Applies {@link AbyssListenerGunSweep#decide} to every copy in inventory 0..40, the cursor and the open handler's
     * foreign slots (crafting grid, containers). Never touches the item cooldown. Cheap when nothing is wrong: a slot
     * scan with no writes.
     * 对背包 0..40、光标及已打开界面的外部栏位（合成格、容器）中的每一份枪应用 {@link AbyssListenerGunSweep#decide}。
     * 从不改动物品冷却。一切正常时开销很低：只扫描栏位而不写入。
     */
    static void reconcile(ServerPlayerEntity player, boolean entitled) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        List<AbyssListenerGunSweep.Location> locations = new ArrayList<>(2);
        List<Runnable> removers = new ArrayList<>(2);
        List<ItemStack> stacks = new ArrayList<>(2);
        // Inventory index of each copy; NO_SLOT for the cursor and foreign handler slots.
        // 每份副本所在的背包下标；光标与外部界面栏位为 NO_SLOT。
        List<Integer> inventorySlots = new ArrayList<>(2);
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (AbyssListenerInventoryRules.isGun(stack)) {
                int index = slot;
                locations.add(PlayerInventory.isValidHotbarIndex(slot)
                        ? AbyssListenerGunSweep.Location.HOTBAR
                        : AbyssListenerGunSweep.Location.STRAY);
                removers.add(() -> inventory.setStack(index, ItemStack.EMPTY));
                stacks.add(stack);
                inventorySlots.add(slot);
            }
        }
        ItemStack cursor = handler.getCursorStack();
        if (AbyssListenerInventoryRules.isGun(cursor)) {
            locations.add(AbyssListenerGunSweep.Location.CURSOR);
            removers.add(() -> handler.setCursorStack(ItemStack.EMPTY));
            stacks.add(cursor);
            inventorySlots.add(AbyssListenerGunSweep.NO_SLOT);
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && AbyssListenerInventoryRules.isGun(slot.getStack())) {
                locations.add(AbyssListenerGunSweep.Location.STRAY);
                removers.add(() -> slot.setStack(ItemStack.EMPTY));
                stacks.add(slot.getStack());
                inventorySlots.add(AbyssListenerGunSweep.NO_SLOT);
            }
        }
        if (locations.isEmpty() && !entitled) {
            return;
        }
        AbyssListenerGunSweep.Decision decision = AbyssListenerGunSweep.decide(entitled, locations);
        boolean changed = false;
        int vacated = AbyssListenerGunSweep.NO_SLOT;
        for (int copy = 0; copy < locations.size(); copy++) {
            if (copy == decision.keep()) {
                ItemStack kept = stacks.get(copy);
                if (kept.getCount() > 1) {
                    kept.setCount(1);
                    changed = true;
                }
                continue;
            }
            removers.get(copy).run();
            int slot = inventorySlots.get(copy);
            if (vacated == AbyssListenerGunSweep.NO_SLOT && AbyssListenerGunSweep.isStorageSlot(slot)) {
                vacated = slot;
            }
            changed = true;
        }
        if (decision.grant()) {
            changed |= placeGun(player, new ItemStack(SparkWitchItems.shriekGun()), vacated);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /**
     * Drop-guard follow-up, called by {@code PlayerEntityAbyssListenerGunMixin} after it refused a gun drop. Vanilla
     * {@code ScreenHandler.onClosed} hands the cursor stack to {@code offerOrDrop} (or {@code dropItem}) and clears the
     * cursor afterwards, so with a full inventory a refused drop would delete the gun. No sweep restores a free holder's
     * copy, so a living free holder gets that cursor gun straight back
     * ({@link AbyssListenerInventoryRules#keepsRefusedDrop}), placed like a sweep grant; with every slot full the
     * displaced hotbar item drops instead, so no other item is destroyed (if that slot holds another gun, the holder
     * keeps that one and the returned copy is not kept). Any other refused drop is left alone: its stack
     * either never left the inventory or is a copy the caller already inserted (vanilla {@code /give} drops one for the
     * pickup animation), so handing it back would duplicate the gun. A match participant's copy stays the sweep's job.
     * 掉落防护的后续处理，由 {@code PlayerEntityAbyssListenerGunMixin} 在拒绝丢枪后调用。原版 {@code ScreenHandler.onClosed}
     * 把光标物品交给 {@code offerOrDrop}（或 {@code dropItem}）之后才清空光标，因此背包已满时被拒绝的丢弃会删除这把枪。
     * 没有清扫会补回自由持有者的枪，因此存活的自由持有者会立即拿回该光标上的枪
     * （{@link AbyssListenerInventoryRules#keepsRefusedDrop}），放置方式与清扫补发相同；所有栏位都满时改为丢出被移出的
     * 快捷栏物品，因此不会销毁其他物品（若该栏位已是另一把枪，持有者保留那把，退回的这把不保留）。其他被拒绝的丢弃不作处理：其物品堆要么从未离开背包，要么是调用方已放入背包后的
     * 副本（原版 {@code /give} 为拾取动画丢出一份），放回会复制出第二把枪。对局参与者的枪仍由清扫处理。
     */
    public static void keepRefusedDrop(PlayerEntity player, ItemStack refused) {
        if (!(player instanceof ServerPlayerEntity holder)
                || !AbyssListenerInventoryRules.keepsRefusedDrop(holder.isAlive(),
                OffMatchUse.isMatchParticipant(holder), holder.currentScreenHandler.getCursorStack() == refused)) {
            return;
        }
        // Empties the cursor stack itself, so the gun exists exactly once whatever the caller does next.
        // 直接清空光标物品堆本身，使调用方之后无论如何处理，这把枪都只存在一份。
        ItemStack gun = refused.copyAndEmpty();
        PlayerInventory inventory = holder.getInventory();
        if (!placeGun(holder, gun, AbyssListenerGunSweep.NO_SLOT)) {
            int target = AbyssListenerGunSweep.displacedHotbarSlot(inventory.selectedSlot);
            ItemStack displaced = inventory.getStack(target);
            if (AbyssListenerInventoryRules.isGun(displaced)) {
                // The holder keeps the gun already there (dropping it would only be refused again), not this copy.
                // 持有者保留该栏位已有的枪（丢出它只会再次被拒绝），不保留这份副本。
                return;
            }
            inventory.setStack(target, gun);
            holder.dropItem(displaced, false);
        }
        inventory.markDirty();
    }

    /**
     * Puts {@code gun} into the first empty hotbar slot (Wathe's shop insert). With a full hotbar the rightmost
     * non-selected hotbar item moves to {@link AbyssListenerGunSweep#displacementSlot}: the slot a stray gun just
     * vacated (Time Stealer rule), kept visible in the shown second row when possible; with no room at all nothing is
     * moved or destroyed and it returns false (the sweep retries next time; {@link #keepRefusedDrop} evicts).
     * Reusing the vacated slot matters since SparkFactionAPI 0.1.5.13 lets a player park the gun in the visible row
     * 27-35: the displaced item takes the gun's place there instead of disappearing into hidden storage.
     * 把 {@code gun} 放入第一个空快捷栏位（Wathe 商店的插入方式）。快捷栏已满时，最右侧非选中快捷栏物品移到
     * {@link AbyssListenerGunSweep#displacementSlot}：错放的枪刚腾出的栏位（窃时者规则），并尽可能留在显示中的第二行
     * 使其可见；完全没有空间时不移动也不销毁任何物品并返回 false（清扫下次重试；{@link #keepRefusedDrop} 则挤出物品）。
     * SparkFactionAPI 0.1.5.13 起玩家可把枪放进可见的 27-35 行，复用腾出的栏位能让被移出的物品留在那里，而不是消失进隐藏栏位。
     */
    private static boolean placeGun(ServerPlayerEntity player, ItemStack gun, int vacated) {
        if (ShopEntry.insertStackInFreeSlot(player, gun)) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        int destination = AbyssListenerGunSweep.displacementSlot(SparkFactionSecondRowCompat.isShown(), vacated,
                slot -> inventory.getStack(slot).isEmpty());
        if (destination == AbyssListenerGunSweep.NO_SLOT) {
            return false;
        }
        int target = AbyssListenerGunSweep.displacedHotbarSlot(inventory.selectedSlot);
        inventory.setStack(destination, inventory.getStack(target));
        inventory.setStack(target, gun);
        return true;
    }

    private static boolean holdsGun(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (AbyssListenerInventoryRules.isGun(inventory.getStack(slot))) {
                return true;
            }
        }
        return AbyssListenerInventoryRules.isGun(player.currentScreenHandler.getCursorStack());
    }
}
