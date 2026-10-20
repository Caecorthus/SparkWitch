package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherAnchor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-only soft follow of each drawn Mini Wither (owner 2026-10-20): a damped spring pulls the drawn position toward
 * {@link MiniWitherAnchor} once per client tick, so it trails a moving owner by at most {@link #MAX_LAG} blocks and
 * settles with a small overshoot when she stops. The server keeps firing from the rigid anchor; only the picture lags.
 * 每个被绘制的小凋灵的仅客户端柔性跟随（所有者 2026-10-20）：阻尼弹簧每个客户端刻把绘制位置拉向 {@link MiniWitherAnchor}，
 * 因此主人移动时它最多落后 {@link #MAX_LAG} 格，停下时带一点回弹后稳住。服务端仍从固定悬浮点发射；只有画面会滞后。
 */
public final class MiniWitherFollow {
    private static final double STIFFNESS = 0.25;
    private static final double DAMPING = 0.6;
    static final double MAX_LAG = 0.35;
    /** A jump this large (teleport, respawn) snaps instead of flying across. / 这么大的跳变（传送、复活）直接对齐。 */
    private static final double SNAP_DISTANCE = 4.0;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MiniWitherFollow() {
    }

    private static final class State {
        private Vec3d previous;
        private Vec3d current;
        private Vec3d velocity = Vec3d.ZERO;

        private State(Vec3d at) {
            previous = at;
            current = at;
        }
    }

    /** End of every client tick. / 每个客户端刻末尾。 */
    static void tick(MinecraftClient client) {
        if (client.world == null) {
            STATES.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (!MiniWitherRenderer.shows(player)) {
                continue;
            }
            Vec3d target = MiniWitherAnchor.position(player);
            State state = STATES.get(player.getUuid());
            if (state == null || state.current.distanceTo(target) > SNAP_DISTANCE) {
                STATES.put(player.getUuid(), new State(target));
            } else {
                step(state, target);
            }
            seen.add(player.getUuid());
        }
        STATES.keySet().retainAll(seen);
    }

    private static void step(State state, Vec3d target) {
        state.previous = state.current;
        state.velocity = state.velocity.multiply(DAMPING).add(target.subtract(state.current).multiply(STIFFNESS));
        Vec3d next = state.current.add(state.velocity);
        Vec3d lag = next.subtract(target);
        if (lag.length() > MAX_LAG) {
            next = target.add(lag.normalize().multiply(MAX_LAG));
            state.velocity = next.subtract(state.current);
        }
        state.current = next;
    }

    /**
     * Drawn position: the followed point interpolated like the owner's own body, pulled in front of walls again
     * because a trailing Wither may lag into one; the rigid anchor before the first tick.
     * 绘制位置：与主人身体同样插值的跟随点，并再次拉到墙前，因为落后的小凋灵可能滞后进墙里；第一个刻之前使用固定悬浮点。
     */
    static Vec3d position(PlayerEntity player, float tickDelta) {
        State state = STATES.get(player.getUuid());
        if (state == null) {
            return MiniWitherAnchor.position(player, tickDelta);
        }
        Vec3d point = state.previous.lerp(state.current, tickDelta);
        Vec3d eye = new Vec3d(
                MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()),
                MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + player.getStandingEyeHeight(),
                MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()));
        return MiniWitherAnchor.clampFromEye(player, eye, point);
    }

    /** Drawn velocity in blocks per tick, for the flight tilt. / 绘制速度（格/刻），用于飞行倾斜。 */
    static Vec3d velocity(PlayerEntity player) {
        State state = STATES.get(player.getUuid());
        return state == null ? Vec3d.ZERO : state.current.subtract(state.previous);
    }

    static void clear() {
        STATES.clear();
    }
}
