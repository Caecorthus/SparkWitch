package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Knife stabs and the knife indicator pass through active Wraiths; NoellesRoles Scavenger and SparkStrength
 * Veteran instant knives reuse this selector too.
 * 刀刺与刀准星穿过冤魂；NoellesRoles 清道夫与 SparkStrength 老兵的瞬刺也复用此选靶。
 */
@Mixin(value = KnifeItem.class, remap = false)
public abstract class WraithKnifeAimMixin {
    @WrapMethod(method = "getKnifeTarget")
    private static HitResult sparkwitch$aimThroughWraiths(PlayerEntity user, Operation<HitResult> original) {
        return WraithAimPassThrough.selectAs(user, () -> original.call(user));
    }
}
