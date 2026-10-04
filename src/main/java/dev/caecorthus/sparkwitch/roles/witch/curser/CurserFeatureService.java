package dev.caecorthus.sparkwitch.roles.witch.curser;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Server authority for Curser promotion and the eight-block confusion burst. / 诅咒师晋升与八码混乱爆发的服务端权威。 */
public final class CurserFeatureService {
    private static boolean registered;

    private CurserFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CurserShopService.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CurserPlayerComponent.KEY.get(handler.player).clear());
    }

    public static void initializeForPromotion(ServerPlayerEntity player) {
        CurserPlayerComponent.KEY.get(player).initializeForPromotion();
        CurserShopService.initializePromotionStock(player);
    }

    /** Empty packet: recompute role, Wraith, round, range, and living target eligibility server-side. */
    public static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getServerWorld());
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(caster);
        CurserPlayerComponent state = CurserPlayerComponent.KEY.get(caster);
        if (!CurserRules.canUse(
                game.isRunning(),
                wraith.isActive(),
                wraith.isPromoted(),
                game.getRole(caster) != null
                        && SparkWitchRoles.CURSER_ID.equals(game.getRole(caster).identifier()),
                state.getCooldownTicks())) {
            return;
        }

        // Spectators are never cursed: a Rift Gate occupant is an ALIVE spectator (Riftwalker D3) and Wathe's liveness
        // check ignores the game mode. Occupants alone read as "no target" (no cooldown), so nothing reveals them.
        // SparkFactionAPI's canAffectPlayer is not used: it denies every target to an active-Wraith caster.
        // 旁观者永远不会被诅咒：裂隙门内的玩家是存活旁观者（隙行者 D3），而 Wathe 的存活检查不看游戏模式。
        // 只有门内玩家时按“无目标”处理（不进入冷却），因此不会暴露他们。不使用 SparkFactionAPI 的 canAffectPlayer：
        // 它会对激活的冤魂施法者拒绝所有目标。
        List<ServerPlayerEntity> targets = caster.getServerWorld().getPlayers(player ->
                player != caster
                        && !player.isSpectator()
                        && GameFunctions.isPlayerPlayingAndAlive(player)
                        && caster.squaredDistanceTo(player) <= CurserRules.RANGE * CurserRules.RANGE
        );
        if (targets.isEmpty()) {
            caster.sendMessage(Text.translatable("message.sparkwitch.curser.no_target"), true);
            return;
        }
        if (!state.startCooldown()) {
            return;
        }
        for (ServerPlayerEntity target : targets) {
            CurserPlayerComponent.KEY.get(target).applyConfusion();
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        CurserPlayerComponent.KEY.get(player).clear();
    }

    public static boolean isActivePromotedCurser(PlayerEntity player) {
        return player != null
                && WraithStateService.isActive(player)
                && WraithStateService.isPromoted(player)
                && GameWorldComponent.KEY.get(player.getWorld()).getRole(player) != null
                && SparkWitchRoles.CURSER_ID.equals(
                        GameWorldComponent.KEY.get(player.getWorld()).getRole(player).identifier());
    }
}
