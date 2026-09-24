package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.skill.WitchSkillUseService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public final class EmmaSkillService {
    private EmmaSkillService() { }

    public static void use(ServerPlayerEntity player, @Nullable UUID requested) {
        if (EmmaRules.isEmma(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            WitchSkillUseService.use(player, Optional.ofNullable(requested));
        }
    }

    public static WitchSkillUseResult useSkill(WitchSkillUseContext context) {
        ServerPlayerEntity player = context.player();
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        if (!context.gameComponent().isRunning() || !EmmaRules.isEmma(context.role())
                || !GameFunctions.isPlayerPlayingAndAlive(player) || mana.getMana() < EmmaRules.MANA_COST
                || GrandWitchFearService.isPlayerFeared(player) || SparkTraitsGunBridge.isRoleSkillBlocked(player)) {
            return WitchSkillUseResult.fail(null);
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(player,
                context.target() == null ? null : context.target().getUuid());
        if (target == null || !SparkFactionApi.canAffectPlayer(player, target, EmmaRules.SKILL_ID, context.gameComponent())) {
            return WitchSkillUseResult.fail("message.sparkwitch.emma.invalid_target");
        }
        if (WitchFactorService.getRemaining(player.getServerWorld()) == 0) {
            return WitchSkillUseResult.fail("message.sparkwitch.emma.no_capacity");
        }
        Role role = context.gameComponent().getRole(target);
        boolean fatal = role == SparkWitchRoles.grandWitch() || role == SparkWitchRoles.accomplice()
                || role == SparkWitchRoles.witchMaiden();
        boolean voodoo = role != null && WitchFactorService.VOODOO_ROLE_ID.equals(role.identifier());
        if (fatal || voodoo) {
            // Hidden-role outcomes have identical immediate cost/cooldown; never preflight them in the HUD.
            // 隐藏职业分支具有相同的即时消耗与冷却，不得在 HUD 预检职业。
            EmmaRoundComponent.KEY.get(player.getWorld()).schedule(player.getUuid(),
                    player.getWorld().getTime() + EmmaRules.BACKLASH_TICKS, fatal);
        } else if (!WitchFactorService.trySpread(player, target)) {
            return WitchSkillUseResult.fail(null);
        }
        mana.setMana(mana.getMana() - EmmaRules.MANA_COST);
        return WitchSkillUseResult.success(EmmaRules.COOLDOWN_TICKS, null);
    }
}
