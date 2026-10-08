package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Add-on gates behind {@code api.client.WraithViewerApi}: extra viewers that see an active Wraith's body.
 * {@code api.client.WraithViewerApi} 背后的附属模组闸门：额外可以看到活跃冤魂身体的观察者。
 */
public final class WraithViewerGates {
    private static final List<BiPredicate<PlayerEntity, PlayerEntity>> GATES = new CopyOnWriteArrayList<>();

    private WraithViewerGates() {
    }

    public static void add(BiPredicate<PlayerEntity, PlayerEntity> gate) {
        GATES.add(gate);
    }

    /**
     * Render thread: whether an add-on gate reveals active Wraith {@code target}'s body to {@code viewer}. Never true
     * for the Wraith itself or a target that is not an active Wraith. A gate that throws is dropped.
     * 渲染线程：附属模组闸门是否向 {@code viewer} 显示活跃冤魂 {@code target} 的身体。冤魂本人或非活跃冤魂的目标
     * 永远为 false。抛出异常的闸门会被丢弃。
     */
    public static boolean revealsBody(PlayerEntity viewer, PlayerEntity target) {
        if (GATES.isEmpty() || viewer == null || target == null || viewer == target
                || !WraithClientState.isActive(target)) {
            return false;
        }
        for (BiPredicate<PlayerEntity, PlayerEntity> gate : GATES) {
            try {
                if (gate.test(viewer, target)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError failure) {
                GATES.remove(gate);
                SparkWitch.LOGGER.warn("Dropping a Wraith viewer gate that threw", failure);
            }
        }
        return false;
    }
}
