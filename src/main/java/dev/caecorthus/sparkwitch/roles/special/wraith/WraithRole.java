package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;

/**
 * Special mid-round identity for an activated Wraith; it never participates in opening role selection.
 * 冤魂激活后的特殊局中身份；永远不参与开局身份抽取。
 */
public final class WraithRole {
    public static final int COLOR = 0x79C7D4;
    public static final Role ROLE = new Role(
            SparkWitch.id("wraith"),
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
}
