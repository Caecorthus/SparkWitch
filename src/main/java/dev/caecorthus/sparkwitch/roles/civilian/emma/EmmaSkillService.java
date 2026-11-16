package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
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
        // Every accomplice, special ones included, backlashes fatally like the plain Accomplice.
        // 所有共犯（含特殊共犯）都与普通共犯一样触发致命反噬。
        boolean fatal = role == SparkWitchRoles.grandWitch() || WitchFactionRules.isAccompliceLike(role)
                || role == SparkWitchRoles.witchMaiden();
        boolean voodoo = role != null && WitchFactorService.VOODOO_ROLE_ID.equals(role.identifier());
        if (fatal || voodoo) {
            // Hidden-role outcomes have identical immediate cost/cooldown; never preflight them in the HUD.
            // 隐藏职业分支具有相同的即时消耗与冷却，不得在 HUD 预检职业。
            EmmaRoundComponent.KEY.get(player.getWorld()).schedule(player.getUuid(),
                    player.getWorld().getTime() + EmmaRules.BACKLASH_TICKS, fatal);
        } else if (WitchFactorService.attemptSpread(player, target) == WitchFactorService.SpreadOutcome.REFUSED) {
            // Only an open refusal is free. A refusal that hidden state alone explains (a dormant factor, a role
            // that carries none) costs the mana and the cooldown below, like a spread and like the two hidden-role
            // branches above, so no target can be probed for free (owner 2026-11).
            // 只有公开的拒绝才免费。仅能由隐藏状态解释的拒绝（潜伏因子、不能携带因子的职业）会像传播以及上方两个隐藏职业
            // 分支一样扣除下方的魔力与冷却，因此无法免费试探任何目标（所有者 2026-11）。
            return WitchSkillUseResult.fail(null);
        }
        mana.setMana(mana.getMana() - EmmaRules.MANA_COST);
        return WitchSkillUseResult.success(EmmaRules.COOLDOWN_TICKS, null);
    }
}
