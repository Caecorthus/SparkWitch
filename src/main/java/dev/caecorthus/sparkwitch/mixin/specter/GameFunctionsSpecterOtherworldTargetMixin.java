package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Otherworld targeting rule on Wathe's 5-arg {@code killPlayer} (Wathe member, {@code remap = false}), before every
 * {@code KillPlayer.BEFORE} listener, psycho armour and Tofana: a kill credited to a Specter whose window is open only
 * lands on good, real-mood players at or below 25 sanity. Other victims take no damage and spend no shields.
 * 里世界索敌规则，挂在 Wathe 五参 {@code killPlayer} 上（Wathe 成员，{@code remap = false}），先于所有
 * {@code KillPlayer.BEFORE} 监听、疯魔护甲与托法娜：幽魂窗口开启时，只有真实理智不高于 25 的好人会成为击杀目标；
 * 其他受害者不受伤、不消耗护盾。
 */
@Mixin(value = GameFunctions.class, remap = false, priority = 1100)
public abstract class GameFunctionsSpecterOtherworldTargetMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$blockOtherworldKillOnInvalidTarget(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (SpecterOtherworldService.blocksKill(victim, killer)) {
            ci.cancel();
        }
    }
}
