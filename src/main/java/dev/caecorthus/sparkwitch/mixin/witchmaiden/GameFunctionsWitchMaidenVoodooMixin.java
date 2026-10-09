package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenFeatureService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the final NoellesRoles Voodoo death of a Witch Maiden before Wathe's first-result-wins
 * {@code KillPlayer.BEFORE}, so no earlier listener (NoellesRoles' swallowed-victim {@code allowWithoutBody()},
 * SparkTraits Last Stand) can let it through or react to it, and no psycho armour or Tofana pays for it.
 * 在 Wathe 首个结果短路的 {@code KillPlayer.BEFORE} 之前取消巫女的 NoellesRoles 巫毒最终死亡，避免更早的监听
 * （NoellesRoles 对被吞者的 {@code allowWithoutBody()}、SparkTraits 背水一战）放行或响应它，也不消耗疯魔护甲或托法娜。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsWitchMaidenVoodooMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private static void sparkwitch$blockWitchMaidenVoodoo(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (WitchMaidenFeatureService.blocksKill(victim, deathReason, force)) {
            ci.cancel();
        }
    }
}
