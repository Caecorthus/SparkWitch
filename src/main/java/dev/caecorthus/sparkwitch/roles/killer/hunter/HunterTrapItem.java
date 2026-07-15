package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

public final class HunterTrapItem extends Item {
    public static final Identifier ID = SparkWitch.id("hunter_trap");
    private static final String POISONED_KEY = "Poisoned";
    private static final double MAX_SURFACE_SEARCH_DEPTH = 2.0D;

    public HunterTrapItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(16);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hitResult = Item.raycast(world, user, RaycastContext.FluidHandling.NONE);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }

        TrapPlacement placement = findPlacement(world, hitResult.getPos());
        if (placement == null) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }

        HunterPlayerComponent component = HunterPlayerComponent.KEY.get(user);
        if (world instanceof ServerWorld serverWorld) {
            for (UUID oldTrapUuid : component.removeOldestTrapsAtCapacity()) {
                Entity oldTrap = serverWorld.getEntity(oldTrapUuid);
                if (oldTrap instanceof HunterTrapEntity hunterTrap) {
                    hunterTrap.discardTrap();
                } else if (oldTrap != null) {
                    oldTrap.discard();
                }
            }
        }

        HunterTrapEntity trap = new HunterTrapEntity(HunterEntities.hunterTrap(), world);
        trap.refreshPositionAndAngles(
                placement.spawnPos().x,
                placement.spawnPos().y,
                placement.spawnPos().z,
                0.0F,
                0.0F
        );
        trap.setOwner(user);
        trap.setSupportPos(placement.supportPos());
        trap.setPoisoned(isPoisoned(stack));
        world.spawnEntity(trap);
        component.registerTrap(trap.getUuid());
        world.playSound(
                null,
                placement.supportPos(),
                SoundEvents.BLOCK_METAL_PLACE,
                SoundCategory.PLAYERS,
                0.8F,
                1.1F
        );
        if (user instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound extra = new NbtCompound();
            GameRecordManager.putBlockPos(extra, "pos", placement.supportPos().up());
            extra.putString("action", "place");
            GameRecordManager.recordItemUse(serverPlayer, ID, null, extra);
        }
        stack.decrement(1);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 5; line++) {
            tooltip.add(Text.translatable(
                    "item.sparkwitch.hunter_trap.tooltip.line" + line
            ).formatted(Formatting.GRAY));
        }
        if (isPoisoned(stack)) {
            tooltip.add(Text.translatable("item.sparkwitch.hunter_trap.poisoned").formatted(Formatting.DARK_GREEN));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }

    public static boolean isPoisoned(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.copyNbt().getBoolean(POISONED_KEY);
    }

    public static void setPoisoned(ItemStack stack, boolean poisoned) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData == null ? new NbtCompound() : customData.copyNbt();
        nbt.putBoolean(POISONED_KEY, poisoned);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static TrapPlacement findPlacement(World world, Vec3d hitPos) {
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
            Box trapBox = HunterEntities.hunterTrap().getDimensions().getBoxAt(spawnPos.x, spawnPos.y, spawnPos.z);
            if (!world.isSpaceEmpty(null, trapBox)) {
                continue;
            }
            bestTopY = topY;
            bestSupportPos = supportPos.toImmutable();
        }

        return bestSupportPos == null
                ? null
                : new TrapPlacement(
                        bestSupportPos,
                        new Vec3d(bestSupportPos.getX() + 0.5D, bestTopY, bestSupportPos.getZ() + 0.5D)
                );
    }

    private record TrapPlacement(BlockPos supportPos, Vec3d spawnPos) {
    }
}
