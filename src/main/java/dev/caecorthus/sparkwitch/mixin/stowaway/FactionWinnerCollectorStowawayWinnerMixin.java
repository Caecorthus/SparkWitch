package dev.caecorthus.sparkwitch.mixin.stowaway;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SparkFactionAPI custom faction wins (Witch, Murderous Witch and every other registered faction) collect their
 * winners here. A Stowaway that is still alive at that moment is added to the set, so it is synced as a co-winner and
 * both the settlement row and {@code SparkFactionRoundEndComponent.didWin} treat it as part of the winning faction. A
 * dead Stowaway is never added.
 * SparkFactionAPI 自定义阵营胜利（魔女、杀意魔女以及其他已注册阵营）在此收集赢家。此刻仍活着的偷渡客会被加入集合，
 * 因此会作为共同赢家同步，结算行与 {@code SparkFactionRoundEndComponent.didWin} 都会把它视作获胜阵营的一员；死亡的
 * 偷渡客永远不会加入。
 */
@Mixin(targets = "dev.caecorthus.sparkfactionapi.impl.roundend.FactionWinnerCollector", remap = false)
public abstract class FactionWinnerCollectorStowawayWinnerMixin {
    @ModifyReturnValue(
            method = "collectFactionWinners(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/cca/GameWorldComponent;Lnet/minecraft/util/Identifier;)Ljava/util/Set;",
            at = @At("RETURN"),
            remap = false
    )
    private static Set<UUID> sparkwitch$livingStowawaysJoinFactionWin(
            Set<UUID> winners,
            ServerWorld world,
            GameWorldComponent game,
            Identifier factionId
    ) {
        Set<UUID> adjusted = new LinkedHashSet<>(winners);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!game.isPlayerDead(player.getUuid()) && StowawayRules.isStowaway(game.getRole(player))) {
                adjusted.add(player.getUuid());
            }
        }
        return adjusted;
    }
}
