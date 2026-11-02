package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import dev.caecorthus.sparkwitch.client.bedrock.anim.BedrockAnimation;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockGeometry;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangRuntime;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherAnchor;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSkullEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plays a skin's animations on every Mini Wither this client draws, from state the client already has: the synced
 * active flag (an inactive-to-active change is a summon), the skulls it receives (a skull that appears next to its
 * owner's Mini Wither is a shot; its synced kind picks the crimson animation), the drawn follow velocity and the synced
 * aim. Nothing is sent or synced for it. Idle (or move) loops underneath; summon and shot play once on top; the head
 * bone then turns to the aim like the vanilla heads.
 * 根据客户端已有的状态为本客户端绘制的每只小凋灵播放皮肤动画：同步的在场标记（由不在场变为在场即为召唤）、收到的头颅（出现在
 * 主人的小凋灵旁边的头颅即为一次射击，其同步的种类决定是否播放赤首动画）、绘制的跟随速度与同步的瞄准方向。不为此发送或同步
 * 任何数据。待机（或移动）动画在底层循环；召唤与射击在上层播放一次；之后头部骨骼像原版的头一样转向瞄准方向。
 */
public final class MiniWitherSkinAnimator {
    /** A skull this close to its owner's anchor when it appears was just fired. / 出现时离主人悬浮点这么近的头颅刚被发射。 */
    private static final double SHOT_MATCH_DISTANCE = 3.0;
    /** Drawn speed (blocks per tick) above which the move state plays. / 超过此绘制速度（格/刻）时播放移动状态。 */
    private static final double MOVING_SPEED = 0.04;
    private static final Map<UUID, Owner> OWNERS = new HashMap<>();
    /** Reused by {@link #tick}, so no set is allocated per tick. / {@link #tick} 复用，避免每刻新建集合。 */
    private static final Set<UUID> SEEN = new HashSet<>();
    private static final Owner PREVIEW = new Owner();
    private static final Owner SKULLS = new Owner();

    private MiniWitherSkinAnimator() {
    }

    private static final class Owner {
        private final MolangRuntime runtime = new MolangRuntime();
        @Nullable
        private BedrockPose pose;
        @Nullable
        private BedrockGeometry poseFor;
        @Nullable
        private Boolean lastActive;
        private double activeSince = Double.NaN;
        private double summonAt = Double.NaN;
        private double shotAt = Double.NaN;
        private WeakReference<MiniWitherSkullEntity> shotSkull = new WeakReference<>(null);
        private boolean shotCrimson;

        private BedrockPose pose(BedrockGeometry geometry) {
            if (pose == null || poseFor != geometry) {
                pose = new BedrockPose(geometry.boneCount());
                poseFor = geometry;
            }
            pose.reset();
            return pose;
        }
    }

    /** End of every client tick: watches the active flags for summons. / 每个客户端刻末尾：观察在场标记以识别召唤。 */
    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            OWNERS.clear();
            return;
        }
        double now = client.world.getTime();
        Set<UUID> seen = SEEN;
        seen.clear();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            boolean active = MiniWitherPlayerComponent.KEY.get(player).isActive();
            Owner owner = OWNERS.computeIfAbsent(player.getUuid(), uuid -> new Owner());
            if (active && !Boolean.TRUE.equals(owner.lastActive)) {
                owner.activeSince = now;
                // First sight of an already-summoned Wither (joined, came into range) is no summon.
                // 第一次看到已在场的小凋灵（刚加入、刚进入范围）不算召唤。
                owner.summonAt = Boolean.FALSE.equals(owner.lastActive) ? now : Double.NaN;
            } else if (!active) {
                owner.activeSince = Double.NaN;
                owner.summonAt = Double.NaN;
                owner.shotAt = Double.NaN;
            }
            owner.lastActive = active;
            seen.add(player.getUuid());
        }
        OWNERS.keySet().retainAll(seen);
        seen.clear();
    }

    public static void clear() {
        OWNERS.clear();
    }

    /** A Mini Wither skull entered the client world. / 一个小凋灵头颅进入了客户端世界。 */
    public static void onSkullLoaded(MiniWitherSkullEntity skull) {
        if (!(skull.getOwner() instanceof PlayerEntity player)
                || skull.getPos().distanceTo(MiniWitherAnchor.position(player)) > SHOT_MATCH_DISTANCE) {
            return;
        }
        Owner owner = OWNERS.computeIfAbsent(player.getUuid(), uuid -> new Owner());
        owner.shotAt = player.getWorld().getTime();
        owner.shotSkull = new WeakReference<>(skull);
        owner.shotCrimson = false;
    }

    /**
     * The body pose for one draw. {@code headYaw} is relative to the body, as for the vanilla heads.
     * 一次绘制的本体姿态。{@code headYaw} 相对于身体，与原版的头相同。
     */
    public static BedrockPose poseBody(PlayerEntity player, MiniWitherSkinModel model, float tickDelta, float bodyYaw,
                                       float headYaw, float headPitch, Vec3d velocity, boolean firstPerson) {
        Owner owner = OWNERS.computeIfAbsent(player.getUuid(), uuid -> new Owner());
        double now = player.getWorld().getTime() + tickDelta;
        BedrockPose pose = owner.pose(model.geometry());
        MolangRuntime runtime = owner.runtime;
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double life = Double.isNaN(owner.activeSince) ? (player.age + tickDelta) / 20.0 : (now - owner.activeSince) / 20.0;
        setCommonQueries(runtime, life);
        runtime.setQuery("ground_speed", speed * 20.0);
        runtime.setQuery("modified_move_speed", speed * 20.0);
        runtime.setQuery("is_moving", speed > MOVING_SPEED ? 1.0 : 0.0);
        runtime.setQuery("head_x_rotation", headPitch);
        runtime.setQuery("head_y_rotation", headYaw);
        runtime.setQuery("body_y_rotation", bodyYaw);
        runtime.setQuery("is_first_person", firstPerson ? 1.0 : 0.0);
        runtime.setQuery("time_of_day", (player.getWorld().getTimeOfDay() % 24000L) / 24000.0);

        boolean moving = speed > MOVING_SPEED && !model.states().getOrDefault(MiniWitherAnimationState.MOVE, List.of())
                .isEmpty();
        loop(model, moving ? MiniWitherAnimationState.MOVE : MiniWitherAnimationState.IDLE, life, runtime, pose);
        if (!Double.isNaN(owner.summonAt)
                && !once(model, MiniWitherAnimationState.SUMMON, (now - owner.summonAt) / 20.0, runtime, pose)) {
            owner.summonAt = Double.NaN;
        }
        if (!Double.isNaN(owner.shotAt)) {
            MiniWitherSkullEntity skull = owner.shotSkull.get();
            if (skull != null && !skull.isRemoved()) {
                owner.shotCrimson = skull.kind() == MiniWitherSkullEntity.Kind.CRIMSON;
            }
            MiniWitherAnimationState shot = owner.shotCrimson
                    && !model.states().getOrDefault(MiniWitherAnimationState.CRIMSON, List.of()).isEmpty()
                    ? MiniWitherAnimationState.CRIMSON : MiniWitherAnimationState.SHOOT;
            if (!once(model, shot, (now - owner.shotAt) / 20.0, runtime, pose)) {
                owner.shotAt = Double.NaN;
            }
        }
        if (model.headBone() >= 0) {
            pose.addRotation(model.headBone(), headPitch, headYaw, 0.0F);
        }
        return pose;
    }

    /** The settings preview: the idle loop on a wall clock. / 设置界面预览：按真实时间播放待机循环。 */
    public static BedrockPose posePreview(MiniWitherSkinModel model) {
        BedrockPose pose = PREVIEW.pose(model.geometry());
        double life = Util.getMeasuringTimeMs() / 1000.0;
        setCommonQueries(PREVIEW.runtime, life);
        loop(model, MiniWitherAnimationState.IDLE, life, PREVIEW.runtime, pose);
        return pose;
    }

    /** A flying skull: its move loop (else idle) from its own age. / 飞行中的头颅：按自身年龄播放移动（否则待机）循环。 */
    public static BedrockPose poseSkull(MiniWitherSkinModel model, MiniWitherSkullEntity skull, float tickDelta) {
        BedrockPose pose = SKULLS.pose(model.geometry());
        double life = (skull.age + tickDelta) / 20.0;
        setCommonQueries(SKULLS.runtime, life);
        boolean move = !model.states().getOrDefault(MiniWitherAnimationState.MOVE, List.of()).isEmpty();
        loop(model, move ? MiniWitherAnimationState.MOVE : MiniWitherAnimationState.IDLE, life, SKULLS.runtime, pose);
        return pose;
    }

    private static void setCommonQueries(MolangRuntime runtime, double life) {
        runtime.setQuery("life_time", life);
        runtime.setQuery("delta_time",
                MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() / 20.0);
        runtime.setQuery("is_alive", 1.0);
        runtime.setQuery("is_on_ground", 0.0);
    }

    private static void loop(MiniWitherSkinModel model, MiniWitherAnimationState state, double time,
                             MolangRuntime runtime, BedrockPose pose) {
        for (String name : model.states().getOrDefault(state, List.of())) {
            BedrockAnimation animation = model.animations().get(name);
            if (animation != null) {
                animation.apply(time, runtime, model.geometry()::boneIndex, pose, 1.0F);
            }
        }
    }

    /**
     * Plays a one-shot state at {@code time} seconds; false once every animation of it has run its length (a looping
     * animation mapped to a one-shot state plays one cycle).
     * 在 {@code time} 秒处播放单次状态；其中所有动画都已播完自身时长时返回 false（映射到单次状态的循环动画只播放一轮）。
     */
    private static boolean once(MiniWitherSkinModel model, MiniWitherAnimationState state, double time,
                                MolangRuntime runtime, BedrockPose pose) {
        boolean running = false;
        for (String name : model.states().getOrDefault(state, List.of())) {
            BedrockAnimation animation = model.animations().get(name);
            if (animation == null || time > animation.length()) {
                continue;
            }
            running = true;
            animation.apply(time, runtime, model.geometry()::boneIndex, pose, 1.0F);
        }
        return running;
    }
}
