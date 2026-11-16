package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkfactionapi.api.ThirdFactionDrawContext;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Thin SparkFactionAPI adapter for Team Jiahao's third-faction lineup (registered in {@code SparkWitchRoleRegistry} as
 * a method reference): reads the draw context and maps {@link JiahaoThirdFactionLineupRules} seats to roles. The
 * Corrupt Cop is found by its exact id among Wathe's registered roles, so a missing NoellesRoles just makes Team Jiahao
 * undrawable. Pure: it never writes a role, the RNG or any state; SparkFactionAPI deals the returned roles.
 * 嘉豪阵营“第三阵营”阵容的 SparkFactionAPI 薄适配层（在 {@code SparkWitchRoleRegistry} 中以方法引用注册）：读取抽取上下文，
 * 把 {@link JiahaoThirdFactionLineupRules} 的席位映射为职业。黑警按精确 id 在 Wathe 已注册职业中查找，因此未安装 NoellesRoles
 * 时嘉豪阵营只是不可抽取。纯函数：从不写入职业、随机数或任何状态；由 SparkFactionAPI 发放返回的职业。
 */
public final class JiahaoThirdFactionLineup {
    private JiahaoThirdFactionLineup() {
    }

    public static List<Role> rolesToDeal(ThirdFactionDrawContext context) {
        return rolesFor(
                context.presentMembers(),
                context.remainingSlots(),
                context.gameComponent()::isRoleEnabled,
                registeredCorruptCop(WatheRoles.ROLES),
                SparkWitchRoles.insider()
        );
    }

    static List<Role> rolesFor(
            List<Role> presentMembers,
            int remainingSlots,
            Predicate<Role> enabled,
            @Nullable Role corruptCop,
            Role insider
    ) {
        int corruptCops = 0;
        int insiders = 0;
        for (Role member : presentMembers) {
            if (InsiderParticipation.isCorruptCopRole(member)) {
                corruptCops++;
            } else if (InsiderParticipation.isInsiderRole(member)) {
                insiders++;
            }
        }
        List<Role> roles = new ArrayList<>();
        for (JiahaoThirdFactionLineupRules.Seat seat : JiahaoThirdFactionLineupRules.seats(
                corruptCops,
                insiders,
                corruptCop != null && enabled.test(corruptCop),
                enabled.test(insider),
                remainingSlots
        )) {
            roles.add(seat == JiahaoThirdFactionLineupRules.Seat.CORRUPT_COP ? corruptCop : insider);
        }
        return List.copyOf(roles);
    }

    /** NoellesRoles' Corrupt Cop by exact id, or null without NoellesRoles. / 按精确 id 查找黑警；未安装 NoellesRoles 时为 null。 */
    static @Nullable Role registeredCorruptCop(List<Role> registeredRoles) {
        for (Role role : registeredRoles) {
            if (InsiderParticipation.isCorruptCopRole(role)) {
                return role;
            }
        }
        return null;
    }
}
