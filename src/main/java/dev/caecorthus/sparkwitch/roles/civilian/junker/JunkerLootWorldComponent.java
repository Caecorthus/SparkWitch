package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.component.WraithRoundComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only, never synced, per-world ledger of the 拾荒者's corpse loot: where a corpse's items come from, which
 * corpses were already searched, and removals still owed by offline owners. Nothing client-side reads it, so syncing
 * it would only leak that a ragpicker exists and which bodies were searched.
 * <p>Loot is transferred, never copied (owner 2026-11-04). Only a lootable owner yields anything
 * ({@link JunkerSearchRules#ownerYieldsLoot}: dead, never a Wraith this round, and if online a spectator who is not an
 * active Wraith). While that owner is online the item is taken out of their live inventory (main + off hand, never
 * armour). The snapshot is only the fallback for an offline owner: {@code JunkerService} takes it at the end of the
 * server tick of the death, after every {@code KillPlayer.AFTER} listener (Wathe's own death drops and every role's
 * kit strip) has run, and it is retaken when a dead owner disconnects. A stack taken from a snapshot is also queued for
 * removal from that owner's saved inventory, applied when they join again.
 * 仅服务端、从不同步的每张地图拾荒者尸体战利品记录：尸体上的物品从哪里来、哪些尸体已被搜刮过，以及离线死者尚欠的移除。
 * 客户端没有任何地方读取它，同步只会泄露拾荒者的存在以及哪些尸体被搜过。
 * <p>战利品是转移而不是复制（所有者 2026-11-04）。只有可被搜刮的主人才会给出物品（{@link JunkerSearchRules#ownerYieldsLoot}：
 * 已死亡、本回合从未成为冤魂，在线时还须处于旁观模式且不是活跃冤魂）。该主人在线时直接从其实时背包（主背包 + 副手，
 * 不含盔甲）取出。快照只是主人离线时的后备：{@code JunkerService} 在死亡所在服务端刻的末尾拍下它，此时所有
 * {@code KillPlayer.AFTER} 监听（Wathe 自己的死亡掉落与各职业的道具清除）都已执行；死者断线时会重拍。从快照取走的物品
 * 还会排队，等该主人重新进入时从其保存的背包中移除。
 */
public final class JunkerLootWorldComponent implements Component {
    public static final ComponentKey<JunkerLootWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ragpicker_loot"),
            JunkerLootWorldComponent.class
    );

    private final World world;
    private final Map<UUID, List<ItemStack>> snapshots = new HashMap<>();
    private final Set<UUID> searched = new HashSet<>();
    /** Stacks taken from an offline owner's snapshot, owed by their saved inventory. / 从离线主人快照取走、其存档背包尚欠的物品。 */
    private final Map<UUID, List<ItemStack>> pendingRemovals = new HashMap<>();

    public JunkerLootWorldComponent(World world) {
        this.world = world;
    }

    /** Server only: the deferred death snapshot (end of the death's server tick). / 仅服务端：延后的死亡快照（死亡所在刻末尾）。 */
    public void recordDeath(ServerPlayerEntity victim) {
        snapshots.put(victim.getUuid(), copyLootable(victim.getInventory()));
        searched.remove(victim.getUuid());
    }

    /**
     * Server only: a dead owner is leaving, so their live inventory is about to become unreachable; retake the
     * fallback snapshot from it, or drop it when they are not lootable (an active Wraith, or alive).
     * 仅服务端：死者即将离线，其实时背包将无法访问；据此重拍后备快照，若其不可被搜刮（活跃冤魂或仍存活）则丢弃快照。
     */
    public void refreshSnapshot(ServerPlayerEntity owner) {
        UUID uuid = owner.getUuid();
        if (!snapshots.containsKey(uuid)) {
            return;
        }
        if (yieldsLoot(uuid, owner)) {
            snapshots.put(uuid, copyLootable(owner.getInventory()));
        } else {
            snapshots.remove(uuid);
        }
    }

    /**
     * Server only: an owner who joins again loses the stacks taken from their snapshot while they were away, one equal
     * stack (item and components) per entry, wherever it now sits in main or off hand.
     * 仅服务端：重新进入的主人失去其离线期间从快照中取走的物品，每项移除一组物品与组件都相同的堆叠，不论它现在在主背包还是副手。
     */
    public void applyPendingRemovals(ServerPlayerEntity owner) {
        List<ItemStack> owed = pendingRemovals.remove(owner.getUuid());
        if (owed == null || owed.isEmpty()) {
            return;
        }
        PlayerInventory inventory = owner.getInventory();
        boolean changed = false;
        for (ItemStack stack : owed) {
            for (int slot : lootSlots(inventory)) {
                if (ItemStack.areItemsAndComponentsEqual(inventory.getStack(slot), stack)) {
                    inventory.removeStack(slot);
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            inventory.markDirty();
        }
    }

    /**
     * Server only: whether the corpse of {@code ownerUuid} still has anything to take, from the same source
     * {@link #takeLoot} uses.
     * 仅服务端：该尸体是否还有东西可拿，来源与 {@link #takeLoot} 相同。
     */
    public boolean hasLoot(UUID ownerUuid) {
        ServerPlayerEntity owner = onlineOwner(ownerUuid);
        if (!yieldsLoot(ownerUuid, owner)) {
            return false;
        }
        if (owner != null) {
            return !lootSlots(owner.getInventory()).isEmpty();
        }
        List<ItemStack> loot = snapshots.get(ownerUuid);
        return loot != null && !loot.isEmpty();
    }

    /**
     * Server only: removes one random whole stack from a lootable owner and returns it (empty when there is none). An
     * online owner loses it from their live inventory; an offline owner's snapshot loses that entry, so it cannot be
     * taken twice, and the stack is queued for removal from their saved inventory.
     * 仅服务端：从可被搜刮的主人身上随机移走一整组物品并返回（没有则为空）。在线主人从实时背包中失去它；离线主人的快照删去
     * 该项，因此不会被拿两次，并排队从其存档背包中移除。
     */
    public ItemStack takeLoot(UUID ownerUuid, Random random) {
        ServerPlayerEntity owner = onlineOwner(ownerUuid);
        if (!yieldsLoot(ownerUuid, owner)) {
            return ItemStack.EMPTY;
        }
        if (owner != null) {
            PlayerInventory inventory = owner.getInventory();
            List<Integer> slots = lootSlots(inventory);
            if (slots.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = inventory.removeStack(slots.get(random.nextInt(slots.size())));
            // The owner's screen handler resends the emptied slot. / 主人的界面处理器会重发被清空的槽位。
            inventory.markDirty();
            return taken;
        }
        List<ItemStack> loot = snapshots.get(ownerUuid);
        if (loot == null || loot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = loot.remove(random.nextInt(loot.size()));
        // Copied before the caller marks it, so it still equals the saved stack. / 在调用方打标记前复制，使其仍与存档物品相同。
        pendingRemovals.computeIfAbsent(ownerUuid, uuid -> new ArrayList<>()).add(taken.copy());
        return taken;
    }

    public boolean wasSearched(UUID deadPlayerUuid) {
        return searched.contains(deadPlayerUuid);
    }

    public void markSearched(UUID deadPlayerUuid) {
        searched.add(deadPlayerUuid);
    }

    /** Round initialization. / 回合初始化。 */
    public void reset() {
        snapshots.clear();
        searched.clear();
        pendingRemovals.clear();
    }

    private boolean yieldsLoot(UUID ownerUuid, @Nullable ServerPlayerEntity owner) {
        return JunkerSearchRules.ownerYieldsLoot(
                GameWorldComponent.KEY.get(world).isPlayerDead(ownerUuid),
                WraithRoundComponent.KEY.get(world).getConsumedPlayers().contains(ownerUuid),
                owner != null,
                owner != null && owner.isSpectator(),
                owner != null && WraithStateService.isActive(owner));
    }

    @Nullable
    private ServerPlayerEntity onlineOwner(UUID ownerUuid) {
        return world instanceof ServerWorld serverWorld
                ? serverWorld.getServer().getPlayerManager().getPlayer(ownerUuid)
                : null;
    }

    /** Non-empty main and off-hand slots; armour is never loot. / 非空的主背包与副手槽位；盔甲永远不是战利品。 */
    private static List<Integer> lootSlots(PlayerInventory inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.main.size(); slot++) {
            if (!inventory.main.get(slot).isEmpty()) {
                slots.add(slot);
            }
        }
        if (!inventory.offHand.get(0).isEmpty()) {
            slots.add(PlayerInventory.OFF_HAND_SLOT);
        }
        return slots;
    }

    private static List<ItemStack> copyLootable(PlayerInventory inventory) {
        List<ItemStack> loot = new ArrayList<>();
        for (int slot : lootSlots(inventory)) {
            loot.add(inventory.getStack(slot).copy());
        }
        return loot;
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        readStacks(tag.getCompound("snapshots"), lookup, snapshots);
        readStacks(tag.getCompound("pendingRemovals"), lookup, pendingRemovals);
        searched.clear();
        NbtList list = tag.getList("searched", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            try {
                searched.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry rather than dropping the whole ledger. / 跳过损坏的项，不因此丢掉整本记录。
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.put("snapshots", writeStacks(snapshots, lookup));
        tag.put("pendingRemovals", writeStacks(pendingRemovals, lookup));
        NbtList list = new NbtList();
        for (UUID uuid : searched) {
            list.add(NbtString.of(uuid.toString()));
        }
        tag.put("searched", list);
    }

    private static void readStacks(NbtCompound all, RegistryWrapper.WrapperLookup lookup,
                                   Map<UUID, List<ItemStack>> into) {
        into.clear();
        for (String key : all.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList list = all.getList(key, NbtElement.COMPOUND_TYPE);
                List<ItemStack> stacks = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = ItemStack.fromNbt(lookup, list.getCompound(i)).orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        stacks.add(stack);
                    }
                }
                into.put(uuid, stacks);
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed key rather than dropping the whole ledger. / 跳过损坏的键，不因此丢掉整本记录。
            }
        }
    }

    private static NbtCompound writeStacks(Map<UUID, List<ItemStack>> stacks, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound all = new NbtCompound();
        stacks.forEach((uuid, list) -> {
            NbtList encoded = new NbtList();
            for (ItemStack stack : list) {
                encoded.add(stack.encodeAllowEmpty(lookup));
            }
            all.put(uuid.toString(), encoded);
        });
        return all;
    }
}
