package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShieldPierce;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.ModEffects;
import org.agmas.noellesroles.professor.IronManPlayerComponent;

/**
 * Pinned NoellesRoles 1.7.6 shield layers a victim carries, for the AXMC pierce loop (O3): the Iron Man buff and the
 * whiskey stack, whose layer count is the effect amplifier + 1 ({@code WhiskeyShieldEffect.consumeShield} lowers the
 * amplifier and removes the effect at the last layer). Read-only.
 * 受害者身上固定版本 NoellesRoles 1.7.6 的护盾层，供 AXMC 穿盾循环使用（O3）：铁人增益与威士忌层数，层数为效果等级 + 1
 * （{@code WhiskeyShieldEffect.consumeShield} 降低等级，最后一层时移除效果）。只读。
 */
public final class NoellesShieldLayersCompat {
    private NoellesShieldLayersCompat() {
    }

    public static UsecShieldPierce.Layers snapshot(ServerPlayerEntity victim) {
        StatusEffectInstance whiskey = victim.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        return new UsecShieldPierce.Layers(IronManPlayerComponent.KEY.get(victim).hasBuff(),
                whiskey == null ? 0 : whiskey.getAmplifier() + 1);
    }
}
