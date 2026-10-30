package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightKillService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Knight: 10 s revolver base cooldown instead of 15 s and no -0.35 mood per gun
 * kill, matching what Wathe grants the Vigilante and the Veteran. Innocent-shot punishment is unchanged. An additive
 * OR-wrap (never a {@code @Redirect}), so it composes with the Judge, Emma, Control Expert and Seeker wrappers.
 * 骑士的原生义警枪械对等：左轮基础冷却 10 秒而非 15 秒、枪杀不扣 0.35 理智，与 Wathe 赋予义警、老兵的效果一致；
 * 误杀惩罚不变。叠加式 OR 包装（绝不使用 {@code @Redirect}），与法官、艾玛、控场专家、搜寻者的包装可任意组合。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class KnightPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$knightPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                      Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || (queriedRole == WatheRoles.VIGILANTE && KnightRules.isKnight(game.getRole(player)));
    }

    /**
     * Wathe's target kill (ordinal 1 at line 116; ordinal 0 is the revolver backfire self-kill at line 93). The gun is
     * Wathe's own {@code mainHandStack} local (the only ItemStack local of {@code receive}), and the wrong-shot check
     * runs before the kill, while the victim is alive. Only a derringer shot at a good player costs sanity; a revolver
     * misfire keeps Wathe's punishment alone. The no-refill rule for kills lives in {@code KnightDerringerReloadMixin}.
     * Wathe 的目标击杀（ordinal 1，第 116 行；ordinal 0 是第 93 行的左轮走火自杀）。枪械取 Wathe 自身的
     * {@code mainHandStack} 局部变量（{@code receive} 中唯一的 ItemStack 局部变量），误射判断在击杀前、受害者仍存活时进行。
     * 只有德林加射击好人才扣理智；左轮误杀只保留 Wathe 的惩罚。击杀不补弹规则位于 {@code KnightDerringerReloadMixin}。
     */
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$knightDerringerOutcome(ServerPlayerEntity victim, boolean spawnBody,
                                                   ServerPlayerEntity shooter, Identifier reason,
                                                   Operation<Void> original, @Local ItemStack mainHandStack) {
        boolean wrongDerringerShot = KnightKillService.isWrongDerringerShot(shooter, victim, mainHandStack);
        boolean spentBeforeKill = Boolean.TRUE.equals(mainHandStack.get(WatheDataComponentTypes.USED));
        original.call(victim, spawnBody, shooter, reason);
        if (wrongDerringerShot) {
            KnightKillService.afterWrongDerringerShot(shooter, victim, mainHandStack, spentBeforeKill);
        }
    }
}
