package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenSettlement;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Plan v2 §3.3 (owner 2026-10-21): Wathe's civilian-death time bonus ({@code TIME_ON_CIVILIAN_KILL}) is settlement,
 * so a dying Black Raven adds no round time. It wraps the single {@code GameTimeComponent.addTime} call inside the
 * 5-argument {@code killPlayer} ({@code require = 1, allow = 1}), next to the Time Stealer's Clock wrap on the same call
 * (MixinExtras chains both). The raw role is read after {@code markPlayerDead}, which never changes it; every other
 * victim still calls the original. Server only.
 * 计划 v2 §3.3（所有者 2026-10-21）：Wathe 的好人死亡加时（{@code TIME_ON_CIVILIAN_KILL}）属于结算，因此黑羽鸦死亡不再为
 * 回合加时。包装 5 参数 {@code killPlayer} 中唯一一次 {@code GameTimeComponent.addTime} 调用
 * （{@code require = 1, allow = 1}），与窃时者时钟对同一调用的包装并存（MixinExtras 会串联两者）。原始职业在
 * {@code markPlayerDead} 之后读取，该操作不会改变它；其他受害者照常调用原逻辑。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsBlackRavenTimeBonusMixin {
    @WrapOperation(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameTimeComponent;addTime(I)V"),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$skipBlackRavenDeathTimeBonus(GameTimeComponent time, int amount,
                                                               Operation<Void> original,
                                                               ServerPlayerEntity victim, boolean spawnBody,
                                                               @Nullable ServerPlayerEntity killer,
                                                               Identifier deathReason, boolean force) {
        if (BlackRavenSettlement.isExcluded(victim)) {
            return;
        }
        original.call(time, amount);
    }
}
