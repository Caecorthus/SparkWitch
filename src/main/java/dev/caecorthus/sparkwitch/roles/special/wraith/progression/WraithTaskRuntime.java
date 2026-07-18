package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.mixin.PlayerMoodComponentAccessor;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.util.TaskCompletePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class WraithTaskRuntime {
    private static final Map<UUID, WraithTaskSnapshot> RECONNECT_SNAPSHOTS = new HashMap<>();

    private WraithTaskRuntime() {
    }

    static void onDisconnect(ServerPlayerEntity player) {
        if (WraithStateService.isActive(player)) {
            RECONNECT_SNAPSHOTS.put(player.getUuid(), WraithTaskSnapshot.capture(player));
        } else {
            RECONNECT_SNAPSHOTS.remove(player.getUuid());
        }
    }

    static void restoreForActivation(ServerPlayerEntity player, WraithTaskSnapshot snapshot) {
        RECONNECT_SNAPSHOTS.remove(player.getUuid());
        snapshot.restore(player);
        ensureMinimumDistinctTasks(player, 3);
    }

    static void resumePlayer(ServerPlayerEntity player) {
        WraithTaskSnapshot snapshot = RECONNECT_SNAPSHOTS.remove(player.getUuid());
        if (snapshot != null) {
            snapshot.restore(player);
        }
        ensureMinimumDistinctTasks(player, 3);
    }

    static void clearPlayer(ServerPlayerEntity player) {
        RECONNECT_SNAPSHOTS.remove(player.getUuid());
    }

    static void clearAll() {
        RECONNECT_SNAPSHOTS.clear();
    }

    static void tick(PlayerMoodComponent component, PlayerEntity player) {
        if (!WraithStateService.isActive(player)) {
            return;
        }
        PlayerMoodComponentAccessor accessor = (PlayerMoodComponentAccessor) component;
        int nextTaskTimer = accessor.sparkwitch$getNextTaskTimer() - 1;
        boolean changed = false;
        if (nextTaskTimer <= 0) {
            changed = addGeneratedTask(component, accessor);
            nextTaskTimer = nextTaskDelay(player, component.tasks.size());
        }
        accessor.sparkwitch$setNextTaskTimer(nextTaskTimer);

        ArrayList<PlayerMoodComponent.Task> removals = new ArrayList<>();
        for (PlayerMoodComponent.TrainTask task : component.tasks.values()) {
            task.tick(player);
            if (!task.isFulfilled(player)) {
                continue;
            }
            removals.add(task.getType());
            if (player instanceof ServerPlayerEntity serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new TaskCompletePayload());
                TaskComplete.EVENT.invoker().onTaskComplete(serverPlayer, task.getType());
            }
            changed = true;
        }
        removals.forEach(component.tasks::remove);
        if (changed) {
            component.sync();
        }
    }

    private static void ensureMinimumDistinctTasks(ServerPlayerEntity player, int minimum) {
        PlayerMoodComponent component = PlayerMoodComponent.KEY.get(player);
        PlayerMoodComponentAccessor accessor = (PlayerMoodComponentAccessor) component;
        int target = Math.min(Math.max(0, minimum), PlayerMoodComponent.Task.values().length);
        boolean changed = false;
        while (component.tasks.size() < target && addGeneratedTask(component, accessor)) {
            changed = true;
        }
        if (changed) {
            component.sync();
        }
    }

    private static boolean addGeneratedTask(PlayerMoodComponent component, PlayerMoodComponentAccessor accessor) {
        PlayerMoodComponent.TrainTask task = accessor.sparkwitch$generateTask();
        if (task == null) {
            return false;
        }
        component.tasks.put(task.getType(), task);
        component.timesGotten.putIfAbsent(task.getType(), 1);
        component.timesGotten.put(task.getType(), component.timesGotten.get(task.getType()) + 1);
        return true;
    }

    private static int nextTaskDelay(PlayerEntity player, int activeTaskCount) {
        int base = (int) (player.getRandom().nextFloat()
                * (GameConstants.MAX_TASK_COOLDOWN - GameConstants.MIN_TASK_COOLDOWN)
                + GameConstants.MIN_TASK_COOLDOWN);
        return Math.max(base + activeTaskCount * GameConstants.TASK_INTERVAL_PER_ACTIVE_TASK, 2);
    }
}
