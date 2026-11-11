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
 * world window is open, a non-forced {@code wathe:knife} kill credited to a Specter that the rule rejects
 * ({@link SpecterOtherworldService#blocksKill}) is cancelled with no side effects. HEAD callbacks run in ascending
 * mixin priority, so 1040 lands after the default-1000 immunity guards and before BEAR's Rys-T ricochet (1050), the
 * Fiend and SparkStrength's vest/Democracy Shield (1100), every {@code KillPlayer.BEFORE} listener, psycho armour, the
 * Specter shield, Tofana and the Cat: a voided stab spends nothing. Every other death reason is untouched.
 * 里世界索敌规则，挂在 Wathe 五参 {@code killPlayer} 的 HEAD（Wathe 成员，{@code remap = false}）：世界窗口开启时，记在幽魂
 * 名下、被规则拒绝（{@link SpecterOtherworldService#blocksKill}）的非强制 {@code wathe:knife} 击杀被无副作用地取消。HEAD 回调
 * 按 mixin 优先级升序执行，因此 1040 位于默认 1000 的免疫守卫之后，并先于 BEAR Rys-T 跳弹（1050）、魔人与 SparkStrength
 * 防弹衣/民主盾牌（1100）、所有 {@code KillPlayer.BEFORE} 监听、疯魔护甲、幽魂护盾、托法娜与猫：被作废的刺击不消耗任何东西。
 * 其他死因不受影响。
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
