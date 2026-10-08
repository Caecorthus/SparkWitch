package dev.caecorthus.sparkwitch.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.compat.NoellesShieldLayersCompat;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShieldPierce;
import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * O3: the AXMC pierces NoellesRoles whiskey and Iron Man layers. The pinned 1.7.6 BEFORE listener (the same lambda the
 * Ceremonial Sword hooks) spends at most one of those layers per call and then cancels, so for the scoped AXMC shot
 * only ({@link UsecShieldPierce#isScoped}) it is re-invoked while each cancel spent exactly one layer and the budget
 * pays for piercing it ({@link UsecShieldPierce#pierceLayers}). Its earlier non-shield branches (Jester stasis, the
 * Jester moment, Taotie and Survival Master moments) keep running first and are never pierced; every other kill, the
 * sword included, calls the listener exactly once. Only this listener is re-invoked: KillPlayer.BEFORE fires once.
 * O3：AXMC 可击穿 NoellesRoles 威士忌与铁人护盾层。固定版本 1.7.6 的 BEFORE 监听（与仪礼剑所挂的是同一个 lambda）每次调用
 * 至多消耗其中一层后取消，因此仅对作用域内的 AXMC 射击（{@link UsecShieldPierce#isScoped}），只要每次取消恰好消耗了一层且
 * 预算支付了穿透，就再次调用它（{@link UsecShieldPierce#pierceLayers}）。其前面的非护盾分支（小丑禁锢、小丑时刻、饕餮与生存
 * 大师时刻）仍先执行且从不被击穿；其他所有击杀（含仪礼剑）都只调用该监听一次。只重复调用这一个监听：KillPlayer.BEFORE 只触发一次。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesUsecShieldPierceMixin {
    @WrapMethod(method = "lambda$registerEvents$5")
    private static @Nullable KillPlayer.KillResult sparkwitch$pierceUsecShieldLayers(ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer, Identifier deathReason,
            Operation<KillPlayer.KillResult> original) {
        if (!UsecShieldPierce.isScoped(victim, killer, deathReason)) {
            return original.call(victim, killer, deathReason);
        }
        return UsecShieldPierce.pierceLayers(
                () -> NoellesShieldLayersCompat.snapshot(victim),
                () -> original.call(victim, killer, deathReason),
                result -> result != null && result.cancelled(),
                () -> UsecShieldPierce.tryPierce(victim, killer, deathReason));
    }
}
