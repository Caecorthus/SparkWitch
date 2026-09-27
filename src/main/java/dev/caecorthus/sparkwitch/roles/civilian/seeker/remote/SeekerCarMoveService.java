package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarMovement;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarPhysics;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarCorrectS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarMoveC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Frozen contract: applies validated car moves to the authoritative car, sends {@code seeker_car_correct} on every
 * rejection or clamp, and calls {@code SeekerCarEntity#onDrivenMove} for every accepted move that changes the car's
 * position (WP-03 throttles and plays the motor sound, so stationary keep-alive moves stay silent). All decisions come
 * from the pure {@link SeekerCarMoveRules}; the world is reached only through {@link SeekerCarPhysics}. Nobody is ever
 * kicked: repeated counted rejections end the session with CHEAT_SUSPECT.
 * 冻结契约：把已校验的移动应用到权威小车上，每次拒绝或钳制都下发 {@code seeker_car_correct}，并对每个改变了小车位置的
 * 已接受移动调用 {@code SeekerCarEntity#onDrivenMove}（WP-03 负责节流与播放电机声，静止的保活包因而保持安静）。
 * 所有判定来自纯规则 {@link SeekerCarMoveRules}；只经由 {@link SeekerCarPhysics} 访问世界。从不踢人：
 * 反复计数的拒绝以 CHEAT_SUSPECT 结束会话。
 */
public final class SeekerCarMoveService {
    private static final double MOVED_EPSILON_SQUARED = 1.0E-8;

    private SeekerCarMoveService() {
    }

    /** Stale or mismatched packets are dropped silently. / 过期或不匹配的包静默丢弃。 */
    public static void handleMove(ServerPlayerEntity player, SeekerCarMoveC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return;
        }
        SeekerSessionState session = status.sessionState();
        if (session == null || status.sessionMode() != SeekerSessionMode.CAR) {
            return;
        }
        long now = SeekerRemoteSessionService.serverTick(player);
        if (session.packetTick != now) {
            session.packetTick = now;
            session.packetsThisTick = 0;
        }
        session.packetsThisTick++;

        SeekerCarEntity car = SeekerDeviceService.findCar(player);
        boolean carMatches = car != null && !car.isRemoved() && car.getWorld() == player.getWorld()
                && car.getId() == status.carEntityId() && car.getId() == session.focusEntityId
                && player.getUuid().equals(car.ownerUuid()) && session.sessionId == status.sessionId();
        Vec3d server = car != null ? car.getPos() : player.getPos();
        SeekerCarMoveRules.Decision admission = SeekerCarMoveRules.admit(session.packetsThisTick, packet.sessionId(),
                status.sessionId(), status.sessionMode(), carMatches, packet.seq(), session.lastSeq, server);
        if (admission != null) {
            if (admission.countsAsCheat()) {
                countRejection(player, session, now);
            }
            return;
        }
        session.lastSeq = packet.seq();
        // Any admitted move proves the client is attached and alive, even when it is corrected below.
        // 任何被准入的移动都证明客户端已挂接且存活，即使随后被纠正。
        session.attached = true;
        session.lastMoveTick = now;

        World world = car.getWorld();
        SeekerCarPhysics.Collider collider = SeekerCarPhysics.worldCollider(world);
        SeekerCarMoveRules.Replay replay = replay(world, collider);
        Vec3d claimed = new Vec3d(packet.x(), packet.y(), packet.z());
        SeekerCarMoveRules.Decision decision = SeekerCarMoveRules.validate(new SeekerCarMoveRules.Move(server, claimed,
                packet.yaw(), session.budget.availableAt(now), session.airTicks, player.getPos(),
                session.effectiveRadius, SeekerRemoteSessionService.playArea(player)), replay);
        switch (decision.outcome()) {
            case DROP -> {
            }
            case CORRECT -> {
                correct(player, status, car);
                if (decision.countsAsCheat()) {
                    countRejection(player, session, now);
                }
            }
            case ACCEPT, VOID -> accept(player, status, session, car, server, claimed, decision, packet.yaw(),
                    collider, now);
        }
    }

    private static void accept(ServerPlayerEntity player, SeekerStatusComponent status, SeekerSessionState session,
                               SeekerCarEntity car, Vec3d server, Vec3d claimed, SeekerCarMoveRules.Decision decision,
                               float yaw, SeekerCarPhysics.Collider collider, long now) {
        Vec3d position = decision.position();
        session.airTicks = SeekerCarPhysics.isSupported(collider, server) ? 0 : session.airTicks + 1;
        session.budget.consume(now, claimed.subtract(server).horizontalLength());
        Vec3d delta = position.subtract(server);
        car.setPosition(position);
        car.setYaw(MathHelper.wrapDegrees(yaw));
        car.setVelocity(delta);
        car.setOnGround(SeekerCarPhysics.isSupported(collider, position));
        if (delta.lengthSquared() > MOVED_EPSILON_SQUARED) {
            car.onDrivenMove();
        }
        if (decision.outcome() == SeekerCarMoveRules.Outcome.VOID) {
            SeekerCarMovement.breakIfInVoid(car);
            return;
        }
        if (decision.clamped()) {
            correct(player, status, car);
        }
    }

    private static void countRejection(ServerPlayerEntity player, SeekerSessionState session, long now) {
        if (SeekerCarMoveRules.isCheatSuspect(session.rejects.record(now))) {
            SeekerCarEntity car = SeekerDeviceService.findCar(player);
            SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
            if (car != null && status != null) {
                correct(player, status, car);
            }
            SeekerRemoteSessionService.end(player, SeekerExitReason.CHEAT_SUSPECT);
        }
    }

    private static void correct(ServerPlayerEntity player, SeekerStatusComponent status, SeekerCarEntity car) {
        SeekerNetworking.sendCorrection(player, new SeekerCarCorrectS2CPacket(status.sessionId(),
                car.getX(), car.getY(), car.getZ(), car.getYaw()));
    }

    private static SeekerCarMoveRules.Replay replay(World world, SeekerCarPhysics.Collider collider) {
        return new SeekerCarMoveRules.Replay() {
            @Override
            public Vec3d replay(Vec3d from, Vec3d delta) {
                return SeekerCarPhysics.move(collider, from, delta).position();
            }

            @Override
            public boolean isSupported(Vec3d position) {
                return SeekerCarPhysics.isSupported(collider, position);
            }

            @Override
            public boolean isSpaceEmpty(Vec3d position) {
                return world.isSpaceEmpty(SeekerCarPhysics.traversalBox(position)
                        .contract(SeekerCarMoveRules.SPACE_CONTRACTION));
            }
        };
    }
}
