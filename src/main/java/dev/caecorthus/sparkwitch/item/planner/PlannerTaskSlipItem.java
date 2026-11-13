package dev.caecorthus.sparkwitch.item.planner;

import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 任务单 / Task slip: right-click another player to hand them one task, or right-click into the air to reroll your own.
 * 任务单：右键其他玩家可递给他一项任务；对着空气右键则重排自己的任务。
 */
public final class PlannerTaskSlipItem extends Item {

    public PlannerTaskSlipItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            ServerPlayerEntity target = crosshairPlayer(serverPlayer);
            boolean used = target != null
                    ? PlannerService.handSlipTo(serverPlayer, target)
                    : PlannerService.reorderTasks(serverPlayer);
            if (used) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack);
    }

    /** Server-side look raycast for another player within 32 blocks. 服务端 32 格视线射线，寻找其他玩家。 */
    private static ServerPlayerEntity crosshairPlayer(ServerPlayerEntity user) {
        Vec3d start = user.getCameraPosVec(1.0F);
        Vec3d direction = user.getRotationVec(1.0F);
        double reach = 32.0D;
        Vec3d end = start.add(direction.multiply(reach));
        Box box = user.getBoundingBox().stretch(direction.multiply(reach)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(user, start, end, box,
                (Entity entity) -> entity instanceof ServerPlayerEntity && entity != user && entity.isAlive(),
                reach * reach);
        return hit != null && hit.getEntity() instanceof ServerPlayerEntity target ? target : null;
    }
}
