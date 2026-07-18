package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.mixin.wraith.WraithPlayerMoodComponentAccessor;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable copy of Wathe task progress and generation cadence. */
final class WraithTaskSnapshot {
    private final List<NbtCompound> tasks;
    private final Map<PlayerMoodComponent.Task, Integer> timesGotten;
    private final int nextTaskTimer;

    private WraithTaskSnapshot(
            List<NbtCompound> tasks,
            Map<PlayerMoodComponent.Task, Integer> timesGotten,
            int nextTaskTimer
    ) {
        this.tasks = tasks.stream().map(NbtCompound::copy).toList();
        EnumMap<PlayerMoodComponent.Task, Integer> times = new EnumMap<>(PlayerMoodComponent.Task.class);
        times.putAll(timesGotten);
        this.timesGotten = Map.copyOf(times);
        this.nextTaskTimer = nextTaskTimer;
    }

    static WraithTaskSnapshot capture(PlayerEntity player) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        List<NbtCompound> tasks = new ArrayList<>();
        for (PlayerMoodComponent.TrainTask task : component.tasks.values()) {
            tasks.add(task.toNbt().copy());
        }
        int timer = ((WraithPlayerMoodComponentAccessor) component).sparkwitch$getNextTaskTimer();
        return new WraithTaskSnapshot(tasks, component.timesGotten, timer);
    }

    void restore(ServerPlayerEntity player) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        component.tasks.clear();
        for (NbtCompound taskNbt : tasks) {
            int ordinal = taskNbt.getInt("type");
            if (ordinal < 0 || ordinal >= PlayerMoodComponent.Task.values().length) {
                continue;
            }
            PlayerMoodComponent.Task type = PlayerMoodComponent.Task.values()[ordinal];
            component.tasks.put(type, type.setFunction.apply(taskNbt.copy()));
        }
        component.timesGotten.clear();
        component.timesGotten.putAll(timesGotten);
        ((WraithPlayerMoodComponentAccessor) component).sparkwitch$setNextTaskTimer(nextTaskTimer);
        component.sync();
    }
}
