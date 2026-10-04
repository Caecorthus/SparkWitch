package dev.caecorthus.sparkwitch.client.guardianangel;

import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelEffects;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/** Mirrors the server's target rules only to choose the local HUD prompt. / 仅为本地 HUD 提示镜像服务端目标规则。 */
public final class GuardianAngelTargetingPreview {
    private GuardianAngelTargetingPreview() {
    }

    @Nullable
    public static PlayerEntity aimedPlayer(ClientPlayerEntity player) {
        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof PlayerEntity target)) {
            return null;
        }
        // Same rule as the server, spectator test included, so a Rift Gate occupant never reads as "ready". Game
        // modes are public (tab list). / 与服务端同一规则（含旁观者判断），裂隙门内的玩家不会显示为“可用”。
        // 游戏模式本就公开（玩家列表）。
        return GuardianAngelRules.canTarget(
                target == player,
                GameFunctions.isPlayerPlayingAndAlive(target),
                target.isSpectator(),
                target.hasStatusEffect(GuardianAngelEffects.guardianShield()),
                player.canSee(target),
                player.squaredDistanceTo(target)
        ) ? target : null;
    }
}
