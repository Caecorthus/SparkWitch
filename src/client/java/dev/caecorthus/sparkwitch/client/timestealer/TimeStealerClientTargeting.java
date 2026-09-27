package dev.caecorthus.sparkwitch.client.timestealer;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Client crosshair hint for the Clock. It reuses the server's {@code ClockGeometry} but filters candidates by public
 * state only, so the hint never reveals a hidden role, trait or faction veto; the server still decides every theft.
 * 时钟的客户端准星提示。复用服务端的 {@code ClockGeometry}，但只按公开状态过滤候选者，因此提示永远不会暴露隐藏的
 * 职业、词条或阵营否决；是否窃取始终由服务端决定。
 */
public final class TimeStealerClientTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");

    private TimeStealerClientTargeting() {
    }

    /** Ready Clock in the local Time Stealer's main hand with a visible player on the ray. / 本地窃时者主手持就绪时钟且射线上有可见玩家。 */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        // TODO(WP-06): own item + cooldown + exact local role + ClockGeometry.findTarget with public filter.
        return false;
    }
}
