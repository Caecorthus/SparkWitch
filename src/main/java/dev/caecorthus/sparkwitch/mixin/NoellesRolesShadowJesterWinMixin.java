package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.impl.WitchWinConditions;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets living Witch faction members keep blocking NoellesRoles Shadow Jester showdown wins.
 * 让存活的魔女阵营成员继续阻止 NoellesRoles 双影谢幕的影子小丑胜利。
 */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesRolesShadowJesterWinMixin {
    @Inject(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;neutralWin(Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/List;)Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;"
            ),
            cancellable = true
    )
    private static void sparkwitch$blockShadowJesterShowdownWin(
            ServerWorld world,
            GameWorldComponent gameComponent,
            GameFunctions.WinStatus currentStatus,
            CallbackInfoReturnable<CheckWinCondition.WinResult> cir
    ) {
        if (WitchWinConditions.shouldBlockShadowJesterShowdownNeutralWin(world, gameComponent)) {
            cir.setReturnValue(CheckWinCondition.WinResult.block());
        }
    }
}
