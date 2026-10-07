package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetAim;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets the Wathe revolver's client target selection hit a Magician puppet that is strictly nearer than its pick. The
 * selector's own result is computed first; {@code @WrapMethod} encloses other mods' HEAD replacements (SparkTraits
 * Marksman range) and the Wraith pass-through scope, and the Seeker device preference in {@code RevolverItem#use}
 * still runs on what this returns, so nearest-wins holds across players, puppets and devices.
 * 让 Wathe 左轮的客户端选靶命中严格更近的魔术师皮套。先计算选靶器自身结果；{@code @WrapMethod} 包住其他模组的 HEAD 替换
 * （SparkTraits 神射手射程）与冤魂穿透作用域，{@code RevolverItem#use} 中的搜寻者设备优先仍作用于本方法的返回值，
 * 因此玩家、皮套与设备之间保持“最近者命中”。
 */
@Mixin(value = RevolverItem.class, remap = false)
public abstract class MagicianRevolverTargetMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$preferNearerPuppet(PlayerEntity user, Operation<HitResult> original) {
        return MagicianPuppetAim.preferNearerPuppet(user, original.call(user), MagicianPuppetAim.REVOLVER_RANGE,
                false);
    }
}
