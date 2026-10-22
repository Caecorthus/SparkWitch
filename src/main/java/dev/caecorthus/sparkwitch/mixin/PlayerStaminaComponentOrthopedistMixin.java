package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistEffects;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistStaminaRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reduces actual Wathe stamina consumption without changing maximum stamina or stacking both sources.
 * 直接降低 Wathe 的真实体力消耗，不改体力上限，也不叠加职业被动与正骨。
 */
@Mixin(PlayerStaminaComponent.class)
public abstract class PlayerStaminaComponentOrthopedistMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Shadow
    private float sprintingTicks;

    @ModifyVariable(method = "setSprintingTicks(F)V", at = @At("HEAD"), argsOnly = true)
    private float sparkwitch$reduceOrthopedistStaminaConsumption(float requestedValue) {
        // Every stamina write on both sides: adjustedValue returns requestedValue unchanged for a non-consuming write
        // or an inactive reduction, so those exit before the injury lookup (all lookups are side-effect free).
        // 双端每次体力写入都会执行：对非消耗写入或未生效的减免，adjustedValue 原样返回 requestedValue，因此在读取伤势
        // 之前即退出（所有查询均无副作用）。
        if (requestedValue >= sprintingTicks) {
            return requestedValue;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        boolean reductionActive = role != null && OrthopedistRules.ROLE_ID.equals(role.identifier())
                || player.hasStatusEffect(OrthopedistEffects.boneSetting());
        if (!reductionActive) {
            return requestedValue;
        }
        HunterPlayerComponent injury = HunterPlayerComponent.KEY.get(player);
        boolean hunterInjuryControlsMovement = injury.isRooted() || injury.getFractureLayers() > 0;
        return OrthopedistStaminaRules.adjustedValue(
                sprintingTicks,
                requestedValue,
                reductionActive,
                hunterInjuryControlsMovement
        );
    }
}
