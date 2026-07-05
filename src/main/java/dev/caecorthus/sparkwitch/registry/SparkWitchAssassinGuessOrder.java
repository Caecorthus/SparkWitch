package dev.caecorthus.sparkwitch.registry;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;

import java.util.List;

final class SparkWitchAssassinGuessOrder {
    private SparkWitchAssassinGuessOrder() {
    }

    static void appendToTail(List<Role> roles) {
        // Keep SparkWitch roles at the end of NoellesRoles/SparkTraits Assassin guess panels.
        // 让 SparkWitch 职业稳定显示在 NoellesRoles/SparkTraits 刺客猜身份面板末尾。
        WatheRoles.ROLES.removeIf(role -> containsIdentity(roles, role));
        WatheRoles.ROLES.addAll(roles);
    }

    private static boolean containsIdentity(List<Role> roles, Role candidate) {
        for (Role role : roles) {
            if (role == candidate) {
                return true;
            }
        }
        return false;
    }
}
