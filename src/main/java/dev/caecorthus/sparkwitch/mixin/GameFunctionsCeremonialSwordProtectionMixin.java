package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Pinned Wathe 1.5.6: preserve one armour cost, then use native psycho cleanup and normal death.
 * 锁定 Wathe 1.5.6：保留一层疯魔盾消耗，再走原生疯魔清理与正常死亡。 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsCeremonialSwordProtectionMixin {
    @ModifyExpressionValue(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/ShouldPiercePsychoArmour;pierces(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)Z"),
            require = 1, allow = 1
    )
    private static boolean sparkwitch$consumePsychoThenPierce(boolean original, ServerPlayerEntity victim,
            boolean spawnBody, @Nullable ServerPlayerEntity killer, Identifier deathReason, boolean force) {
        if (!CeremonialSwordProtectionPolicy.pierces(deathReason)) {
            return original;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(victim);
        if (!force && psycho.getPsychoTicks() > 0 && psycho.getArmour() > 0) {
            psycho.setArmour(psycho.getArmour() - 1);
            psycho.sync();
            victim.playSoundToPlayer(WatheSounds.ITEM_PSYCHO_ARMOUR, SoundCategory.MASTER, 5.0F, 1.0F);
            var event = GameRecordManager.event("shield_blocked").actor(victim)
                    .put("source", "wathe:psycho_mode").put("death_reason", deathReason.toString())
                    .putInt("armour_remaining", psycho.getArmour());
            if (killer != null) {
                event.target(killer);
            }
            event.record();
        }
        return true;
    }

    // Noelles' allowWithoutBody() would stop all later BEFORE listeners. Move only that sword case.
    // Noelles 的 allowWithoutBody() 会终止后续 BEFORE 监听；仅迁移仪礼剑的无尸体分支。
    @ModifyVariable(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, allow = 1
    )
    private static boolean sparkwitch$preserveSwallowedBodyRule(boolean original, ServerPlayerEntity victim,
            boolean spawnBody, @Nullable ServerPlayerEntity killer, Identifier deathReason, boolean force) {
        return original && !(CeremonialSwordProtectionPolicy.pierces(deathReason)
                && SwallowedPlayerComponent.KEY.get(victim).isSwallowed());
    }
}
