package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
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
 * Places a ghost trigger on the targeted support surface. The placed trigger is invisible and spends itself when any
 * living player walks into it; nothing is returned. The item icon is used by the shop and the held stack only.
 * 在瞄准的支撑面放置鬼魂触发器。放置后的触发器隐形，任何存活玩家走进判定框即消耗，不返还物品；
 * 物品贴图只用于商店与手持物品堆。
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
            return TypedActionResult.success(stack, true);
        }

        GhostTriggerEntity trigger = new GhostTriggerEntity(SpecterEntities.ghostTrigger(), world);
        trigger.refreshPositionAndAngles(
                placement.spawnPos().x,
                placement.spawnPos().y,
                placement.spawnPos().z,
                0.0F,
                0.0F
        );
        trigger.setOwner(user);
        world.spawnEntity(trigger);
        stack.decrement(1);
        if (user instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "place");
            GameRecordManager.putBlockPos(extra, "pos", placement.supportPos());
            GameRecordManager.recordItemUse(serverPlayer, ID, null, extra);
        }
        return TypedActionResult.success(stack, false);
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
            if (!world.isSpaceEmpty(null, triggerBox)) {
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

    private record Placement(BlockPos supportPos, Vec3d spawnPos) {
    }
}
