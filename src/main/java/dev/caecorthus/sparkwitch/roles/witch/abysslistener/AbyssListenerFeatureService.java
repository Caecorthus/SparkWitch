package dev.caecorthus.sparkwitch.roles.witch.abysslistener;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout.AbyssListenerLoadout;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkZoneService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkZoneStandingService;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantHooks;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Abyss Listener server registration, called once from SparkWitchEvents. It enters the role into the shared
 * special-accomplice pool (so Grand Witch recruitment may roll it and every "basic accomplice" rule applies), then
 * wires the role-owned shop, bound loadout and Deep Dark Zone runtime.
 * 聆渊者服务端注册，由 SparkWitchEvents 调用一次。先把本职业登记进共享的特殊共犯池（大魔女招募可抽到它，
 * 所有"共犯基础功能"规则随之生效），再接入本职业自有的商店、绑定装备与深暗领域运行时。
 */
public final class AbyssListenerFeatureService {
    private static final Set<Identifier> OWN_SKILL_IDS = Set.of(AbyssListenerRules.SHRIEK_SKILL_ID);
    private static final AccompliceVariantHooks HOOKS = new AccompliceVariantHooks() {
        // The bound gun is granted only after the recruitment committed: a RoleAssigned grant would be wiped by the
        // retained-inventory restore. / 绑定枪械只在招募提交后发放：在 RoleAssigned 中发放会被保留背包恢复抹掉。
        @Override
        public void afterRecruitCommitted(ServerPlayerEntity recruit, ServerPlayerEntity recruiter) {
            AbyssListenerLoadout.grantAfterRecruit(recruit);
        }

        // D13: Warden's Shriek is the role's own witch skill, so it shows in the gui.sparkwitch.skills panel.
        // D13：监守之啸是本职业自有的魔女技能，因此显示在 gui.sparkwitch.skills 技能面板中。
        @Override
        public Set<Identifier> ownSkillIds() {
            return OWN_SKILL_IDS;
        }
    };
    private static boolean registered;

    private AbyssListenerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AccompliceVariants.register(SparkWitchRoles.abyssListener(), HOOKS);
        AbyssListenerShopService.register();
        AbyssListenerReplayFormatters.register();
        AbyssListenerLoadout.register();
        DeepDarkZoneService.register();
        DeepDarkZoneStandingService.register();
    }
}
