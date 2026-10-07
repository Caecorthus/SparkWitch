package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendBombLedger;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles 1.7.6, members {@code remap = false}): feeds {@link FiendBombLedger}. The component
 * owner {@code player} is the passer in {@code transferBomb} and the holder in {@code placeBomb}/{@code explode}. The
 * transfer is observed at its {@code recordItemUse} INVOKE, reached only after a successful hand-off and before
 * SparkWitch's record-level Vendetta suppression. Both {@code explode} kills are wrapped like
 * {@code JudgeBomberAttributionMixin} (chained {@code @WrapOperation}, the kill still runs exactly once).
 * 外部接缝（锁定 NoellesRoles 1.7.6，成员 {@code remap = false}）：为 {@link FiendBombLedger} 提供数据。组件拥有者
 * {@code player} 在 {@code transferBomb} 中是转手者，在 {@code placeBomb}/{@code explode} 中是持有者。转手在其
 * {@code recordItemUse} 调用处观察，只有成功转手后才会到达，且早于 SparkWitch 记录层的仇杀屏蔽。{@code explode} 的两处击杀
 * 按 {@code JudgeBomberAttributionMixin} 的方式包裹（链式 {@code @WrapOperation}，击杀仍恰好执行一次）。
 */
@Mixin(value = BomberPlayerComponent.class, remap = false)
public abstract class BomberPlayerComponentFiendLedgerMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "transferBomb(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$recordFiendPass(PlayerEntity target, CallbackInfo ci) {
        FiendBombLedger.onTransfer(player, target);
    }

    @Inject(method = "placeBomb(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"))
    private void sparkwitch$forgetFiendPassOnNewBomb(PlayerEntity bomber, CallbackInfo ci) {
        FiendBombLedger.onPlaced(player);
    }

    @WrapOperation(method = "explode()V", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"),
            require = 2)
    private void sparkwitch$creditFiendPass(ServerPlayerEntity victim, boolean spawnBody, ServerPlayerEntity killer,
                                           Identifier reason, Operation<Void> original) {
        FiendBombLedger.onExplosionKill(player, victim, () -> original.call(victim, spawnBody, killer, reason));
    }

    @Inject(method = "explode()V", at = @At("TAIL"))
    private void sparkwitch$closeFiendPass(CallbackInfo ci) {
        FiendBombLedger.onExplosionEnd(player);
    }
}
