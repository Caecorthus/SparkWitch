package dev.caecorthus.sparkwitch.mixin.defensevial;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.item.defense.DefenseVialTrays;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pours a main-hand Defense Vial into a Wathe food platter / drinks tray (server side). It acts only while the vial is
 * held, a case no Wathe, NoellesRoles, SparkStrength or SparkTraits branch of {@code onUse} handles, so its HEAD order
 * among their injectors does not matter; the Witch Maiden's whole-method wrapper encloses it. Outside a running round
 * it does nothing and Wathe's own logic runs. The client keeps Wathe's unconditional SUCCESS.
 * 把主手的防御药剂倒进 Wathe 餐盘 / 饮料托盘（服务端）。只在手持药剂时生效，而 Wathe、NoellesRoles、SparkStrength、
 * SparkTraits 的 {@code onUse} 分支都不处理这种情况，因此它在这些注入之间的 HEAD 顺序无关紧要；巫女的整段方法包裹会包住它。
 * 不在进行中的回合时不做任何事，交给 Wathe 自身逻辑。客户端沿用 Wathe 无条件返回的 SUCCESS。
 */
@Mixin(FoodPlatterBlock.class)
public abstract class FoodPlatterBlockDefenseVialPourMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$pourDefenseVial(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()
                || !(player instanceof ServerPlayerEntity pourer)
                || !pourer.getMainHandStack().isOf(SparkWitchItems.defenseVial())
                || !(world.getBlockEntity(pos) instanceof BeveragePlateBlockEntity plate)) {
            return;
        }
        if (DefenseVialTrays.tryPour(pourer, plate, pos)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
