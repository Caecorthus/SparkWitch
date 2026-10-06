package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stable CCA contract {@code sparkwitch:bewitched} (NEVER_COPY, C3): the Bewitched's promotion task count
 * (0..{@link BewitchedRules#PROMOTION_TASKS}). Server-authoritative; synced only to the owner, whose HUD shows
 * {@code n/2}. NBT {@code PromotionTasks} keeps it across a mid-round relog; round start, round end and Wathe's
 * {@code ResetPlayer} clear it. No game logic lives here.
 * 稳定 CCA 契约 {@code sparkwitch:bewitched}（NEVER_COPY，C3）：魔化使的晋升任务计数（0..{@link BewitchedRules#PROMOTION_TASKS}）。
 * 服务端权威；只同步给本人，其 HUD 显示 {@code n/2}。NBT {@code PromotionTasks} 使其在局中重新登录后保留；开局、局末与
 * Wathe 的 {@code ResetPlayer} 会清空它。这里不含游戏逻辑。
 */
public final class BewitchedPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<BewitchedPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            BewitchedRules.ROLE_ID, BewitchedPlayerComponent.class);
    static final String PROMOTION_TASKS = "PromotionTasks";

    private final PlayerEntity player;
    private int promotionTasks;

    public BewitchedPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getPromotionTasks() {
        return promotionTasks;
    }

    /**
     * Server only: records one completed task and returns the new count (capped).
     * 仅服务端：记录完成一个任务并返回新的计数（封顶）。
     */
    public int recordTask() {
        int next = BewitchedRules.nextTaskCount(promotionTasks);
        if (next != promotionTasks) {
            promotionTasks = next;
            sync();
        }
        return promotionTasks;
    }

    /**
     * Server only: after a promotion failed before the role changed, the next completed task queues it again.
     * 仅服务端：晋升在身份变更前失败后，下一个完成的任务会再次让其入队。
     */
    public void rewindForRetry() {
        int rewound = Math.min(promotionTasks, BewitchedRules.PROMOTION_TASKS - 1);
        if (rewound != promotionTasks) {
            promotionTasks = rewound;
            sync();
        }
    }

    /** Server only. / 仅服务端。 */
    public void clear() {
        if (promotionTasks != 0) {
            promotionTasks = 0;
            sync();
        }
    }

    private void sync() {
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
        buf.writeVarInt(promotionTasks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        promotionTasks = Math.clamp(buf.readVarInt(), 0, BewitchedRules.PROMOTION_TASKS);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        promotionTasks = tag.contains(PROMOTION_TASKS, NbtElement.NUMBER_TYPE)
                ? Math.clamp(tag.getInt(PROMOTION_TASKS), 0, BewitchedRules.PROMOTION_TASKS)
                : 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (promotionTasks > 0) {
            tag.putInt(PROMOTION_TASKS, promotionTasks);
        }
    }
}
