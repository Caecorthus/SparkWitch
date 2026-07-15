package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Starts or refreshes one ten-second sanity-restoration timer on the target.
 * 为目标启动或刷新唯一的一段十秒理智恢复计时。
 */
public final class CologneItem extends Item {
    public CologneItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)
                || !apply(serverUser, serverUser, stack)) {
            return TypedActionResult.fail(stack);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverUser)
                || !(target instanceof ServerPlayerEntity serverTarget)) {
            return ActionResult.PASS;
        }
        return apply(serverUser, serverTarget, stack) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    private static boolean apply(ServerPlayerEntity user, ServerPlayerEntity target, ItemStack stack) {
        boolean samePlayer = user.getUuid().equals(target.getUuid());
        boolean allowed = PerfumerRules.canApplyCologne(
                PerfumerRuntime.isActivePerfumer(user),
                PerfumerRuntime.isActivePlayer(user),
                PerfumerRuntime.isActivePlayer(target),
                samePlayer,
                user.squaredDistanceTo(target),
                samePlayer || user.canSee(target)
        );
        if (!allowed) {
            return false;
        }

        PerfumerPlayerComponent.KEY.get(target).startCologne();
        stack.decrementUnlessCreative(1, user);
        if (samePlayer) {
            user.sendMessage(Text.translatable("message.sparkwitch.cologne.used_self"), true);
        } else {
            user.sendMessage(
                    Text.translatable("message.sparkwitch.cologne.used_target", target.getDisplayName()),
                    true
            );
            target.sendMessage(Text.translatable("message.sparkwitch.cologne.received"), true);
        }
        return true;
    }
}
