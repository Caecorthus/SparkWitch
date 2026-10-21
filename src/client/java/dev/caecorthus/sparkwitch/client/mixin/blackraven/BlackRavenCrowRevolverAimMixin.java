package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowAim;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the Wathe revolver's client target selection (and every add-on gun that asks {@code RevolverItem.getGunTarget},
 * today SparkStrength's Serial Killer pistols) hit a Raven Form crow strictly nearer than its pick
 * ({@link BlackRavenCrowAim}). {@code @WrapMethod} chains with the Magician puppet and Wraith wrappers on the same
 * method, and the Seeker device preference in {@code RevolverItem#use} still runs on what this returns; each keeps the
 * nearer result, so nearest-wins holds in any order. Client prediction only.
 * 让 Wathe 左轮的客户端选靶（以及所有调用 {@code RevolverItem.getGunTarget} 的附属枪械，目前为 SparkStrength 连环杀手手枪）
 * 命中严格更近的化鸦乌鸦（{@link BlackRavenCrowAim}）。{@code @WrapMethod} 与同一方法上的魔术师皮套、冤魂包装串联，
 * {@code RevolverItem#use} 中的搜寻者设备优先仍作用于本方法的返回值；各自保留更近的结果，因此任意顺序下都保持“最近者命中”。
 * 仅为客户端预测。
 */
@Mixin(value = RevolverItem.class, remap = false)
public abstract class BlackRavenCrowRevolverAimMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$preferNearerCrow(PlayerEntity user, Operation<HitResult> original) {
        return BlackRavenCrowAim.preferNearerCrow(user, original.call(user), BlackRavenCrowAim.REVOLVER_RANGE);
    }
}
