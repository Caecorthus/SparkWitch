package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Specter's Otherworld shield inside Wathe's 5-arg {@code killPlayer} (Wathe member, {@code remap = false}), at the
 * first {@code PlayerPsychoComponent.getPsychoTicks()} call. In pinned Wathe 1.5.6 that call is the third operand of
 * {@code !force && !piercesArmour && psycho.getPsychoTicks() > 0}, so it is reached only after every
 * {@code KillPlayer.BEFORE} listener, for a non-forced kill whose final {@code ShouldPiercePsychoArmour} answer (with
 * the Ceremonial Sword and AXMC expression hooks already applied) does not pierce. There one shield layer is spent
 * exactly like a psycho-armour layer ({@link SpecterSecondSkillService#absorbLethalHit}) and the kill is cancelled,
 * before Wathe's own psycho armour; with no layer left nothing changes. It never reads or writes psycho state.
 * 幽魂里世界护盾，挂在 Wathe 五参 {@code killPlayer}（Wathe 成员，{@code remap = false}）中第一次
 * {@code PlayerPsychoComponent.getPsychoTicks()} 调用处。在固定版本 Wathe 1.5.6 中该调用是
 * {@code !force && !piercesArmour && psycho.getPsychoTicks() > 0} 的第三个操作数，因此只会在所有 {@code KillPlayer.BEFORE}
 * 监听之后、且仅对最终 {@code ShouldPiercePsychoArmour} 结果（已应用仪礼剑与 AXMC 的表达式钩子）不穿盾的非强制击杀到达。
 * 在此按疯魔护甲的方式消耗 1 层护盾（{@link SpecterSecondSkillService#absorbLethalHit}）并取消击杀，先于 Wathe 自身的疯魔护甲；
 * 没有剩余护盾时不做任何改变。从不读取或写入疯魔状态。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class GameFunctionsSpecterShieldMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerPsychoComponent;getPsychoTicks()I",
                    ordinal = 0
            ),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private static void sparkwitch$absorbWithOtherworldShield(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (!force && SpecterSecondSkillService.absorbLethalHit(victim, killer, deathReason)) {
            ci.cancel();
        }
    }
}
