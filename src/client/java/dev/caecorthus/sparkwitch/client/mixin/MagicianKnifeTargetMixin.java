package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetAim;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets every Wathe knife target pick (the charged stab, the NoellesRoles/SparkStrength instant stabs and Wathe's knife
 * crosshair hint) hit a Magician puppet that is strictly nearer than its pick. {@code @WrapMethod} encloses the Seeker's
 * RETURN device preference and any HEAD replacement, so the selector's full answer is compared.
 * 让所有 Wathe 刀的选靶（蓄力刺、NoellesRoles/SparkStrength 瞬刺以及 Wathe 刀准星提示）命中严格更近的魔术师皮套。
 * {@code @WrapMethod} 包住搜寻者在 RETURN 的设备优先与任何 HEAD 替换，因此比较的是选靶器的完整结果。
 */
@Mixin(value = KnifeItem.class, remap = false)
public abstract class MagicianKnifeTargetMixin {
    @WrapMethod(method = "getKnifeTarget")
    private static HitResult sparkwitch$preferNearerPuppet(PlayerEntity user, Operation<HitResult> original) {
        return MagicianPuppetAim.preferNearerPuppet(user, original.call(user), MagicianPuppetAim.KNIFE_RANGE, false);
    }
}
