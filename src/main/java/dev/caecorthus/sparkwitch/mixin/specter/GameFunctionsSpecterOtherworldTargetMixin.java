package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Otherworld targeting rule at HEAD of Wathe's 5-arg {@code killPlayer} (Wathe member, {@code remap = false}): while the
 * world window is open, a non-forced kill credited to a Specter that the rule rejects
 * ({@link SpecterOtherworldService#blocksKill}) is cancelled, whatever weapon delivered it. HEAD callbacks run in
 * ascending mixin priority, so 1040 lands after the default-1000 immunity guards and the rope interrupt (1030), and
 * before BEAR's Rys-T ricochet (1050), the Fiend and SparkStrength's vest/Democracy Shield (1100), the Charon's pardon
 * (1200), every {@code KillPlayer.BEFORE} listener, psycho armour, the Specter shield, Tofana and the Cat: a voided
 * kill spends none of the victim's protections. What the attacker already spent before {@code killPlayer} is another
 * matter: a knife stab or gun shot on a rejected target is dropped earlier, at its receiver
 * ({@code SpecterOtherworldKnifeMixin}, {@code SpecterOtherworldGunMixin}), while a thrown or delayed weapon (grenade,
 * poison, another mod's projectile) is spent as usual and only its kill is voided here. Deaths the rule does not judge
 * are listed on {@link SpecterOtherworldService#blocksKill}.
 * 里世界索敌规则，挂在 Wathe 五参 {@code killPlayer} 的 HEAD（Wathe 成员，{@code remap = false}）：世界窗口开启时，记在幽魂
 * 名下、被规则拒绝（{@link SpecterOtherworldService#blocksKill}）的非强制击杀被取消，无论致命一击来自哪种武器。HEAD 回调
 * 按 mixin 优先级升序执行，因此 1040 位于默认 1000 的免疫守卫与绳索打断（1030）之后，并先于 BEAR Rys-T 跳弹（1050）、魔人与
 * SparkStrength 防弹衣/民主盾牌（1100）、摆渡人宽恕（1200）、所有 {@code KillPlayer.BEFORE} 监听、疯魔护甲、幽魂护盾、
 * 托法娜与猫：被作废的击杀不消耗受害者的任何保护。攻击者在 {@code killPlayer} 之前已经付出的代价则另当别论：对被拒绝目标的
 * 刀刺与枪击会更早地在各自的接收器处被丢弃（{@code SpecterOtherworldKnifeMixin}、{@code SpecterOtherworldGunMixin}），
 * 而投掷类或延迟类武器（手雷、毒、其他模组的投射物）照常消耗，只有其击杀在此被作废。不受规则判定的死亡见
 * {@link SpecterOtherworldService#blocksKill}。
 */
@Mixin(value = GameFunctions.class, remap = false, priority = 1040)
public abstract class GameFunctionsSpecterOtherworldTargetMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$blockOtherworldKillOnInvalidTarget(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (SpecterOtherworldService.blocksKill(victim, killer, deathReason, force)) {
            ci.cancel();
        }
    }
}
