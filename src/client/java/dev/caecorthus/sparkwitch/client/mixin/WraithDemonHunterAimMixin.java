package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.demonhunter.DemonHunterPistolItem;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NoellesRoles Demon Hunter pistol copies Wathe's gun selector, so it passes through active Wraiths the same way.
 * NoellesRoles 猎魔人手枪复制了 Wathe 的枪械选靶，同样需要穿过激活的冤魂。
 */
@Mixin(value = DemonHunterPistolItem.class, remap = false)
public abstract class WraithDemonHunterAimMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$aimThroughWraiths(PlayerEntity user, Operation<HitResult> original) {
        return WraithAimPassThrough.selectAs(user, () -> original.call(user));
    }
}
