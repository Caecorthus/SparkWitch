package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: breaker mark (10 s, owner-only, newest replaces oldest) when the owner has a tablet anywhere.
 * TODO(WP-04): implement. / 待 WP-04 实现。
 * 冻结契约：拥有者背包中有平板时标记损坏者（10 秒、仅拥有者可见、新标记替换旧标记）。
 */
public final class SeekerMarkService {
    private SeekerMarkService() {
    }

    public static void onDeviceBroken(ServerPlayerEntity owner, @Nullable ServerPlayerEntity breaker,
                                      SeekerDeviceKind kind) {
    }
}
