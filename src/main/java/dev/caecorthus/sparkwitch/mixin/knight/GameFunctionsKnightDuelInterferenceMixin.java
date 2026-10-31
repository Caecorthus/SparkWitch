package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelInterference;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every lethal attack (gun, knife, bat, cross sword, role abilities) funnels through {@code GameFunctions.killPlayer}.
 * Checking at HEAD punishes a third-party attacker even when the duelist's own traits or shields cancel the kill; the
 * victim's kill is never cancelled here.
 * 所有致命攻击（枪、刀、球棒、十字剑、职业技能）都经过 {@code GameFunctions.killPlayer}。在 HEAD 检查可在决斗者自身
 * 词条或护盾取消击杀时仍处死第三人；这里绝不取消受害者的击杀。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsKnightDuelInterferenceMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"), require = 1, allow = 1
    )
    private static void sparkwitch$punishDuelInterference(ServerPlayerEntity victim, boolean spawnBody,
            @Nullable ServerPlayerEntity killer, Identifier deathReason, boolean force, CallbackInfo ci) {
        KnightDuelInterference.onKillPlayer(victim, killer, deathReason);
    }
}
