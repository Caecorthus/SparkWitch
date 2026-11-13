package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Role lookups for Koko from Wathe's synced {@code GameWorldComponent}, usable on both sides. The role is the raw
 * assigned role, never a disguise.
 * 依据 Wathe 已同步的 {@code GameWorldComponent} 判断可可身份，两端均可调用。读取的始终是真实分配的职业，而不是伪装。
 */
public final class KokoParticipation {
    private KokoParticipation() {
    }

    public static boolean isKoko(@Nullable PlayerEntity player) {
        return player != null && KokoRules.isKoko(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
