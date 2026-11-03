package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-player planner state: which task types are planner recommendations (so the client can label them) and the
 * server's next-task countdown (the passive that lets the planner see it).
 * 每名玩家的规划大师状态：哪些任务类型是推荐任务（供客户端标注），以及服务端的下次任务倒计时（被动可视化）。
 */
public final class PlannerPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<PlannerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("planner_player"),
            PlannerPlayerComponent.class
    );

    private final PlayerEntity player;
    /** Ordinals of task types that currently carry a recommendation. 当前带推荐的任务类型序号。 */
    private final Set<Integer> recommended = new LinkedHashSet<>();
    /** Remaining ticks until the next automatic task, -1 when unknown. 距离下次自动任务的剩余刻数，未知时为 -1。 */
    private int nextTaskTimer = -1;
    /** Last second value pushed to clients, to keep the sync cadence at 1 Hz. 上次同步给客户端的秒数，保持 1 Hz 同步。 */
    private int syncedSeconds = -1;

    public PlannerPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isRecommended(PlayerMoodComponent.Task type) {
        return recommended.contains(type.ordinal());
    }

    public void setRecommended(PlayerMoodComponent.Task type, boolean value) {
        boolean changed = value ? recommended.add(type.ordinal()) : recommended.remove(type.ordinal());
        if (changed) {
            sync();
        }
    }

    /** Drops every recommendation marker. 清除所有推荐标记。 */
    public void clearRecommended() {
        if (!recommended.isEmpty()) {
            recommended.clear();
            sync();
        }
    }

    public int getNextTaskTimer() {
        return nextTaskTimer;
    }

    /** Server-side write; only pushes a sync when the displayed second changes. 服务端写入；仅当显示的秒数变化时同步。 */
    public void setNextTaskTimer(int ticks) {
        nextTaskTimer = ticks;
        int seconds = ticks < 0 ? -1 : ticks / 20;
        if (seconds != syncedSeconds) {
            syncedSeconds = seconds;
            sync();
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (int ordinal : recommended) {
            list.add(NbtInt.of(ordinal));
        }
        tag.put("PlannerRecommended", list);
        tag.putInt("PlannerNextTaskTimer", nextTaskTimer);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        recommended.clear();
        NbtList list = tag.getList("PlannerRecommended", NbtElement.NUMBER_TYPE);
        for (int i = 0; i < list.size(); i++) {
            recommended.add(list.getInt(i));
        }
        nextTaskTimer = tag.contains("PlannerNextTaskTimer", NbtElement.NUMBER_TYPE)
                ? tag.getInt("PlannerNextTaskTimer")
                : -1;
        syncedSeconds = nextTaskTimer < 0 ? -1 : nextTaskTimer / 20;
    }
}
