package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.FocusedFootstepsUseResultS2CPacket;
import dev.caecorthus.sparkwitch.skill.WitchSkillUseService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentProvider;

/** Adds an owner-only acknowledgement without changing generic skill dispatch. / 在通用技能分发外补充仅所有者可见的确认。 */
public final class FocusedFootstepsRequestService {
    private FocusedFootstepsRequestService() {
    }

    public static boolean use(ServerPlayerEntity player, Optional<UUID> targetUuid) {
        boolean accepted = WitchSkillUseService.use(player, targetUuid);
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (WitchMaidenRules.isWitchMaiden(game.getRole(player))) {
            // Explicit recipient + provider keeps this acknowledgement owner-only.
            // 显式指定接收者与组件提供者，确保该确认只发给技能所有者。
            WitchPlayerComponent.KEY.syncWith(player, (ComponentProvider) player);
            ServerPlayNetworking.send(player, new FocusedFootstepsUseResultS2CPacket(
                    accepted,
                    WitchPlayerComponent.KEY.get(player).getCooldownTicks()
            ));
        }
        return accepted;
    }
}
