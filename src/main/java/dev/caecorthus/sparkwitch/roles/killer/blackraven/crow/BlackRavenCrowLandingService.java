package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Raven Form landing, listening to {@link BlackRavenCrowEvents#ENDED}: the session's Homing Feather
 * marker is always removed first; then a natural end with a dropped feather ({@link BlackRavenCrowLandingRules#landing})
 * teleports the player to a safe spot at the feather ({@link BlackRavenCrowLandingSafety}) and starts the 3 s hold, while
 * every other end returns to the body at once (crow-core already restored the body's visibility). Landing cues are
 * public and vanilla only: parrot flight and phantom flap at a lowered pitch, plus {@code sparkwitch:black_feather}
 * particles swirling around the player from many to none. The swirl is server-driven with vanilla particle packets
 * (velocity mode, one per feather, at most three per tick): the client particle turns its velocity by
 * {@link BlackRavenCrowLandingRules#SWIRL_TURN} every tick, so a tangential launch orbits the player with no custom
 * packet. During the hold the player stays visible and attackable; movement, items and skills are locked through the
 * Control Expert stun seam ({@link BlackRavenCrowLandingHold}), and this service pulls a drifting player back (camera
 * untouched) and zeroes the hold when it ends. Server thread only.
 * 化鸦落地的服务端部分，监听 {@link BlackRavenCrowEvents#ENDED}：总是先移除本次会话的归羽标记；随后，自然结束且已丢出归羽时，把玩家传送到归羽处的
 * 安全点并开始 3 秒定身；其他结束方式立即回到本体（crow-core 已恢复本体可见）。落地提示公开且只用原版资源：压低音调的鹦鹉振翅与幻翼扇翅，以及
 * 由多到无环绕玩家的 {@code sparkwitch:black_feather} 粒子。环绕由服务端以原版粒子包驱动（速度模式，每根羽毛一个包，每刻至多三个）：客户端粒子
 * 每刻把速度转过 SWIRL_TURN，因此切向发射即可绕玩家旋转，无需自定义数据包。定身期间玩家保持可见、可被攻击；移动、物品与技能经控场专家眩晕接缝
 * 锁定，本服务把偏离的玩家拉回（不改动视角），并在定身结束时同步零值。仅服务端线程。
 */
public final class BlackRavenCrowLandingService {
    static final String FEATHER_BLOCKED_KEY = "message.sparkwitch.black_raven_crow.feather_blocked";

    /** Owner uuid -> landing spot of a running hold. / 主人 uuid -> 进行中定身的落点。 */
    private static final Map<UUID, Vec3d> ANCHORS = new HashMap<>();

    private BlackRavenCrowLandingService() {
    }

    /** {@link BlackRavenCrowEvents#ENDED} listener. / 会话结束监听器。 */
    static void onEnded(ServerPlayerEntity owner, BlackRavenCrowEndReason reason, Vec3d crowPos) {
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(owner);
        if (component == null || reason == null) {
            return;
        }
        Vec3d feather = BlackRavenCrowFeatherService.takeFeather(owner, component);
        BlackRavenCrowLandingRules.Landing landing = BlackRavenCrowLandingRules.landing(
                reason.usesHomingFeather(), feather != null, isParticipant(owner));
        if (landing != BlackRavenCrowLandingRules.Landing.FEATHER) {
            return;
        }
        Vec3d spot = BlackRavenCrowLandingSafety.findSpot(owner, owner.getServerWorld(), feather);
        if (spot == null) {
            owner.sendMessage(Text.translatable(FEATHER_BLOCKED_KEY), true);
            return;
        }
        land(owner, component, spot);
    }

    private static void land(ServerPlayerEntity owner, BlackRavenCrowPlayerComponent component, Vec3d spot) {
        ServerWorld world = owner.getServerWorld();
        // Clear, never release: releasing a Wathe knife would stab. / 只清除、绝不释放：释放 Wathe 小刀会触发刺击。
        owner.clearActiveItem();
        owner.teleport(world, spot.x, spot.y, spot.z, owner.getYaw(), owner.getPitch());
        owner.setVelocity(Vec3d.ZERO);
        owner.velocityModified = true;
        owner.fallDistance = 0.0F;
        component.setLandingHoldUntil(world.getTime() + BlackRavenCrowLandingRules.HOLD_TICKS);
        ANCHORS.put(owner.getUuid(), spot);
        world.playSound(null, spot.x, spot.y + 1.0, spot.z, SoundEvents.ENTITY_PARROT_FLY, SoundCategory.PLAYERS,
                BlackRavenCrowLandingRules.PARROT_VOLUME, BlackRavenCrowLandingRules.PARROT_PITCH);
        world.playSound(null, spot.x, spot.y + 1.0, spot.z, SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS,
                BlackRavenCrowLandingRules.PHANTOM_VOLUME, BlackRavenCrowLandingRules.PHANTOM_PITCH);
        spawnSwirl(world, spot, BlackRavenCrowLandingRules.SWIRL_BURST);
    }

    /** END_SERVER_TICK: runs every hold. / 服务端刻末：处理所有定身。 */
    static void tick(MinecraftServer server) {
        if (ANCHORS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Vec3d>> iterator = ANCHORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Vec3d> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            BlackRavenCrowPlayerComponent component = player == null
                    ? null : BlackRavenCrowPlayerComponent.KEY.getNullable(player);
            // Cleared elsewhere (death, reset, role change, round end) or gone. / 已在别处清除或玩家已离开。
            if (component == null || component.getLandingHoldUntilTick() <= 0L) {
                iterator.remove();
                continue;
            }
            ServerWorld world = player.getServerWorld();
            long now = world.getTime();
            Vec3d anchor = entry.getValue();
            BlackRavenCrowLandingRules.HoldStep step = BlackRavenCrowLandingRules.holdStep(now,
                    component.getLandingHoldUntilTick(), isParticipant(player), player.getX() - anchor.x,
                    player.getY() - anchor.y, player.getZ() - anchor.z);
            if (step == BlackRavenCrowLandingRules.HoldStep.RELEASE) {
                // The zero sync is what unlocks the owner's client. / 零值同步才会解除主人客户端的锁定。
                component.setLandingHoldUntil(0L);
                iterator.remove();
                continue;
            }
            if (step == BlackRavenCrowLandingRules.HoldStep.PULL_BACK) {
                // Relative zero rotation: the camera stays free. / 相对旋转为零：视角保持自由。
                player.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F, PositionFlag.ROT);
                player.setVelocity(Vec3d.ZERO);
            }
            spawnSwirl(world, player.getPos(),
                    BlackRavenCrowLandingRules.swirlCount(component.getLandingHoldUntilTick() - now));
        }
    }

    static void forget(UUID player) {
        ANCHORS.remove(player);
    }

    static void forgetAll() {
        ANCHORS.clear();
    }

    private static boolean isParticipant(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /**
     * Launches {@code count} feathers tangentially on the swirl circle around {@code feet}; vanilla velocity mode
     * (count 0) carries the exact launch velocity to every client within 32 blocks.
     * 在 {@code feet} 周围的环绕圆上切向发射 {@code count} 根羽毛；原版速度模式（count 为 0）把精确的发射速度发给 32 格内的所有客户端。
     */
    private static void spawnSwirl(ServerWorld world, Vec3d feet, int count) {
        Random random = world.getRandom();
        double speed = BlackRavenCrowLandingRules.swirlSpeed();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double height = MathHelper.lerp(random.nextDouble(), BlackRavenCrowLandingRules.SWIRL_MIN_HEIGHT,
                    BlackRavenCrowLandingRules.SWIRL_MAX_HEIGHT);
            double lift = (random.nextDouble() * 2.0 - 1.0) * BlackRavenCrowLandingRules.SWIRL_VERTICAL_DRIFT;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            world.spawnParticles(BlackRavenFeatherRegistry.blackFeather(),
                    feet.x + cos * BlackRavenCrowLandingRules.SWIRL_RADIUS, feet.y + height,
                    feet.z + sin * BlackRavenCrowLandingRules.SWIRL_RADIUS,
                    0, -sin * speed, lift, cos * speed, 1.0);
        }
    }
}
