package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowAim;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the Wathe knife's client target selection hit a Raven Form crow strictly nearer than its pick
 * ({@link BlackRavenCrowAim}); every caller sees it: the charged release, the instant stabs, the knife crosshair and
 * the Swordfish. {@code @WrapMethod} chains with the Magician puppet, Seeker device and Wraith wrappers on the same method;
 * each keeps the nearer result, so nearest-wins holds in any order. Client prediction only.
 * 让 Wathe 刀的客户端选靶命中严格更近的化鸦乌鸦（{@link BlackRavenCrowAim}）；所有调用方都会看到：蓄力释放、瞬刺、刀准星与
 * 剑鱼。{@code @WrapMethod} 与同一方法上的魔术师皮套、搜寻者设备、冤魂包装串联；各自保留更近的结果，因此任意顺序下都保持
 * “最近者命中”。仅为客户端预测。
 */
@Mixin(value = KnifeItem.class, remap = false)
public abstract class BlackRavenCrowKnifeAimMixin {
    @WrapMethod(method = "getKnifeTarget")
    private static HitResult sparkwitch$preferNearerCrow(PlayerEntity user, Operation<HitResult> original) {
        return BlackRavenCrowAim.preferNearerCrow(user, original.call(user), BlackRavenCrowAim.KNIFE_RANGE);
    }
}
