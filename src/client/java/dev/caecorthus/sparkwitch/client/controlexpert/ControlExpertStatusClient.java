package dev.caecorthus.sparkwitch.client.controlexpert;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Local reads of the owner-only {@code sparkwitch:control_expert_status} copy. The server picks targets and runs the
 * timers; this client only honours its own synced counters, and only while it is a living participant, so dead,
 * spectating, creative or Wraith players keep their spectator and Wraith instinct.
 * 对仅拥有者可见的 {@code sparkwitch:control_expert_status} 副本的本地读取。目标与计时由服务端决定；
 * 本客户端只遵守自己收到的同步计时，且仅在自己是存活参与者时生效，因此死亡、旁观、创造或冤魂玩家
 * 保留其旁观与冤魂本能。
 */
public final class ControlExpertStatusClient {
    private ControlExpertStatusClient() {
    }

    /**
     * True while a disrupt or stun counter vetoes the local player's keyed instinct.
     * 当干扰或眩晕计时否决本地玩家的按键本能时返回 true。
     */
    public static boolean blocksLocalInstinct() {
        return localActiveStatus() != null;
    }

    /**
     * The local player's own status while it binds them, else null. Called for every outlined entity each frame,
     * so the O(1) counter test runs before the participant lookups.
     * 本地玩家自身的状态（仅在生效时），否则为 null。每帧每个被描边实体都会调用，
     * 因此先做 O(1) 的计时判断，再查询参与者状态。
     */
    static @Nullable ControlExpertStatusComponent localActiveStatus() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return null;
        }
        ControlExpertStatusComponent status = ControlExpertStatusComponent.KEY.getNullable(player);
        if (status == null || !status.blocksInstinct() || !ControlExpertTargeting.isParticipant(player)) {
            return null;
        }
        return status;
    }
}
