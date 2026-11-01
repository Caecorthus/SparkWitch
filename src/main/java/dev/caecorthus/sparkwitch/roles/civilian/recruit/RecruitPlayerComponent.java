package dev.caecorthus.sparkwitch.roles.civilian.recruit;

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
 * Stable CCA contract {@code sparkwitch:recruit} (NEVER_COPY): the Recruit's completed-task count, whether the
 * first-task knife has been granted, and the per-round gun-pickup lock. Server-authoritative; the task fields are
 * synced only to the owner, whose bottom-right HUD shows {@code n/1} until the knife is granted; the gun lock is
 * server-only. NBT keeps all three across a mid-round relog; role assignment, Wathe's {@code ResetPlayer} and round
 * end clear them. No game logic lives here.
 * 稳定 CCA 契约 {@code sparkwitch:recruit}（NEVER_COPY）：新兵已完成任务数、第一个任务的刀是否已发放，以及本局枪械
 * 拾取锁。任务字段只同步给本人，本人右下角 HUD 在发刀前显示 {@code n/1}；枪械锁仅服务端。NBT 使三者都在局中重新登录后
 * 保留；身份分配、Wathe 的 {@code ResetPlayer} 与局末会清空它们。这里不含游戏逻辑。
 */
public final class RecruitPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<RecruitPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            RecruitRules.ROLE_ID, RecruitPlayerComponent.class);
    static final String TASKS_COMPLETED = "TasksCompleted";
    static final String KNIFE_GRANTED = "KnifeGranted";
    static final String GUN_ACQUIRED = "GunAcquired";

    private final PlayerEntity player;
    private int tasksCompleted;
    private boolean knifeGranted;
    private boolean gunAcquired;

    public RecruitPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public boolean isKnifeGranted() {
        return knifeGranted;
    }

    public boolean isGunAcquired() {
        return gunAcquired;
    }

    /**
     * Server only: locks ground gun pickup for the rest of the round. Deliberately not synced; the client never reads
     * it, and the NBT copy keeps it across a mid-round relog.
     * 仅服务端：本局余下时间禁止再从地面捡枪。刻意不同步；客户端不读取，NBT 副本使其在局中重登后保留。
     */
    public void lockGunPickup() {
        gunAcquired = true;
    }

    /** Server only: records one completed task, capped at the knife threshold, and syncs a change. / 仅服务端。 */
    public void recordTask() {
        int next = Math.min(tasksCompleted + 1, RecruitRules.REQUIRED_TASKS);
        if (next != tasksCompleted) {
            tasksCompleted = next;
            sync();
        }
    }

    /** Server only: marks the first-task knife granted and syncs a change. / 仅服务端。 */
    public void grantKnife() {
        if (!knifeGranted) {
            knifeGranted = true;
            sync();
        }
    }

    /** Server only: clears both fields and syncs. / 仅服务端：清空两个字段并同步。 */
    public void clear() {
        gunAcquired = false;
        if (tasksCompleted != 0 || knifeGranted) {
            tasksCompleted = 0;
            knifeGranted = false;
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
        buf.writeVarInt(tasksCompleted);
        buf.writeBoolean(knifeGranted);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        tasksCompleted = Math.clamp(buf.readVarInt(), 0, RecruitRules.REQUIRED_TASKS);
        knifeGranted = buf.readBoolean();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tasksCompleted = tag.contains(TASKS_COMPLETED, NbtElement.NUMBER_TYPE)
                ? Math.clamp(tag.getInt(TASKS_COMPLETED), 0, RecruitRules.REQUIRED_TASKS)
                : 0;
        knifeGranted = tag.getBoolean(KNIFE_GRANTED);
        gunAcquired = tag.getBoolean(GUN_ACQUIRED);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (tasksCompleted > 0) {
            tag.putInt(TASKS_COMPLETED, tasksCompleted);
        }
        if (knifeGranted) {
            tag.putBoolean(KNIFE_GRANTED, true);
        }
        if (gunAcquired) {
            tag.putBoolean(GUN_ACQUIRED, true);
        }
    }
}
