package dev.caecorthus.sparkwitch.client.ninja;

import dev.caecorthus.sparkwitch.entity.NinjaGrapplingHookEntity;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Steers the local player along their own PULLING Grappling Hook chain. The server decides when a pull starts and ends
 * and syncs the feet target; the owner's client sets its own velocity toward it before each player tick, like vanilla
 * player movement, so latency never makes the pull overshoot and walls and doors stop it through ordinary physics.
 * Once arrived it stops pushing and waits for the server to end the cycle.
 * 沿本地玩家自己处于拉拽状态的钩爪铁链转向。服务端决定拉拽何时开始与结束并同步双脚终点；持有者客户端在每次玩家 tick 前
 * 朝终点设置自身速度（与原版玩家移动一样），因此延迟不会造成冲过头，墙与门也通过普通物理挡住它。到达后停止推动，等待
 * 服务端结束本次循环。
 */
public final class NinjaGrappleClientPull {
    private static final double SEARCH_MARGIN = 2.0;
    private static int settledHookId = -1;
    private static boolean registered;
    // Every grappling hook a client world tracks. Vanilla's ClientEntityManager.addEntity calls startTracking for each
    // entity it adds (Fabric ENTITY_LOAD fires at its TAIL) and removal calls stopTracking (ENTITY_UNLOAD at its HEAD),
    // so any hook the world query below could return is listed here; while none belongs to the current world the query
    // is skipped, with the same null result. Main thread only; compared by identity (Entity.equals compares ids, which
    // repeat across worlds) and held weakly, so a discarded world is never retained.
    // 客户端世界追踪的所有钩爪。原版 ClientEntityManager.addEntity 对每个加入的实体调用 startTracking（Fabric ENTITY_LOAD
    // 在其 TAIL 触发），移除时调用 stopTracking（ENTITY_UNLOAD 在其 HEAD 触发），因此下方世界查询可能返回的任何钩爪都在此
    // 列表中；没有属于当前世界的钩爪时跳过查询，结果同样为 null。仅主线程；按身份比较（Entity.equals 比较的 id 会跨世界
    // 重复），并以弱引用持有，因此不会滞留已丢弃的世界。
    private static final List<WeakReference<NinjaGrapplingHookEntity>> TRACKED_HOOKS = new ArrayList<>();

    private NinjaGrappleClientPull() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Start of the client tick, so the velocity is in place before the player's own movement this tick.
        // 在客户端 tick 开始时执行，使速度在本刻玩家自身移动之前就位。
        ClientTickEvents.START_CLIENT_TICK.register(NinjaGrappleClientPull::tick);
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof NinjaGrapplingHookEntity hook) {
                TRACKED_HOOKS.add(new WeakReference<>(hook));
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof NinjaGrapplingHookEntity hook) {
                TRACKED_HOOKS.removeIf(reference -> reference.get() == null || reference.get() == hook);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            settledHookId = -1;
            return;
        }
        NinjaGrapplingHookEntity hook = pullingHook(client.world, player);
        if (hook == null) {
            settledHookId = -1;
            return;
        }
        if (hook.getId() == settledHookId) {
            return;
        }
        Vec3d toTarget = hook.getPullTarget().subtract(player.getPos());
        double distance = toTarget.length();
        if (NinjaRules.hasGrappleArrived(distance)) {
            settledHookId = hook.getId();
            player.setVelocity(Vec3d.ZERO);
            return;
        }
        player.setVelocity(toTarget.multiply(NinjaRules.grapplePullSpeed(distance) / distance));
        player.fallDistance = 0.0F;
    }

    private static @Nullable NinjaGrapplingHookEntity pullingHook(ClientWorld world, ClientPlayerEntity player) {
        if (!tracksHookIn(world)) {
            return null;
        }
        Box area = player.getBoundingBox().expand(NinjaRules.GRAPPLING_HOOK_BREAK_DISTANCE + SEARCH_MARGIN);
        for (NinjaGrapplingHookEntity hook : world.getEntitiesByClass(NinjaGrapplingHookEntity.class, area,
                candidate -> candidate.getState() == NinjaGrapplingHookEntity.State.PULLING
                        && candidate.getOwnerPlayer() == player)) {
            return hook;
        }
        return null;
    }

    /** Whether any tracked hook lives in {@code world}; drops collected references. / 是否有已追踪钩爪位于该世界。 */
    private static boolean tracksHookIn(ClientWorld world) {
        boolean found = false;
        for (int index = TRACKED_HOOKS.size() - 1; index >= 0; index--) {
            NinjaGrapplingHookEntity hook = TRACKED_HOOKS.get(index).get();
            if (hook == null) {
                TRACKED_HOOKS.remove(index);
            } else if (hook.getWorld() == world) {
                found = true;
            }
        }
        return found;
    }
}
