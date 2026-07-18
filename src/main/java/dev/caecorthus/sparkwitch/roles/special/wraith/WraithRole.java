package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/**
 * Special mid-round identity for an activated Wraith; it never participates in role selection.
 * 冤魂激活后的特殊局中身份；永远不参与开局身份抽取。
 */
public final class WraithRole {
    public static final Identifier ID = SparkWitch.id("wraith");
    public static final int COLOR = 0x79C7D4;
    public static final Role ROLE = new Role(
            ID,
            COLOR,
            false,
            false,
            Role.MoodType.NONE,
            -1,
            false,
            context -> false
    );

    private WraithRole() {
    }

    public static boolean isWraith(Role role) {
        return role != null && ID.equals(role.identifier());
    }
}
