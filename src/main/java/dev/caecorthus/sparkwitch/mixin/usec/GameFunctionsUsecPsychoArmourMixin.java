package dev.caecorthus.sparkwitch.mixin.usec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShieldPierce;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * O3: the AXMC pierces Wathe psycho armour, Jester-moment armour included (owner). Pinned Wathe 1.5.6 asks
 * {@code ShouldPiercePsychoArmour} once in {@code killPlayer}, after BEFORE and before its absorb branch; for the
 * scoped AXMC shot only, this strips one armour layer per pierce left, each recorded as {@code shield_blocked} with
 * source {@code sparkwitch:usec_rifle}, and returns the original answer, so Wathe itself then absorbs with the next
 * layer, or stops psycho mode and lets the death through at 0. A forced kill or an answer that already pierces is left
 * alone, and every other kill (the Ceremonial Sword included) is untouched.
 * O3：AXMC 可击穿 Wathe 疯魔护甲，含小丑时刻护甲（所有者）。固定版本 Wathe 1.5.6 在 {@code killPlayer} 中、BEFORE 之后且吸收
 * 分支之前询问一次 {@code ShouldPiercePsychoArmour}；仅对作用域内的 AXMC 射击，这里按剩余穿透次数逐层剥去护甲，每层都以来源
 * {@code sparkwitch:usec_rifle} 记为 {@code shield_blocked}，然后返回原答案，于是由 Wathe 自身用下一层吸收，或在护甲为 0 时
 * 结束疯魔并放行死亡。强制击杀或已经穿透的答案不做处理，其他所有击杀（含仪礼剑）不受影响。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsUsecPsychoArmourMixin {
    @ModifyExpressionValue(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/ShouldPiercePsychoArmour;pierces(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)Z")
    )
    private static boolean sparkwitch$pierceUsecPsychoArmour(boolean original, ServerPlayerEntity victim,
            boolean spawnBody, @Nullable ServerPlayerEntity killer, Identifier deathReason, boolean force) {
        if (original || force || !UsecShieldPierce.isScoped(victim, killer, deathReason)) {
            return original;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(victim);
        while (psycho.getPsychoTicks() > 0 && psycho.getArmour() > 0
                && UsecShieldPierce.tryPierce(victim, killer, deathReason)) {
            psycho.setArmour(psycho.getArmour() - 1);
            psycho.sync();
            victim.playSoundToPlayer(WatheSounds.ITEM_PSYCHO_ARMOUR, SoundCategory.MASTER, 5.0F, 1.0F);
            var event = GameRecordManager.event(GameRecordTypes.SHIELD_BLOCKED).actor(victim)
                    .put("source", UsecRules.RIFLE_ITEM_ID.toString()).put("death_reason", deathReason.toString())
                    .putInt("armour_remaining", psycho.getArmour());
            if (killer != null) {
                event.target(killer);
            }
            event.record();
        }
        return original;
    }
}
