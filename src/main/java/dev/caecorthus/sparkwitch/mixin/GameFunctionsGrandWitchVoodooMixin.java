package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.witch.WitchFactionProtectionPolicy;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe seam, same shape as {@code witchmaiden.GameFunctionsWitchMaidenVoodooMixin}: cancels the final NoellesRoles
 * Voodoo death of a Grand Witch before Wathe's first-result-wins {@code KillPlayer.BEFORE}. Her immunity was only a
 * default-phase BEFORE listener ({@link WitchFactionProtectionPolicy}), and NoellesRoles, which registers first,
 * answers {@code allowWithoutBody()} for any Taotie-swallowed victim, so a swallowed Grand Witch died to the curse she
 * is immune to. Only that role and that death reason are judged, and a forced kill is left alone, as Wathe itself
 * ignores a cancel on one.
 * Wathe 接缝，与 {@code witchmaiden.GameFunctionsWitchMaidenVoodooMixin} 形态相同：在 Wathe 首个结果短路的
 * {@code KillPlayer.BEFORE} 之前取消大魔女的 NoellesRoles 巫毒最终死亡。她的免疫此前只是默认阶段的 BEFORE 监听
 * （{@link WitchFactionProtectionPolicy}），而先注册的 NoellesRoles 会对任何被饕餮吞下的受害者返回
 * {@code allowWithoutBody()}，于是被吞下的大魔女会死于她本应免疫的诅咒。只判定该职业与该死亡原因；强制击杀不受影响，
 * 因为 Wathe 自身也不会取消强制击杀。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsGrandWitchVoodooMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private static void sparkwitch$blockGrandWitchVoodoo(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (WitchFactionProtectionPolicy.cancelsVoodooKill(victim, deathReason, force)) {
            ci.cancel();
        }
    }
}
