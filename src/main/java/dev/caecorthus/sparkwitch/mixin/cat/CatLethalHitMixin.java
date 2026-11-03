package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over a lethal hit against the cat.
 * <p>Knife and gun kills pass {@code force=true}, so {@code KillPlayer.BEFORE} cannot veto them and the cat would
 * simply die. Intercepting at the entry of {@code killPlayer} is the only place that sees every lethal path.
 * 接管针对猫的致命伤。刀与枪传入 {@code force=true}，{@code KillPlayer.BEFORE} 否决不了，
 * 猫会直接死亡；{@code killPlayer} 入口是唯一能看到全部致命路径的位置。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class CatLethalHitMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void sparkwitch$catTakeover(ServerPlayerEntity victim, boolean spawnBody,
                                               @Nullable ServerPlayerEntity killer,
                                               Identifier deathReason, boolean force,
                                               CallbackInfo ci) {
        if (SchrodingersCatService.interceptLethalHit(victim, killer, deathReason)) {
            ci.cancel();
        }
    }
}
