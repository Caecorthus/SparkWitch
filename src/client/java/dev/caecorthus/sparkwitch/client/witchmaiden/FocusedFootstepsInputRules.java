package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;

/** Resolves the affected client's forced planar input and run phase. / 解析受影响客户端的强制平面输入与跑步阶段。 */
public final class FocusedFootstepsInputRules {
    private FocusedFootstepsInputRules() {
    }

    public static PlanarInput forcedPlanarInput() {
        return new PlanarInput(1.0F, 0.0F, false);
    }

    public static boolean shouldSprint(int amplifier, boolean infiniteStamina, boolean exhausted) {
        return infiniteStamina || (!exhausted
                && FocusedFootstepsRules.Phase.fromAmplifier(amplifier)
                == FocusedFootstepsRules.Phase.RUNNING);
    }

    public record PlanarInput(
            float movementForward,
            float movementSideways,
            boolean sneaking
    ) {
    }
}
