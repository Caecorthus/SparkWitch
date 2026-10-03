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
 * {@link #PROBE_DISTANCE} toward the motion, with no block collision in the gap between the two boxes (the probe never
 * reaches through a thin block), and who really blocks the Blind ({@code collidesWith}, the same predicate vanilla
 * movement uses, so Wathe's solid players and every SparkFactionAPI / Wraith / NoellesRoles collision exemption apply
 * as-is: passable players never bump). It becomes perceived for {@code PLAYER_PULSE_TICKS} with a small local pulse
 * around it; otherwise a small pulse lights the contact point. A head bump needs the {@link #HEAD_PROBE_DISTANCE}
 * just above the head to be blocked. Local pulses are kind {@code SELF}.
 * 本地盲人的纯客户端碰撞（C10）。移动碰撞由客户端判定，因此碰撞无需数据包，也不发出公开声音。被撞的玩家指其碰撞箱
 * 与盲人沿移动方向拉伸 {@link #PROBE_DISTANCE} 的碰撞箱相接、两碰撞箱之间的间隙内没有方块碰撞（探测不会穿过薄方块）、
 * 且确实阻挡盲人的玩家（{@code collidesWith}，与原版移动使用的判定相同，因此 Wathe 的实体玩家与所有 SparkFactionAPI /
 * 冤魂 / NoellesRoles 碰撞豁免原样生效：可穿过的玩家永远撞不到）。被撞的玩家被感知 {@code PLAYER_PULSE_TICKS}，
 * 并在其周围产生一个小的本地脉冲；否则在接触点产生一个小脉冲。撞头要求头顶 {@link #HEAD_PROBE_DISTANCE} 内确实被挡住。
 * 本地脉冲的类型为 {@code SELF}。
 */
public final class BlindBumpClient {
    public static final double PROBE_DISTANCE = 0.1;
    /** Thickness of the space above the head that must be blocked for a head bump. / 撞头时须被挡住的头顶空间厚度。 */
    public static final double HEAD_PROBE_DISTANCE = 0.05;
    public static final float PULSE_RADIUS = 2.0f;
    /** Vanilla's collision epsilon: touching faces are not a block in between. / 原版碰撞容差：贴合的面不算中间有方块。 */
    private static final double TOUCH_EPSILON = 1.0E-7;
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
        boolean headBlocked = self.verticalCollision && !self.isOnGround()
                && !client.world.isSpaceEmpty(self, headProbe(self.getBoundingBox()));
        BlindBumpDetector.Bump bump = DETECTOR.tick(self.horizontalCollision, self.collidedSoftly,
                forward != 0.0f || sideways != 0.0f, self.verticalCollision, self.isOnGround(), headBlocked);
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
        onBump(client, self, direction, client.world.getTime());
    }

    private static void onBump(MinecraftClient client, ClientPlayerEntity self, Vec3d direction, long worldTick) {
        Box box = self.getBoundingBox();
        Box probe = box.stretch(direction.multiply(PROBE_DISTANCE));
        List<PlayerEntity> touched = client.world.getEntitiesByClass(PlayerEntity.class, probe,
                other -> other != self && !other.isSpectator() && other.isAlive() && self.collidesWith(other)
                        && client.world.isBlockSpaceEmpty(self,
                        BlindBumpDetector.between(box, other.getBoundingBox()).contract(TOUCH_EPSILON)));
        BlindPerceptionClientState state = BlindPerceptionClientState.get();
        long now = Util.getMeasuringTimeNano();
        float pulseSeconds = BlindPerceptionClientState.ticksToSeconds(BlindRules.SOUND_PULSE_TICKS);
        if (touched.isEmpty()) {
            if (!DETECTOR.admit(worldTick, BlindBumpDetector.ENVIRONMENT_CONTACT)) {
                return;
            }
            Vec3d contact = contactPoint(box, direction);
            state.addLocalPulse(contact.x, contact.y, contact.z, PULSE_RADIUS, pulseSeconds,
                    BlindPulseS2CPayload.SELF, now);
            return;
        }
        float perceivedSeconds = BlindPerceptionClientState.ticksToSeconds(BlindRules.PLAYER_PULSE_TICKS);
        for (PlayerEntity other : touched) {
            if (!DETECTOR.admit(worldTick, BlindBumpDetector.playerContact(other.getId()))) {
                continue;
            }
            state.perceive(other.getId(), perceivedSeconds, now);
            Vec3d center = other.getBoundingBox().getCenter();
            state.addLocalPulse(center.x, center.y, center.z, PULSE_RADIUS, pulseSeconds,
                    BlindPulseS2CPayload.SELF, now);
        }
    }

    /** The thin space just above the head. / 头顶紧邻的薄层空间。 */
    private static Box headProbe(Box box) {
        return new Box(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + HEAD_PROBE_DISTANCE, box.maxZ)
                .contract(TOUCH_EPSILON);
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
