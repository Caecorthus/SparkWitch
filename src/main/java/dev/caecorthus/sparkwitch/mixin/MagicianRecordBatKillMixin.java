package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianRecordedAction;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records a recording Magician's full-charge bat kill. Wathe's bat kill lives in its own {@code PlayerEntity.attack}
 * wrapper and never reaches the plain attack chain, so the only accepted-swing anchor is its
 * {@code killPlayer(..., BAT)} call; a weak swing is recorded as an ordinary ATTACK (a punch). Never cancels.
 * 记录录制中魔术师的满蓄力球棒击杀。Wathe 的球棒击杀位于其自身的 {@code PlayerEntity.attack} 包装中，从不进入普通攻击链，
 * 因此唯一的“已接受挥击”锚点是其 {@code killPlayer(..., BAT)} 调用；弱挥击按普通 ATTACK（一拳）录制。从不取消。
 */
@Mixin(GameFunctions.class)
public abstract class MagicianRecordBatKillMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD")
    )
    private static void sparkwitch$recordMagicianBatKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (killer != null && GameConstants.DeathReasons.BAT.equals(deathReason)) {
            MagicianServerHooks.record(killer, MagicianRecordedAction.Type.BAT_HIT);
        }
    }
}
