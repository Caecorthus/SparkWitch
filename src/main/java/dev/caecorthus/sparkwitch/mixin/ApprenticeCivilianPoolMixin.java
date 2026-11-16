package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeCivilianPool;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Server-side seam on Wathe's civilian draw: every {@code addRole} in {@code assignCivilians} passes through, and only
 * the plain-Civilian fallback seats may become an Apprentice Witch (ApprenticeCivilianPool). Special civilian picks,
 * group seats and the host's order are untouched.
 * Wathe 平民抽取的服务端接缝：{@code assignCivilians} 中每次 {@code addRole} 都经过这里，只有普通平民兜底席位可能变成
 * 预备魔女（ApprenticeCivilianPool）。特殊平民抽取、成组席位与宿主顺序不变。
 */
@Mixin(ScoreboardRoleSelectorComponent.class)
public abstract class ApprenticeCivilianPoolMixin {
    @WrapOperation(method = "assignCivilians", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;addRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)V"))
    private void sparkwitch$shareFallbackSeatsWithApprentice(GameWorldComponent game, PlayerEntity player, Role role,
                                                            Operation<Void> original,
                                                            @Local(argsOnly = true) ServerWorld world) {
        original.call(game, player, ApprenticeCivilianPool.chooseSeatRole(game, role, world.getRandom()));
    }
}
