package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.impl.FirePokerFallAttributionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameWorldComponent.class)
public abstract class GameWorldComponentMixin {
    @Redirect(
            method = "serverTick()V",
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Ldev/doctor4t/wathe/game/GameConstants$DeathReasons;FELL_OUT_OF_TRAIN:Lnet/minecraft/util/Identifier;"
                    ),
                    to = @At(
                            value = "FIELD",
                            target = "Ldev/doctor4t/wathe/game/GameConstants$DeathReasons;DROWNED:Lnet/minecraft/util/Identifier;"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"
            )
    )
    private static void sparkwitch$attributeFirePokerFallKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        // Keep Wathe's fall death reason while replacing only the direct killer when a fresh Fire Poker push exists.
        // 保留 wathe 的坠车死因，只在有新鲜烧火棍推人记录时替换直接击杀者。
        GameFunctions.killPlayer(
                victim,
                spawnBody,
                FirePokerFallAttributionService.resolveFallKiller(victim, killer, deathReason),
                deathReason
        );
    }
}
