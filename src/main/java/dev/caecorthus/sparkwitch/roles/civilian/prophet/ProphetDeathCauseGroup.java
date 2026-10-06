package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed Prophecy answer groups; declaration order is the UI order and never depends on the round's roles.
 * Foreign death reasons are matched by id string only, so NoellesRoles/SparkTraits stay optional; unknown ids are OTHER.
 * 预言的固定死因分组；声明顺序即界面顺序，不随本局职业变化。外部死因只按 id 字符串匹配，
 * NoellesRoles/SparkTraits 保持可选；未知 id 归为「其他」。
 */
public enum ProphetDeathCauseGroup {
    BLADE,
    GUNSHOT,
    THROWN,
    BLUNT,
    EXPLOSION,
    POISON,
    ACCIDENT,
    MENTAL,
    SUPERNATURAL,
    PENALTY,
    OTHER;

    // Ids verified against wathe-1.5.6-spark-1.21.1, NoellesRoles, SparkTraits and SparkWitch kill sites. Every
    // sparkwitch death reason must have an entry (owner-picked group); a local test fails when one is missing.
    // id 已对照 wathe-1.5.6-spark-1.21.1、NoellesRoles、SparkTraits 与 SparkWitch 的击杀调用核实。每个 sparkwitch
    // 死因都必须有条目（分组由所有者决定）；缺少条目时本地测试会失败。
    private static final Map<Identifier, ProphetDeathCauseGroup> BY_REASON = Map.ofEntries(
            entry("wathe:knife_stab", BLADE),
            entry("sparkwitch:ceremonial_blade", BLADE),
            entry("sparkwitch:ninja_knife_kill", BLADE),
            entry("sparkwitch:swordfish_stab", BLADE),
            entry("wathe:gun_shot", GUNSHOT),
            entry("wathe:gun_shot_backfire", GUNSHOT),
            entry("sparkwitch:ninja_shuriken_kill", THROWN),
            entry("noellesroles:throwing_axe", THROWN),
            entry("wathe:bat_hit", BLUNT),
            entry("sparkwitch:mighty_force", BLUNT),
            entry("wathe:grenade", EXPLOSION),
            entry("noellesroles:bomb", EXPLOSION),
            // Potion Gunner TR shell and launcher backblast; the gunner is the responsible player (owner 2026-10-03).
            // 药炮手 TR 炮弹与炮筒尾焰；药炮手为责任人（所有者 2026-10-03）。
            entry("sparkwitch:potion_shell", EXPLOSION),
            entry("sparkwitch:potion_backblast", EXPLOSION),
            entry("wathe:poison", POISON),
            entry("sparkwitch:tofana_elixir", POISON),
            entry("wathe:fell_out_of_train", ACCIDENT),
            entry("wathe:drowned", ACCIDENT),
            entry("wathe:vanilla_death", ACCIDENT),
            entry("wathe:escaped", ACCIDENT),
            entry("wathe:mental_breakdown", MENTAL),
            entry("sparkwitch:pierced_by_ray", SUPERNATURAL),
            entry("sparkwitch:bell_toll", SUPERNATURAL),
            entry("sparkwitch:time_stolen", SUPERNATURAL),
            entry("sparkwitch:factor_backlash", SUPERNATURAL),
            // Swapper crushed by a Rift Gate: forced, terminal and killer-less, so a correct guess reveals no killer.
            // 交换者被裂隙门夹死：强制、终结且无凶手，猜中时揭示「无人行凶」。
            entry("sparkwitch:portal_crushed", SUPERNATURAL),
            entry("noellesroles:voodoo", SUPERNATURAL),
            entry("noellesroles:assassinated", SUPERNATURAL),
            entry("noellesroles:digested", SUPERNATURAL),
            entry("wathe:shot_innocent", PENALTY),
            entry("noellesroles:assassin_misfire", PENALTY),
            entry("noellesroles:jester_timeout", PENALTY),
            entry("noellesroles:commander_suicide", PENALTY),
            entry("noellesroles:bodyguard_sacrifice", PENALTY),
            entry("sparktraits:self_realization", PENALTY)
    );

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.sparkwitch.prophecy.cause." + id;
    }

    public static ProphetDeathCauseGroup classify(@Nullable Identifier deathReason) {
        return deathReason == null ? OTHER : BY_REASON.getOrDefault(deathReason, OTHER);
    }

    public static Optional<ProphetDeathCauseGroup> byId(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ProphetDeathCauseGroup group : values()) {
            if (group.id.equals(id)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    private static Map.Entry<Identifier, ProphetDeathCauseGroup> entry(String reason, ProphetDeathCauseGroup group) {
        return Map.entry(Identifier.of(reason), group);
    }
}
