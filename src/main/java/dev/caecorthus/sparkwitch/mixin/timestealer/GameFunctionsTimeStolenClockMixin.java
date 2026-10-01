package dev.caecorthus.sparkwitch.mixin.timestealer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes Wathe's +30 s civilian-death time bonus only for the Clock curse death ({@code sparkwitch:time_stolen}); the
 * Time Stealer instead takes 30 s off after the death is confirmed. It wraps the single {@code GameTimeComponent.addTime}
 * call inside the 5-argument {@code killPlayer} ({@code require = 1, allow = 1}); every other death reason still calls
 * the original, so no other kill changes. Server-only: {@code killPlayer} never runs on the client.
 * 仅对时钟诅咒死亡（{@code sparkwitch:time_stolen}）移除 Wathe 平民死亡时的 +30 秒时间奖励；窃时者改为在确认死亡后
 * 扣除 30 秒。它包住 5 参数 {@code killPlayer} 中唯一一次 {@code GameTimeComponent.addTime} 调用
 * （{@code require = 1, allow = 1}）；其他所有死因仍调用原逻辑，因此其他击杀不受影响。仅服务端：客户端从不执行 {@code killPlayer}。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsTimeStolenClockMixin {
    @WrapOperation(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameTimeComponent;addTime(I)V"),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$skipClockKillTimeBonus(GameTimeComponent time, int amount,
                                                         Operation<Void> original,
                                                         ServerPlayerEntity victim, boolean spawnBody,
                                                         @Nullable ServerPlayerEntity killer, Identifier deathReason,
                                                         boolean force) {
        if (TimeStealerRules.suppressesCivilianKillTime(deathReason)) {
            return;
        }
        original.call(time, amount);
    }
}
