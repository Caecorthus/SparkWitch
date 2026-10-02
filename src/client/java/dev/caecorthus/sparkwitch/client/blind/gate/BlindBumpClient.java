package dev.caecorthus.sparkwitch.client.blind.gate;

import dev.caecorthus.sparkwitch.client.blind.BlindPerceptionClientState;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Client-only bumps for the local Blind (C10). Movement collision is decided on the client, so a bump needs no packet
 * and makes no public sound. A bumped player is a player whose box touches the Blind's box stretched
 * {@link #PROBE_DISTANCE} toward the motion and who really blocks the Blind ({@code collidesWith}, the same predicate
 * vanilla movement uses, so Wathe's solid players and every SparkFactionAPI / Wraith / NoellesRoles collision exemption
 * apply as-is: passable players never bump). It becomes perceived for {@code PLAYER_PULSE_TICKS} with a small local
 * pulse around it; otherwise a small pulse lights the contact point. Local pulses are kind {@code SELF}.
 * 本地盲人的纯客户端碰撞（C10）。移动碰撞由客户端判定，因此碰撞无需数据包，也不发出公开声音。被撞的玩家指其碰撞箱
 * 与盲人沿移动方向拉伸 {@link #PROBE_DISTANCE} 的碰撞箱相接、且确实阻挡盲人的玩家（{@code collidesWith}，与原版移动
 * 使用的判定相同，因此 Wathe 的实体玩家与所有 SparkFactionAPI / 冤魂 / NoellesRoles 碰撞豁免原样生效：可穿过的玩家
 * 永远撞不到）。被撞的玩家被感知 {@code PLAYER_PULSE_TICKS}，并在其周围产生一个小的本地脉冲；否则在接触点产生
 * 一个小脉冲。本地脉冲的类型为 {@code SELF}。
 */
public final class BlindBumpClient {
    public static final double PROBE_DISTANCE = 0.1;
    public static final float PULSE_RADIUS = 2.0f;
    private static final BlindBumpDetector DETECTOR = new BlindBumpDetector();

    private BlindBumpClient() {
    }

    static void tick(MinecraftClient client) {
        ClientPlayerEntity self = client.player;
        if (self == null || client.world == null || self.isSpectator() || !BlindView.isActive(client)) {
            DETECTOR.reset();
            return;
        }
        Input input = self.input;
        float forward = input == null ? 0.0f : input.movementForward;
        float sideways = input == null ? 0.0f : input.movementSideways;
        BlindBumpDetector.Bump bump = DETECTOR.tick(client.world.getTime(), self.horizontalCollision,
                self.collidedSoftly, forward != 0.0f || sideways != 0.0f, self.verticalCollision, self.isOnGround());
        if (bump == BlindBumpDetector.Bump.NONE) {
            return;
        }
        Vec3d direction;
        if (bump == BlindBumpDetector.Bump.HEAD) {
            direction = new Vec3d(0.0, 1.0, 0.0);
        } else {
            double[] horizontal = BlindBumpDetector.inputDirection(forward, sideways, self.getYaw());
            direction = new Vec3d(horizontal[0], 0.0, horizontal[1]);
        }
        onBump(client, self, direction);
    }

    private static void onBump(MinecraftClient client, ClientPlayerEntity self, Vec3d direction) {
        Box box = self.getBoundingBox();
        Box probe = box.stretch(direction.multiply(PROBE_DISTANCE));
        List<PlayerEntity> touched = client.world.getEntitiesByClass(PlayerEntity.class, probe,
                other -> other != self && !other.isSpectator() && other.isAlive() && self.collidesWith(other));
        BlindPerceptionClientState state = BlindPerceptionClientState.get();
        long now = Util.getMeasuringTimeNano();
        float pulseSeconds = BlindPerceptionClientState.ticksToSeconds(BlindRules.SOUND_PULSE_TICKS);
        if (touched.isEmpty()) {
            Vec3d contact = contactPoint(box, direction);
            state.addLocalPulse(contact.x, contact.y, contact.z, PULSE_RADIUS, pulseSeconds,
                    BlindPulseS2CPayload.SELF, now);
            return;
        }
        float perceivedSeconds = BlindPerceptionClientState.ticksToSeconds(BlindRules.PLAYER_PULSE_TICKS);
        for (PlayerEntity other : touched) {
            state.perceive(other.getId(), perceivedSeconds, now);
            Vec3d center = other.getBoundingBox().getCenter();
            state.addLocalPulse(center.x, center.y, center.z, PULSE_RADIUS, pulseSeconds,
                    BlindPulseS2CPayload.SELF, now);
        }
    }

    /** The face of the Blind's box in the bump direction. / 盲人碰撞箱在碰撞方向上的那一面。 */
    private static Vec3d contactPoint(Box box, Vec3d direction) {
        Vec3d center = box.getCenter();
        return new Vec3d(
                center.x + direction.x * box.getLengthX() / 2.0,
                center.y + direction.y * box.getLengthY() / 2.0,
                center.z + direction.z * box.getLengthZ() / 2.0);
    }
}
