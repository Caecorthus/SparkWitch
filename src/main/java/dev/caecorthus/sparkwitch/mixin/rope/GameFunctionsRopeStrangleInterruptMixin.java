package dev.caecorthus.sparkwitch.mixin.rope;

import dev.caecorthus.sparkwitch.item.rope.RopeStrangleService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rope interrupt ("若你期间受到攻击则立刻中止") at HEAD of Wathe's 5-arg {@code killPlayer} (Wathe member,
 * {@code remap = false}): every lethal attack funnels through it, so a kill attempt on a strangling player by another
 * player breaks the strangle here. A pure observer, it never cancels and never changes an argument. Order is the
 * contract: HEAD callbacks run in ascending mixin priority, so 1030 runs after the default-1000 structural guards
 * (SparkFactionAPI's affect veto, the Saint guard, Vendetta, the forced-shot guard: an attack they refuse is no attack,
 * the Fiend's precedent) and before everything that can block or absorb a real attack: the Specter's Otherworld void
 * (1040), the Rys-T ricochet (1050), the Fiend and SparkStrength's vest (1100), the Charon's pardon (1200), every
 * {@code KillPlayer.BEFORE} listener and psycho armour. So a shielded user is interrupted too. SparkTraits' and the
 * Judge's {@code @WrapMethod}s stay outermost; a re-entry finds no session and does nothing.
 * 绳索打断（「若你期间受到攻击则立刻中止」），挂在 Wathe 五参 {@code killPlayer} 的 HEAD（Wathe 成员，{@code remap = false}）：
 * 所有致命攻击都经过这里，因此其他玩家对勒杀者的击杀尝试在此打断勒杀。纯观察者，绝不取消、绝不改参数。顺序即契约：HEAD 回调按 mixin
 * 优先级升序执行，1030 位于默认 1000 的结构性守卫之后（SparkFactionAPI 影响否决、圣徒守卫、仇杀、强制开枪守卫：被它们拒绝的攻击不算
 * 攻击，沿用魔人先例），并位于所有能挡下或吸收真实攻击的机制之前：幽魂里世界作废（1040）、Rys-T 跳弹（1050）、魔人与 SparkStrength
 * 防弹衣（1100）、卡戎宽恕（1200）、所有 {@code KillPlayer.BEFORE} 监听与疯魔护甲。因此带盾的使用者同样会被打断。SparkTraits 与
 * 法官的 {@code @WrapMethod} 仍在最外层；重入时找不到会话，不做任何事。
 */
@Mixin(value = GameFunctions.class, remap = false, priority = 1030)
public abstract class GameFunctionsRopeStrangleInterruptMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$interruptRopeStrangle(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        RopeStrangleService.onKillAttempt(victim, killer);
    }
}
