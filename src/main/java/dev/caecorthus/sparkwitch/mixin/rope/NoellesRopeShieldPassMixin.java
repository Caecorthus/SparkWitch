package dev.caecorthus.sparkwitch.mixin.rope;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.item.rope.RopeShieldPolicy;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "不破盾" for the two NoellesRoles shield layers, inside the pinned 1.7.6 {@code KillPlayer.BEFORE} listener (the
 * lambda the Ceremonial Sword and the AXMC also hook; NoellesRoles member, {@code remap = false}). Each layer starts
 * with one read of its own state: {@code IronManPlayerComponent.hasBuff()} and, the only {@code hasStatusEffect} call
 * of the lambda, the whiskey shield (vanilla target, hence {@code remap = true}). For {@code sparkwitch:strangled} both
 * reads answer false, so the layer neither blocks nor is consumed, exactly like the reasons NoellesRoles itself
 * exempts. Unlike the Ceremonial Sword's HEAD cancel, the rest of the listener still runs: the moment immunities and
 * the Jester's stasis keep stopping the Rope (role immunities, not shields), and a swallowed victim still leaves no
 * body. Every other death reason reads the original value.
 * 针对 NoellesRoles 两层护盾的「不破盾」，位于固定版本 1.7.6 的 {@code KillPlayer.BEFORE} 监听内（与仪礼剑、AXMC 所挂的是同一个
 * lambda；NoellesRoles 成员，{@code remap = false}）。每层护盾都以一次自身状态读取开头：{@code IronManPlayerComponent.hasBuff()}，
 * 以及该 lambda 中唯一的 {@code hasStatusEffect} 调用（威士忌护盾，原版目标，因此 {@code remap = true}）。对
 * {@code sparkwitch:strangled}，两次读取都回答 false，于是该层既不拦截也不被消耗，与 NoellesRoles 自身豁免的死因完全一致。与仪礼剑
 * 在 HEAD 取消不同，监听的其余部分照常执行：时刻免疫与小丑禁锢仍会挡下绳索（职业免疫而非护盾），被吞噬的受害者仍不留尸体。其他死因
 * 一律读取原值。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesRopeShieldPassMixin {
    @ModifyExpressionValue(
            method = "lambda$registerEvents$5",
            at = @At(value = "INVOKE",
                    target = "Lorg/agmas/noellesroles/professor/IronManPlayerComponent;hasBuff()Z"),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$ropePassesIronMan(boolean hasBuff, ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        return hasBuff && !RopeShieldPolicy.passesShields(deathReason);
    }

    @ModifyExpressionValue(
            method = "lambda$registerEvents$5",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                    remap = true),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$ropePassesWhiskey(boolean hasShield, ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        return hasShield && !RopeShieldPolicy.passesShields(deathReason);
    }
}
