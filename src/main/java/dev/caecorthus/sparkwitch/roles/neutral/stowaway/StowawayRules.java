package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stowaway contract: stable role values and pure role predicates.
 * 偷渡客契约：稳定的职业数值与纯职业判断。
 */
public final class StowawayRules {
    public static final Identifier ROLE_ID = SparkWitch.id("stowaway");
    /** Gray-green role color. / 灰绿色职业颜色。 */
    public static final int COLOR = 0x6E8B6E;

    private StowawayRules() {
    }

    public static boolean isStowaway(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
