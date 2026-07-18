package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateAccess;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateService;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Low-priority whole-method adapter so NoellesRoles' priority-1100 platter injections remain inside original.call.
 * 低优先级包裹整段方法，让 NoellesRoles priority 1100 的餐盘逻辑先在 original.call 内完整执行。
 */
@Mixin(value = FoodPlatterBlock.class, priority = 1_000)
public abstract class FoodPlatterBlockPoisonAppleMixin {
    @WrapMethod(method = "onUse")
    private ActionResult sparkwitch$handlePoisonApple(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            Operation<ActionResult> original
    ) {
        if (world.isClient) {
            return original.call(state, world, pos, player, hit);
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof BeveragePlateBlockEntity plate)
                || !(blockEntity instanceof PoisonApplePlateAccess poisonApple)) {
            return original.call(state, world, pos, player, hit);
        }

        PoisonApplePlateService.refreshMatch(poisonApple);
        if (PoisonApplePlateService.isHoldingPoisonApple(player)) {
            PoisonApplePlateService.tryArm(player, poisonApple);
            // A duplicate or out-of-match placement is fully rejected instead of falling into native platter logic.
            // 重复或非对局内布置会被完整拒绝，不会继续进入原生餐盘逻辑。
            return ActionResult.SUCCESS;
        }

        boolean handWasEmpty = player.getMainHandStack().isEmpty();
        boolean antidoteWasReady = PoisonApplePlateService.isReadyAntidote(player);
        boolean hadNativePoison = plate.getPoisoner() != null;
        boolean hadPoisonApple = poisonApple.sparkwitch$isPoisonAppleArmed();
        ActionResult result = original.call(state, world, pos, player, hit);

        if (hadPoisonApple && PoisonApplePlateService.cureWithAntidote(
                world,
                pos,
                player,
                plate,
                poisonApple,
                antidoteWasReady,
                hadNativePoison
        )) {
            return ActionResult.SUCCESS;
        }
        if (handWasEmpty && !player.getMainHandStack().isEmpty()) {
            PoisonApplePlateService.recordSuccessfulTake(player, poisonApple);
        }
        return result;
    }
}
