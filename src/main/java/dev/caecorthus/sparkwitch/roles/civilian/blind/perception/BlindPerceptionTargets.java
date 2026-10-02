package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseSender;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The listening Blinds, rebuilt on the server thread when marked dirty (role assignment, death, reset, disconnect) and
 * by a sweep every {@link #SWEEP_INTERVAL_TICKS} ticks. {@link #anyActive()} is a volatile flag any thread may read:
 * the sound hooks and the voice listener return on it at once while no Blind is active, allocating nothing. Membership
 * is a hint; every send re-checks the real role ({@link BlindParticipants#isActiveBlind}). Server only.
 * 正在聆听的盲人名单，在被标记为脏（分配职业、死亡、重置、断线）时以及每 {@link #SWEEP_INTERVAL_TICKS} 刻的巡检中于服务端线程
 * 重建。{@link #anyActive()} 是任意线程都可读取的 volatile 标记：没有激活的盲人时，声音钩子与语音监听立即返回，不分配内存。
 * 名单只是提示；每次发送前都会重新确认真实职业（{@link BlindParticipants#isActiveBlind}）。仅服务端。
 */
public final class BlindPerceptionTargets {
    static final int SWEEP_INTERVAL_TICKS = 20;
    private static final BlindPerceiver[] NONE = new BlindPerceiver[0];

    private static volatile boolean anyActive;
    private static BlindPerceiver[] perceivers = NONE;
    private static final Map<UUID, ServerBlindPerceiver> BY_PLAYER = new HashMap<>();
    private static boolean dirty = true;

    private BlindPerceptionTargets() {
    }

    /** Any thread. / 任意线程。 */
    public static boolean anyActive() {
        return anyActive;
    }

    /** Server thread; never null, never mutated in place. / 服务端线程；从不为 null，也不会原地修改。 */
    static BlindPerceiver[] perceivers() {
        return perceivers;
    }

    /** Server thread: rebuild at the end of this tick. / 服务端线程：在本刻结束时重建。 */
    static void markDirty() {
        dirty = true;
    }

    static void tick(MinecraftServer server) {
        if (!dirty && server.getTicks() % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        dirty = false;
        refresh(server);
    }

    static void clear() {
        BY_PLAYER.clear();
        perceivers = NONE;
        anyActive = false;
        dirty = true;
    }

    private static void refresh(MinecraftServer server) {
        List<ServerBlindPerceiver> active = new ArrayList<>(1);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!BlindParticipants.isActiveBlind(player)) {
                continue;
            }
            ServerBlindPerceiver known = BY_PLAYER.get(player.getUuid());
            BlindEmitterThrottle throttle = known == null ? new BlindEmitterThrottle() : known.throttle();
            throttle.prune(player.getServerWorld().getTime());
            active.add(known != null && known.player() == player ? known : new ServerBlindPerceiver(player, throttle));
        }
        if (active.isEmpty() && BY_PLAYER.isEmpty()) {
            return;
        }
        BY_PLAYER.clear();
        for (ServerBlindPerceiver perceiver : active) {
            BY_PLAYER.put(perceiver.player().getUuid(), perceiver);
        }
        perceivers = active.isEmpty() ? NONE : active.toArray(BlindPerceiver[]::new);
        anyActive = perceivers.length > 0;
    }

    /** A listening Blind on this server. / 本服务端上的一个聆听盲人。 */
    private record ServerBlindPerceiver(ServerPlayerEntity player, BlindEmitterThrottle throttle)
            implements BlindPerceiver {
        @Override
        public Object world() {
            return player.getServerWorld();
        }

        @Override
        public int entityId() {
            return player.getId();
        }

        @Override
        public double squaredDistanceTo(double x, double y, double z) {
            return player.squaredDistanceTo(x, y, z);
        }

        @Override
        public int perceptionRange() {
            return BlindParticipants.perceptionRange(player);
        }

        @Override
        public boolean isActive() {
            return BlindParticipants.isActiveBlind(player);
        }

        @Override
        public long time() {
            return player.getServerWorld().getTime();
        }

        @Override
        public void send(BlindPulseS2CPayload payload) {
            BlindPulseSender.send(player, payload);
        }
    }
}
