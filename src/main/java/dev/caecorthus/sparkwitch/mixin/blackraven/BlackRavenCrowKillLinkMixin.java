package dev.caecorthus.sparkwitch.mixin.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowTargets;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raven Form kill link for owner substitution ({@code BlackRavenCrowTargets#substitute}): at the HEAD of Wathe's
 * 5-argument {@code killPlayer} (every overload delegates to it), a victim that a weapon substituted for its crow this
 * tick loses its session first ({@code CROW_KILLED}, no Homing Feather), exactly as
 * {@code BlackRavenCrowSessions.killOwnerVia} does before its own kill, unless SparkFactionAPI refuses the killer on
 * that victim for the death reason (SparkFactionAPI's own HEAD veto cancels that kill, in either order, so the crow and
 * its session stay). It also records the victims of an open blast scope (the Wathe grenade crow pass). Any other victim
 * is untouched; never cancels, so the kill, its protections and every other HEAD hook run as for any player. Server
 * only.
 * 化鸦主人替身（{@code BlackRavenCrowTargets#substitute}）的击杀链接：在 Wathe 5 参数 {@code killPlayer}（所有重载都委托给它）
 * 的 HEAD，本刻被武器替身为乌鸦的受害者先结束会话（{@code CROW_KILLED}，不使用归羽），与 {@code BlackRavenCrowSessions.killOwnerVia}
 * 在自身击杀前所做的完全一致；但 SparkFactionAPI 以该死因拒绝凶手影响该受害者时除外（无论先后，SparkFactionAPI 自身的 HEAD
 * 否决都会取消该击杀，因此乌鸦与会话保持不变）。它还记录打开的爆炸作用域中的受害者（Wathe 手雷的乌鸦扫描）。其他受害者不受影响；
 * 从不取消，因此击杀、其保护与其他 HEAD 钩子都与任何玩家一样执行。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class BlackRavenCrowKillLinkMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"), require = 1)
    private static void sparkwitch$endSubstitutedCrowSession(ServerPlayerEntity victim, boolean spawnBody,
                                                             @Nullable ServerPlayerEntity killer, Identifier deathReason,
                                                             boolean force, CallbackInfo ci) {
        BlackRavenCrowTargets.beforeKillPlayer(victim, killer, deathReason);
    }
}
