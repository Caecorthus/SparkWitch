package dev.caecorthus.sparkwitch.mixin.knight;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightKillService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Knight rule "killing a good player never refills" (owner, 2026-10-30). Pinned Wathe 1.5.6 reloads every spent
 * derringer of a non-null killer in the 5-argument {@code killPlayer} (the 3/4-argument overloads delegate to it),
 * whatever the weapon. This wraps that loop's only {@code ItemStack.isOf} call, the {@code isOf(DERRINGER)} gate in
 * front of both {@code set(USED, false)} and the reload sound ({@code require = 1, allow = 1}): when a Knight kills a
 * good player, no stack counts as a derringer, so nothing reloads and no reload sound plays. Every other killer,
 * bad-victim kills and self-kills keep Wathe's reload. Server only.
 * 骑士规则“击杀好人不补弹”（所有者，2026-10-30）。固定版本 Wathe 1.5.6 在 5 参数 {@code killPlayer}（3/4 参数重载均委托
 * 给它）中，无论使用何种武器，都会为非空击杀者装填所有已用德林加。这里包装该循环中唯一的 {@code ItemStack.isOf} 调用，
 * 即 {@code set(USED, false)} 与装填音效之前的 {@code isOf(DERRINGER)} 判断（{@code require = 1, allow = 1}）：骑士击杀
 * 好人时，任何物品都不算德林加，因此既不装填也不播放装填音效。其他击杀者、击杀坏人与自杀均保留 Wathe 的装填。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class KnightDerringerReloadMixin {
    @ModifyExpressionValue(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$knightGoodKillKeepsDerringersSpent(boolean derringer, ServerPlayerEntity victim,
                                                                         boolean spawnBody,
                                                                         @Nullable ServerPlayerEntity killer,
                                                                         Identifier deathReason, boolean force) {
        return derringer && !KnightKillService.blocksKillReload(victim, killer);
    }
}
