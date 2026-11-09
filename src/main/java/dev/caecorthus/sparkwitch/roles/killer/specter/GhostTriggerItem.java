package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Places a ghost trigger on the targeted support surface. The placed trigger is invisible and spends itself when an
 * eligible player walks into it; nothing is returned. The item icon is used by the shop and the held stack only.
 * <ul>
 *   <li>Server authority: the shared Specter caster gate ({@link SpecterRules#mayCast}: exactly ACTIVE round, raw
 *       Specter, alive and playing, not swallowed/held/silenced/role-skill blocked), then the Grand Witch Fear check.
 *       A refused use consumes nothing; outside an ACTIVE round the stack is never spent.</li>
 *   <li>Placement: a free support surface within 2 blocks below the hit point whose box is empty, and never a block
 *       that already holds a ghost trigger (no stacking). The client applies the same geometry to decide its swing.</li>
 * </ul>
 * 在瞄准的支撑面放置鬼魂触发器。放置后的触发器隐形，符合条件的玩家走进判定框即消耗，不返还物品；物品贴图只用于商店与手持物品堆。
 * 服务端权威：先过幽魂共用施放者门槛（{@link SpecterRules#mayCast}：对局恰为 ACTIVE、真实职业为幽魂、存活且参与对局、
 * 未被吞下/控制/沉默/封锁职业技能），再检查大魔女恐惧；被拒绝的使用不消耗任何东西，非 ACTIVE 回合绝不消耗物品。
 * 放置：命中点下方 2 格内判定框为空的支撑面，且该方块上不能已有鬼魂触发器（不可叠放）；客户端用相同的几何判定决定是否挥手。
 */
public final class GhostTriggerItem extends Item {
    public static final Identifier ID = SparkWitch.id("ghost_trigger");
    private static final double MAX_SURFACE_SEARCH_DEPTH = 2.0D;

    public GhostTriggerItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(16);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (SparkTraitsKillerBridge.isKillerInteractionBlocked(user)) {
            return TypedActionResult.fail(stack);
        }
        BlockHitResult hitResult = Item.raycast(world, user, RaycastContext.FluidHandling.NONE);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }
        Placement placement = findPlacement(world, hitResult.getPos());
        if (placement == null) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) {
            // Swing prediction only; the server re-checks everything below. / 仅预测挥手；服务端会重新检查下方全部条件。
            return mayPlausiblyPlace(world, user)
                    ? TypedActionResult.success(stack, true)
                    : TypedActionResult.pass(stack);
        }
        if (!(user instanceof ServerPlayerEntity caster) || !SpecterRules.mayCast(caster)) {
            return TypedActionResult.fail(stack);
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return TypedActionResult.fail(stack);
        }

        GhostTriggerEntity trigger = new GhostTriggerEntity(SpecterEntities.ghostTrigger(), world);
        trigger.refreshPositionAndAngles(
                placement.spawnPos().x,
                placement.spawnPos().y,
                placement.spawnPos().z,
                0.0F,
                0.0F
        );
        trigger.setOwner(caster);
        trigger.bindToCurrentMatch();
        if (!world.spawnEntity(trigger)) {
            return TypedActionResult.fail(stack);
        }
        stack.decrement(1);
        NbtCompound extra = new NbtCompound();
        extra.putString("action", "place");
        GameRecordManager.putBlockPos(extra, "pos", placement.supportPos());
        GameRecordManager.recordItemUse(caster, ID, null, extra);
        return TypedActionResult.success(stack, false);
    }

    /**
     * Side-neutral subset of {@link SpecterRules#mayCast} the client can read (ACTIVE round, raw Specter, alive and
     * playing, not spectating or swallowed), so a doomed use does not swing.
     * 客户端可读取的 {@link SpecterRules#mayCast} 两端通用子集（ACTIVE 回合、真实职业为幽魂、存活且参与对局、非旁观且未被吞下），
     * 使注定失败的使用不挥手。
     */
    private static boolean mayPlausiblyPlace(World world, PlayerEntity user) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && SpecterRules.isSpecter(game.getRole(user))
                && GameFunctions.isPlayerPlayingAndAlive(user)
                && !GameFunctions.isPlayerSpectatingOrCreative(user)
                && !NoellesTaotieSeekerBridge.isSwallowed(user);
    }

    private static Placement findPlacement(World world, Vec3d hitPos) {
        int minBlockY = MathHelper.floor(hitPos.y - MAX_SURFACE_SEARCH_DEPTH);
        int maxBlockY = MathHelper.floor(hitPos.y);
        double bestTopY = Double.NEGATIVE_INFINITY;
        BlockPos bestSupportPos = null;

        for (int y = maxBlockY; y >= minBlockY; y--) {
            BlockPos supportPos = BlockPos.ofFloored(hitPos.x, y, hitPos.z);
            VoxelShape collisionShape = world.getBlockState(supportPos).getCollisionShape(world, supportPos);
            if (collisionShape.isEmpty()) {
                continue;
            }
            double topY = supportPos.getY() + collisionShape.getMax(Direction.Axis.Y);
            if (topY <= bestTopY || hitPos.y < topY - 1.0E-4D) {
                continue;
            }
            Vec3d spawnPos = new Vec3d(supportPos.getX() + 0.5D, topY, supportPos.getZ() + 0.5D);
            Box triggerBox = SpecterEntities.ghostTrigger().getDimensions().getBoxAt(spawnPos.x, spawnPos.y, spawnPos.z);
            if (!world.isSpaceEmpty(null, triggerBox) || holdsTrigger(world, supportPos)) {
                continue;
            }
            bestTopY = topY;
            bestSupportPos = supportPos.toImmutable();
        }

        return bestSupportPos == null
                ? null
                : new Placement(
                        bestSupportPos,
                        new Vec3d(bestSupportPos.getX() + 0.5D, bestTopY, bestSupportPos.getZ() + 0.5D)
                );
    }

    /**
     * Whether a ghost trigger already sits on this support block (its column, one block up), so two triggers never
     * stack on one spot. Triggers are tracked to clients (Hunter trap precedent), so both sides agree.
     * 该支撑方块上（其所在列，向上一格内）是否已有鬼魂触发器，避免两个触发器叠放在同一处。触发器会被客户端追踪（猎人捕兽夹先例），
     * 因此两端判定一致。
     */
    private static boolean holdsTrigger(World world, BlockPos supportPos) {
        Box column = new Box(supportPos).stretch(0.0D, 1.0D, 0.0D);
        return !world.getEntitiesByClass(GhostTriggerEntity.class, column, trigger -> !trigger.isRemoved()).isEmpty();
    }

    private record Placement(BlockPos supportPos, Vec3d spawnPos) {
    }
}
