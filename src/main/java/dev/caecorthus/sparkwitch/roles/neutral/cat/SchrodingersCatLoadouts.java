package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerLifecycleService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderEquipmentService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The kits a transformed cat would otherwise miss: these roles hand theirs out only at the round start
 * ({@code ON_FINISH_INITIALIZE} or while STARTING), never on a mid-round {@code RoleAssigned}. Each role's own
 * late entry reuses its round-start per-player code. Every other side (Vigilante, Emma, USEC, killer, Bewitched) is
 * fully equipped by {@code RoleAssigned}.
 * 转化后的猫原本会缺少的装备：这些职业只在开局（{@code ON_FINISH_INITIALIZE} 或 STARTING 期间）发放装备，从不在对局中途的
 * {@code RoleAssigned} 中发放。每个职业自己的中途入口复用其开局的个人发放代码。其余阵营（义警、艾玛、USEC、杀手、魔化使）
 * 由 {@code RoleAssigned} 完整发放。
 */
final class SchrodingersCatLoadouts {
    private SchrodingersCatLoadouts() {
    }

    static void grantLateLoadout(ServerPlayerEntity cat, Role role) {
        if (KnightRules.isKnight(role)) {
            KnightFeatureService.grantLateLoadout(cat);
        } else if (ControlExpertRules.isControlExpert(role)) {
            ControlExpertFeatureService.grantLateLoadout(cat);
        } else if (JudgeRules.isJudge(role)) {
            JudgeRuntime.grantLateLoadout(cat);
        } else if (SeekerRules.isSeeker(role)) {
            SeekerLifecycleService.grantLateLoadout(cat);
        } else if (BlackRavenRules.isBlackRaven(role)) {
            BlackRavenFeatureService.beginLateRound(cat);
        } else if (InsiderParticipation.isInsiderRole(role)) {
            InsiderEquipmentService.grantLateMasterKey(cat);
        }
    }
}
