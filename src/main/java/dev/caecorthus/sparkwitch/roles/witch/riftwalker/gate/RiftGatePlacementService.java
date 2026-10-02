package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerMatch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Server placement transaction behind {@link RiftGateItem#use} (plan §5.1): validate (round, alive Riftwalker, not
 * inside a gate, not stunned or Kidnapper-controlled, SparkTraits interaction lock, floor, play area, room in front,
 * spacing, neighbours), allocate the gate number, spawn and register the entity, consume one item, start the short
 * placement cooldown, play the cue and record the replay item use. Never charges anything on failure. Owned by P1.
 * {@link RiftGateItem#use} 背后的服务端放置事务（plan §5.1）：校验（对局、存活的隙行者、不在门内、未眩晕且未被绑架者控制、
 * SparkTraits 交互封锁、地面、play area、正前方空间、间距、邻居），分配门编号，生成并登记实体，消耗一个物品，开始短暂的放置冷却，
 * 播放提示并记录回放。失败时不扣任何东西。归属 P1。
 *
 * <p>Server authority: the client only sends vanilla's use-item packet (it predicts nothing but CONSUME); every check
 * below reads server state. The user is classified by the RAW Wathe role, never the Black Raven acting role.
 * 服务端权威：客户端只发送原版使用物品数据包（除 CONSUME 外不做任何预测）；以下每项检查都读取服务端状态。
 * 使用者按 Wathe 原始职业判定，从不使用黑羽鸦伪装职业。
 */
public final class RiftGatePlacementService {
    /** Placement cue: a quiet charge, audible nearby (counterplay). / 放置提示音：较轻的充能声，附近可闻（反制线索）。 */
    static final float PLACE_SOUND_VOLUME = 0.6F;
    static final float PLACE_SOUND_PITCH = 1.3F;
    static final int PLACE_PORTAL_PARTICLES = 24;
    static final int PLACE_WITCH_PARTICLES = 6;
    /**
     * N-2: item cooldown after a successful placement. Holding use repeats it every 4 ticks, which would replace the
     * "placed #n" line with "too close" at once; the cooldown (also checked by the server) gives the line a second.
     * N-2：成功放置后的物品冷却。按住使用键时原版每 4 刻重复一次，会立刻把「已放置 #n」换成「太近」；冷却（服务端也会检查）
     * 让这行提示停留一秒。
     */
    static final int PLACE_COOLDOWN_TICKS = 20;

    private RiftGatePlacementService() {
    }

    /**
     * Frozen entry point; returns the item-use result for the hand ({@code SUCCESS}/{@code CONSUME} on placement,
     * {@code FAIL} otherwise). Server thread only.
     * 冻结入口；返回该手的物品使用结果（放置成功为 {@code SUCCESS}/{@code CONSUME}，否则为 {@code FAIL}）。仅服务端线程。
     */
    public static ActionResult tryPlace(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof RiftGateItem gateItem)) {
            return ActionResult.PASS;
        }
        ServerWorld world = player.getServerWorld();
        RiftGatePlacementFailure userFailure = userFailure(world, player);
        if (userFailure != null) {
            return refuse(player, userFailure);
        }
        // A null binding never matches, so the gate would discard itself at once. / null 绑定永不匹配，门会立即自删。
        String matchId = RiftwalkerMatch.currentMatchId(world);
        if (matchId == null) {
            return refuse(player, RiftGatePlacementFailure.UNAVAILABLE);
        }

        Direction facing = RiftGatePlacementRules.facingFromYaw(player.getYaw());
        OptionalDouble floorY = findFloorY(world, player);
        if (floorY.isEmpty()) {
            return refuse(player, RiftGatePlacementFailure.NO_FLOOR);
        }
        Vec3d pos = RiftGatePlacementRules.gatePosition(player.getPos(), facing, floorY.getAsDouble());
        RiftGatePlacementFailure spotFailure = spotFailure(world, pos, facing);
        if (spotFailure != null) {
            return refuse(player, spotFailure);
        }

        Optional<RiftGateEntity> spawned = RiftGateRegistry.spawn(world, pos, facing, player.getUuid(), matchId);
        if (spawned.isEmpty()) {
            return refuse(player, RiftGatePlacementFailure.BLOCKED);
        }
        int number = spawned.get().gateNumber();
        stack.decrementUnlessCreative(1, player);
        player.getItemCooldownManager().set(gateItem, PLACE_COOLDOWN_TICKS);
        playPlacementCue(world, pos);
        NbtCompound extra = new NbtCompound();
        extra.putString(RiftGateReplayFormatters.ACTION_KEY, RiftGateReplayFormatters.PLACE_ACTION);
        extra.putInt(RiftGateReplayFormatters.GATE_NUMBER_KEY, number);
        GameRecordManager.recordItemUse(player, RiftwalkerRules.GATE_ITEM_ID, null, extra);
        player.sendMessage(Text.translatable(RiftGatePlacementFailure.PLACED_MESSAGE_KEY, number), true);
        return ActionResult.CONSUME;
    }

    /**
     * Who may place: an ACTIVE round; a living, playing, survival participant who is not an active Wraith; RAW role
     * Riftwalker; not inside a gate; not Control-Expert-stunned; not Kidnapper-controlled (M-7, C16: the client input
     * lock is not authority, and entry and console close refuse a dragged player too); not under the SparkTraits
     * killer-interaction lock (Hunter trap precedent; an absent Traits adds no lock).
     * 谁可以放置：对局处于 ACTIVE；存活、参与中、生存模式且不是激活冤魂的参与者；原始职业为隙行者；不在门内；
     * 未被控制专家眩晕；未被绑架者控制（M-7、C16：客户端输入锁不是权威，进门与控制台关门同样拒绝被拖行的玩家）；
     * 未处于 SparkTraits 杀手交互封锁（与猎人陷阱相同；未安装 Traits 时不附加封锁）。
     */
    @Nullable
    private static RiftGatePlacementFailure userFailure(ServerWorld world, ServerPlayerEntity player) {
        // Inside first: an occupant is an alive spectator, so the liveness checks below would hide the reason.
        // 先判断门内：门内玩家是活着的旁观者，下面的存活检查会掩盖真正原因。
        if (RiftSessionService.isInside(player)) {
            return RiftGatePlacementFailure.INSIDE_GATE;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameFunctions.isPlayerAliveAndSurvival(player)
                || WraithStateService.isActive(player)
                || !RiftwalkerRules.isRiftwalker(game.getRole(player))
                || ControlExpertStun.isStunned(player)
                || isKidnapperControlled(player)
                || SparkTraitsKillerBridge.isKillerInteractionBlocked(player)) {
            return RiftGatePlacementFailure.UNAVAILABLE;
        }
        return null;
    }

    private static boolean isKidnapperControlled(ServerPlayerEntity player) {
        KidnapperControlComponent control = KidnapperControlComponent.KEY.getNullable(player);
        return control != null && control.isControlled();
    }

    /**
     * Snaps the feet to the floor: the top face a short downward ray hits, else the feet while on the ground.
     * 把脚下位置贴到地面：短距离向下射线命中的顶面，否则在站地时取脚下高度。
     */
    private static OptionalDouble findFloorY(ServerWorld world, ServerPlayerEntity player) {
        Vec3d feet = player.getPos();
        Vec3d[] probe = RiftGatePlacementRules.floorProbe(feet);
        BlockHitResult ground = world.raycast(new RaycastContext(probe[0], probe[1],
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        boolean topFace = ground.getType() == HitResult.Type.BLOCK && ground.getSide() == Direction.UP;
        return RiftGatePlacementRules.snapFloorY(topFace, ground.getPos().y, player.isOnGround(), feet.y);
    }

    /**
     * Spot checks in order: support under the footprint, play area and cull height (the front cell included), empty
     * slab, no fluid, room in front (C15), gate spacing, Seeker device clearance, forbidden neighbours.
     * 按顺序检查位置：脚印下的支撑、play area（含正前方一格）与剔除高度、薄板无方块、无流体、正前方有空间（C15）、门间距、
     * 搜寻者设备间隙、禁放邻居。
     */
    @Nullable
    private static RiftGatePlacementFailure spotFailure(ServerWorld world, Vec3d pos, Direction facing) {
        if (world.isSpaceEmpty(RiftGatePlacementRules.supportProbe(pos, facing))) {
            return RiftGatePlacementFailure.NO_FLOOR;
        }
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        Box front = RiftGatePlacementRules.frontClearanceBox(pos, facing);
        if (!RiftGatePlacementRules.withinPlayArea(playArea, pos) || !RiftGatePlacementRules.belowCullHeight(pos)
                || !RiftGatePlacementRules.boxWithin(playArea, front)) {
            return RiftGatePlacementFailure.OUT_OF_BOUNDS;
        }
        Box clearance = RiftGatePlacementRules.clearanceBox(pos, facing);
        if (!world.isSpaceEmpty(clearance)) {
            return RiftGatePlacementFailure.BLOCKED;
        }
        if (world.containsFluid(clearance)) {
            return RiftGatePlacementFailure.IN_FLUID;
        }
        // C15: players and projectiles leave through the front, so a gate facing a wall would be a dead exit. Blocks
        // only, probed with no entity context (door exemptions cannot hide a solid), like the exit search.
        // C15：人和投掷物都从正面出门，对着墙的门是死门。只查方块，且不带实体上下文（穿门豁免无法掩盖实心方块），与出门搜索一致。
        if (world.getBlockCollisions(null, front).iterator().hasNext()) {
            return RiftGatePlacementFailure.FRONT_BLOCKED;
        }
        List<Vec3d> otherCentres = new ArrayList<>();
        for (RiftGateRecord record : RiftGateRegistry.gates(world)) {
            otherCentres.add(RiftGatePlacementRules.centre(record.pos()));
        }
        if (!RiftGatePlacementRules.respectsSpacing(RiftGatePlacementRules.centre(pos), otherCentres)
                || !world.getEntitiesByClass(SeekerDeviceEntity.class,
                        RiftGatePlacementRules.deviceClearanceBox(pos, facing), Entity::isAlive).isEmpty()) {
            return RiftGatePlacementFailure.TOO_CLOSE;
        }
        for (BlockPos near : RiftGatePlacementRules.neighbourScan(pos, facing)) {
            if (RiftGateNeighbourRules.isForbiddenNeighbour(world.getBlockState(near))) {
                return RiftGatePlacementFailure.NEAR_INTERACTIVE;
            }
        }
        return null;
    }

    /**
     * Audible and visible to everyone nearby (a counterplay cue); the client adds the steady swirl particles (P6).
     * 附近所有人都能听到、看到（反制线索）；持续的旋涡粒子由客户端添加（P6）。
     */
    private static void playPlacementCue(ServerWorld world, Vec3d pos) {
        Vec3d centre = RiftGatePlacementRules.centre(pos);
        world.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, PLACE_SOUND_VOLUME, PLACE_SOUND_PITCH);
        world.spawnParticles(ParticleTypes.PORTAL, centre.x, centre.y, centre.z, PLACE_PORTAL_PARTICLES,
                0.3, 0.7, 0.3, 0.4);
        world.spawnParticles(ParticleTypes.WITCH, centre.x, centre.y, centre.z, PLACE_WITCH_PARTICLES,
                0.3, 0.6, 0.3, 0.0);
    }

    private static ActionResult refuse(ServerPlayerEntity player, RiftGatePlacementFailure failure) {
        player.sendMessage(Text.translatable(failure.messageKey()), true);
        return ActionResult.FAIL;
    }
}
