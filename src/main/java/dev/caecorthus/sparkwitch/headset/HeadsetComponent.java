package dev.caecorthus.sparkwitch.headset;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Frozen contract (BEAR WP0, design A of the 2026-11-05 survey): the storage behind the fifth "headset" slot next to
 * the four armor slots, one {@link ItemStack} per player in the CCA player component {@code sparkwitch:headset}
 * ({@code NEVER_COPY}, appended last in the CCA list). It lives outside {@code PlayerInventory}, so Wathe's inventory
 * clears, its death-drop loop and every 0..40 slot loop never see it; {@code HeadsetLifecycle} clears it on Wathe's
 * reset and BEAR's death cleanup deletes earplugs from it. Saved with the player; synced to its owner only (no other
 * client needs it). Server-authoritative: only the server's write syncs; a client copy (creative screen, WP4 slot
 * prediction) is overwritten by the next sync. Callers go through {@link HeadsetSlots}, never this class.
 * 冻结契约（BEAR WP0，2026-11-05 调研的方案 A）：四个盔甲槽旁第五个“耳机槽”背后的存储，每名玩家一个 {@link ItemStack}，
 * 存于 CCA 玩家组件 {@code sparkwitch:headset}（{@code NEVER_COPY}，追加在 CCA 列表末尾）。它不在 {@code PlayerInventory} 内，
 * 因此 Wathe 的背包清空、死亡掉落循环与所有 0..40 栏位循环都看不到它；{@code HeadsetLifecycle} 在 Wathe 重置时清空它，BEAR
 * 死亡清理会从中删除耳塞。随玩家存档；只同步给本人（其他客户端不需要）。服务端权威：只有服务端的写入会同步；客户端副本
 * （创造模式界面、WP4 栏位预测）会被下次同步覆盖。调用方一律经由 {@link HeadsetSlots}，不直接使用本类。
 */
public final class HeadsetComponent implements AutoSyncedComponent {
    public static final Identifier ID = SparkWitch.id("headset");
    public static final ComponentKey<HeadsetComponent> KEY = ComponentRegistry.getOrCreate(ID, HeadsetComponent.class);
    private static final String STACK_KEY = "stack";

    private final PlayerEntity player;
    private ItemStack stack = ItemStack.EMPTY;

    public HeadsetComponent(PlayerEntity player) {
        this.player = player;
    }

    /** The worn stack (never null; {@link ItemStack#EMPTY} when free). / 佩戴的物品堆（从不为 null；空时为 EMPTY）。 */
    public ItemStack getStack() {
        return stack;
    }

    /**
     * Stores {@code stack} (null = empty) and, on the server, syncs it to the owner.
     * 存入 {@code stack}（null 视为空），在服务端同步给本人。
     */
    public void setStack(ItemStack stack) {
        ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
        if (next.isEmpty() && this.stack.isEmpty()) {
            return;
        }
        this.stack = next;
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        stack = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        stack = tag.contains(STACK_KEY, NbtElement.COMPOUND_TYPE)
                ? ItemStack.fromNbtOrEmpty(lookup, tag.getCompound(STACK_KEY))
                : ItemStack.EMPTY;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (!stack.isEmpty()) {
            tag.put(STACK_KEY, stack.encode(lookup));
        }
    }
}
