package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
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
 * Abyss Listener bound kit: grants the Shriek Gun after a committed recruitment and at round start for a forced Abyss
 * Listener, and keeps it reconciled every {@link AbyssListenerGunSweep#INTERVAL_TICKS} ticks (restore a missing gun
 * without touching its cooldown, remove duplicates, revoke it from every non-holder). Server-only except the
 * world-use veto, which also answers on the client; it never creates item entities. The gun is never granted from
 * {@code RoleAssigned}: the recruitment transaction restores the retained inventory after that event and would wipe it.
 * 聆渊者绑定装备：招募提交后、以及被强制指定的聆渊者开局时发放啸音铳，并每 {@link AbyssListenerGunSweep#INTERVAL_TICKS}
 * tick 校正一次（补发缺失的枪但不改动冷却、移除重复、从所有非持有者身上收回）。除世界交互否决在双端生效外仅服务端；从不生成物品实体。
 * 永不在 {@code RoleAssigned} 中发枪：招募事务会在该事件之后恢复保留背包，从而抹掉它。
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
                if (AbyssListenerGunSweep.isSweepTick(time, player.getId())) {
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
     * Called by the accomplice-variant hook once the recruitment committed (inventory already restored).
     * 招募提交后（背包已恢复）由共犯变体回调调用。
     */
    public static void grantAfterRecruit(ServerPlayerEntity recruit) {
        if (recruit == null || !mayHold(recruit)) {
            return;
        }
        grantWithInitialCooldown(recruit);
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
     * recruitment, so each living one lacking the gun is granted it with the initial cooldown here.
     * 开局被强制指定的聆渊者（/wathe:forceRole）：身份在此阶段之前分配，不经过招募，因此在此为每名缺枪的存活聆渊者发枪并写入首次冷却。
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
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (AbyssListenerInventoryRules.isGun(stack)) {
                int index = slot;
                locations.add(PlayerInventory.isValidHotbarIndex(slot)
                        ? AbyssListenerGunSweep.Location.HOTBAR
                        : AbyssListenerGunSweep.Location.STRAY);
                removers.add(() -> inventory.setStack(index, ItemStack.EMPTY));
                stacks.add(stack);
            }
        }
        ItemStack cursor = handler.getCursorStack();
        if (AbyssListenerInventoryRules.isGun(cursor)) {
            locations.add(AbyssListenerGunSweep.Location.CURSOR);
            removers.add(() -> handler.setCursorStack(ItemStack.EMPTY));
            stacks.add(cursor);
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && AbyssListenerInventoryRules.isGun(slot.getStack())) {
                locations.add(AbyssListenerGunSweep.Location.STRAY);
                removers.add(() -> slot.setStack(ItemStack.EMPTY));
                stacks.add(slot.getStack());
            }
        }
        if (locations.isEmpty() && !entitled) {
            return;
        }
        AbyssListenerGunSweep.Decision decision = AbyssListenerGunSweep.decide(entitled, locations);
        boolean changed = false;
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
            changed = true;
        }
        if (decision.grant()) {
            changed |= placeGun(player);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /**
     * Puts a fresh gun into the first empty hotbar slot (Wathe's shop insert). With a full hotbar the rightmost
     * non-selected hotbar item moves into the first empty hidden main slot (else an empty offhand) to make room; with
     * no room at all nothing is moved or destroyed and the next sweep retries.
     * 把新枪放入第一个空快捷栏位（Wathe 商店的插入方式）。快捷栏已满时，最右侧非选中快捷栏物品移入第一个空的隐藏主背包栏位
     * （否则移入空副手）以腾出位置；完全没有空间时不移动也不销毁任何物品，由下一次清理重试。
     */
    private static boolean placeGun(ServerPlayerEntity player) {
        ItemStack gun = new ItemStack(SparkWitchItems.shriekGun());
        if (ShopEntry.insertStackInFreeSlot(player, gun)) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        int destination = emptyHiddenStorageSlot(inventory);
        if (destination == AbyssListenerGunSweep.NO_SLOT) {
            return false;
        }
        int target = AbyssListenerGunSweep.displacedHotbarSlot(inventory.selectedSlot);
        inventory.setStack(destination, inventory.getStack(target));
        inventory.setStack(target, gun);
        return true;
    }

    private static int emptyHiddenStorageSlot(PlayerInventory inventory) {
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return inventory.getStack(PlayerInventory.OFF_HAND_SLOT).isEmpty()
                ? PlayerInventory.OFF_HAND_SLOT
                : AbyssListenerGunSweep.NO_SLOT;
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
