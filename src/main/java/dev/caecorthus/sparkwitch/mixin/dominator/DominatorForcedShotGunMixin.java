package dev.caecorthus.sparkwitch.mixin.dominator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotGuns;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotScope;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe's gun receiver for the Dominator forced shot. Every hook below is keyed on {@link DominatorForcedShotScope}
 * (only the synchronous forced shot), never on the 1 s sequence window, so a shot fired by hand is never protected:
 * <ul>
 *   <li>HEAD: a hand-fired {@code wathe:gunshoot} with the marked gun in hand, or during the sequence, is refused
 *   ({@link DominatorForcedShotGuns#refusesHandShot}).</li>
 *   <li>The cooldown gate reads "ready" and the final cooldown write is skipped, so the shot neither depends on nor
 *   changes the shooter's own revolver cooldown (SparkStrength's {@code @ModifyArg} and SparkWitch's Emma fallback
 *   wrapper on the same write compose with this).</li>
 *   <li>{@code ShouldPunishGunShooter} answers cancel and the passenger mood loss is skipped: no innocent-shot
 *   punishment, backfire, gun drop or mood cost (owner decision).</li>
 * </ul>
 * Chaining {@code @WrapOperation}s only (never {@code @Redirect}); the Judge outcome wrapper on the same calls composes.
 * Wathe 枪械接收器上的支配者强制开枪钩子。以下每个钩子都以 {@link DominatorForcedShotScope}（仅同步的强制开枪）为准，绝不以
 * 1 秒序列窗口为准，因此亲手开的枪永远不受保护：HEAD 拒绝主手为标记左轮或序列进行中的亲手 {@code wathe:gunshoot}；冷却检查
 * 视为就绪且跳过最终冷却写入，使这一枪既不依赖也不改变射手自己的左轮冷却（与 SparkStrength 的 {@code @ModifyArg} 及
 * SparkWitch 艾玛回退包装可组合）；{@code ShouldPunishGunShooter} 返回取消并跳过乘客理智扣减：无误伤惩罚、走火、掉枪或理智
 * 损失（所有者决定）。只使用可串联的 {@code @WrapOperation}（绝不 {@code @Redirect}），与同一调用上的法官包装可组合。
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class DominatorForcedShotGunMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$refuseHandShotWithForcedGun(GunShootPayload payload, ServerPlayNetworking.Context context,
                                                        CallbackInfo ci) {
        if (DominatorForcedShotGuns.refusesHandShot(context.player())) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ItemCooldownManager;isCoolingDown(Lnet/minecraft/item/Item;)Z"))
    private boolean sparkwitch$forcedShotIgnoresCooldown(ItemCooldownManager manager, Item item,
                                                         Operation<Boolean> original,
                                                         GunShootPayload payload, ServerPlayNetworking.Context context) {
        return !DominatorForcedShotScope.isFiring(context.player()) && original.call(manager, item);
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V"))
    private void sparkwitch$forcedShotWritesNoCooldown(ItemCooldownManager manager, Item item, int duration,
                                                      Operation<Void> original,
                                                      GunShootPayload payload, ServerPlayNetworking.Context context) {
        if (!DominatorForcedShotScope.isFiring(context.player())) {
            original.call(manager, item, duration);
        }
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter;shouldPunish(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter$PunishResult;"))
    private ShouldPunishGunShooter.PunishResult sparkwitch$protectForcedShot(
            ShouldPunishGunShooter event, PlayerEntity shooter, PlayerEntity victim,
            Operation<ShouldPunishGunShooter.PunishResult> original) {
        return DominatorForcedShotScope.isFiring(shooter)
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : original.call(event, shooter, victim);
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerMoodComponent;setMood(F)V"))
    private void sparkwitch$skipForcedShotMood(PlayerMoodComponent mood, float amount, Operation<Void> original,
                                               GunShootPayload payload, ServerPlayNetworking.Context context) {
        if (!DominatorForcedShotScope.isFiring(context.player())) {
            original.call(mood, amount);
        }
    }
}
