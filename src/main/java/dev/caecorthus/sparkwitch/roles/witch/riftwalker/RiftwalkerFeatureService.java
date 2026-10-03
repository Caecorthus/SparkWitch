package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantHooks;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateLifecycle;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile.RiftGateProjectileService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath.WitchesSabbathService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper.RiftSwapperCrushService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet.RiftGateConsoleService;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Riftwalker server registration, called once from SparkWitchEvents. It enters the role into the shared
 * special-accomplice pool (so Grand Witch recruitment may roll it and every "basic accomplice" rule applies), then
 * wires the role-owned shop and every work package's server runtime. There is no recruit kit: gates are bought, so no
 * {@code afterRecruitCommitted} hook.
 * 隙行者服务端注册，由 SparkWitchEvents 调用一次。先把本职业登记进共享的特殊共犯池（大魔女招募可抽到它，
 * 所有「共犯基础功能」规则随之生效），再接入本职业自有的商店与各工作包的服务端运行时。没有招募装备：
 * 裂隙门靠购买获得，因此不实现 {@code afterRecruitCommitted}。
 */
public final class RiftwalkerFeatureService {
    private static final Set<Identifier> OWN_SKILL_IDS = Set.of(RiftwalkerRules.SABBATH_SKILL_ID);
    private static final AccompliceVariantHooks HOOKS = new AccompliceVariantHooks() {
        // AGENTS.md witch-skill-panel rule: Witches' Sabbath is the role's own witch skill, so it shows in the panel.
        // AGENTS.md 魔女技能面板规则：魔女集会是本职业自有的魔女技能，因此显示在 gui.sparkwitch.skills 技能面板中。
        @Override
        public Set<Identifier> ownSkillIds() {
            return OWN_SKILL_IDS;
        }
    };
    private static boolean registered;

    private RiftwalkerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AccompliceVariants.register(SparkWitchRoles.riftwalker(), HOOKS);
        RiftwalkerShopService.register();
        // Work-package runtimes (each idempotent; inert until its WP lands). / 各工作包运行时（均幂等；对应工作包落地前无效果）。
        RiftGateLifecycle.register();
        RiftSessionService.register();
        RiftGateProjectileService.register();
        WitchesSabbathService.register();
        RiftGateConsoleService.register();
        RiftSwapperCrushService.register();
    }
}
