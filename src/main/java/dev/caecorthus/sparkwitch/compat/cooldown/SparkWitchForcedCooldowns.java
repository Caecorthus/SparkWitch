package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Registers SparkWitch's and NoellesRoles' cooldown counters with the SparkFactionAPI forced-cooldown contract
 * ({@code api.cooldown}), so features that force cooldowns on other players (penalties, auras) write them through
 * one monotonic API instead of each counter. Called once from {@code SparkWitch.onInitialize}. The store order is
 * observable (it is the {@code ForcedCooldowns.slots} order) and must stay stable. NoellesRoles is a hard dependency.
 * 将 SparkWitch 与 NoellesRoles 的冷却计数注册到 SparkFactionAPI 强制冷却契约（api.cooldown），使对他人强制冷却的功能
 * （惩罚、光环）经由统一的单调 API 写入，而不是逐个写各计数。由 SparkWitch.onInitialize 调用一次。存储顺序可观察
 * （即 ForcedCooldowns.slots 的顺序），必须保持稳定。NoellesRoles 是硬依赖。
 */
public final class SparkWitchForcedCooldowns {
    private static boolean registered;

    private SparkWitchForcedCooldowns() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (RoleSkillCooldownStore store : roleSkillStores()) {
            ForcedCooldowns.registerRoleSkillStore(store);
        }
        ForcedCooldowns.registerItemNominalProvider(new SparkWitchItemCooldownNominals());
        ForcedCooldowns.registerItemExemption(item -> isExemptItem(Registries.ITEM.getId(item)));
    }

    /** Registration order of the role-skill stores. / 职业技能存储的注册顺序。 */
    static List<RoleSkillCooldownStore> roleSkillStores() {
        return List.of(
                new WitchSkillCooldownStore(),
                new SaintHellfireCooldownStore(),
                new OrthopedistCooldownStore(),
                new SaboteurCooldownStore(),
                new NoellesAbilityCooldownStore(),
                new NoellesTaotieSwallowCooldownStore(),
                new NoellesAssassinCooldownStore(),
                // Appended last: earlier slots keep their positions. / 追加在末尾：之前各槽位置不变。
                new BlindAttuneCooldownStore(),
                // Fiend Dash (owner decision 2026-10-04), appended after the Blind.
                // 魔人疾驰（所有者 2026-10-04 决定），追加在盲人之后。
                new FiendDashCooldownStore(),
                // Apprentice Purify (owner decision 2026-10-06), appended after the Fiend Dash.
                // 预备魔女净化（所有者 2026-10-06 决定），追加在魔人疾驰之后。
                new ApprenticePurifyCooldownStore()
        );
    }

    /**
     * The Seeker car is exempt: {@code SeekerCooldowns} is its frozen sole "max + exact" writer, driven only by the
     * Seeker state machine, and offers no write path to other features.
     * 搜寻者小车豁免：SeekerCooldowns 是其冻结的唯一“取最大 + 精确”写入方，只由搜寻者状态机驱动，不向其他功能开放写入。
     */
    static boolean isExemptItem(Identifier itemId) {
        return SparkWitchItems.SEEKER_CAR_ID.equals(itemId);
    }
}
