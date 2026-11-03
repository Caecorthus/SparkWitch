package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world ledger of what each dead player was carrying, plus which corpses were already searched.
 * <p>The snapshot is taken at death, before any loot settles, so a corpse always yields what its owner actually had.
 * Only {@code searchedCorpses} is synced — the snapshots are server-only answer keys.
 * 每张地图的记录：每名死者当时带着什么，以及哪些尸体已经被搜刮过。
 * <p>快照在死亡瞬间、结算之前拍下，所以尸体给出的永远是死者真正拥有的东西。
 * 只同步 {@code searchedCorpses}；快照是仅服务端使用的答案。
 */
public final class JunkerLootWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<JunkerLootWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ragpicker_loot"),
            JunkerLootWorldComponent.class
    );

    private final World world;
    private final Map<UUID, List<ItemStack>> snapshots = new HashMap<>();
    private final Set<UUID> searched = new HashSet<>();

    public JunkerLootWorldComponent(World world) {
        this.world = world;
    }

    public void recordDeath(ServerPlayerEntity victim) {
        List<ItemStack> loot = new ArrayList<>();
        DefaultedList<ItemStack> inventory = victim.getInventory().main;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                loot.add(stack.copy());
            }
        }
        snapshots.put(victim.getUuid(), loot);
        searched.remove(victim.getUuid());
    }

    public boolean hasLoot(UUID deadPlayerUuid) {
        List<ItemStack> loot = snapshots.get(deadPlayerUuid);
        return loot != null && !loot.isEmpty();
    }

    public ItemStack randomLoot(UUID deadPlayerUuid, net.minecraft.util.math.random.Random random) {
        List<ItemStack> loot = snapshots.get(deadPlayerUuid);
        if (loot == null || loot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return loot.get(random.nextInt(loot.size())).copy();
    }

    public boolean wasSearched(UUID deadPlayerUuid) {
        return searched.contains(deadPlayerUuid);
    }

    public void markSearched(UUID deadPlayerUuid) {
        if (searched.add(deadPlayerUuid)) {
            KEY.sync(world);
        }
    }

    public void reset() {
        snapshots.clear();
        if (!searched.isEmpty()) {
            searched.clear();
            KEY.sync(world);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(searched.size());
        for (UUID uuid : searched) {
            buf.writeUuid(uuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        searched.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            searched.add(buf.readUuid());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        snapshots.clear();
        searched.clear();
        if (tag.contains("snapshots")) {
            NbtCompound all = tag.getCompound("snapshots");
            for (String key : all.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    NbtList list = all.getList(key, NbtElement.COMPOUND_TYPE);
                    List<ItemStack> loot = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack stack = ItemStack.fromNbt(lookup, list.getCompound(i)).orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty()) {
                            loot.add(stack);
                        }
                    }
                    snapshots.put(uuid, loot);
                } catch (IllegalArgumentException ignored) {
                    // Skip a malformed key rather than dropping the whole ledger.
                    // 跳过损坏的键，不因此丢掉整本记录。
                }
            }
        }
        if (tag.contains("searched")) {
            NbtList list = tag.getList("searched", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                try {
                    searched.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Same as above. 同上。
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound all = new NbtCompound();
        snapshots.forEach((uuid, loot) -> {
            NbtList list = new NbtList();
            for (ItemStack stack : loot) {
                list.add(stack.encodeAllowEmpty(lookup));
            }
            all.put(uuid.toString(), list);
        });
        tag.put("snapshots", all);

        NbtList list = new NbtList();
        for (UUID uuid : searched) {
            list.add(NbtString.of(uuid.toString()));
        }
        tag.put("searched", list);
    }
}
