package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowAim;
import dev.doctor4t.wathe.item.DerringerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the Wathe derringer's client target selection hit a Raven Form crow strictly nearer than its pick
 * ({@link BlackRavenCrowAim}). {@code @WrapMethod} chains with the Magician puppet and Wraith wrappers on the same
 * method; each keeps the nearer result, so nearest-wins holds in any order. Client prediction only.
 * 让 Wathe 德林加的客户端选靶命中严格更近的化鸦乌鸦（{@link BlackRavenCrowAim}）。{@code @WrapMethod} 与同一方法上的
 * 魔术师皮套、冤魂包装串联；各自保留更近的结果，因此任意顺序下都保持“最近者命中”。仅为客户端预测。
 */
@Mixin(value = DerringerItem.class, remap = false)
public abstract class BlackRavenCrowDerringerAimMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$preferNearerCrow(PlayerEntity user, Operation<HitResult> original) {
        return BlackRavenCrowAim.preferNearerCrow(user, original.call(user), BlackRavenCrowAim.DERRINGER_RANGE);
    }
}
