package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import net.minecraft.server.world.ServerWorld;

/**
 * Owns Grand Witch world-spell countdowns and pulses.
 * 负责大魔女世界法术的倒计时与周期效果，组件只保存和同步状态。
 */
public final class GrandWitchWorldRuntime {
    private GrandWitchWorldRuntime() {
    }

    public static void tick(ServerWorld world, WitchWorldComponent component) {
        WitchWorldComponent.GrandWitchRuntimeState state = component.grandWitchRuntimeState();
        int instinctObscureTicks = state.instinctObscureTicks();
        int obscureActionbarTicks = state.obscureActionbarTicks();
        int fearTicks = state.fearTicks();
        boolean shouldSync = false;

        if (instinctObscureTicks > 0) {
            if (obscureActionbarTicks <= 0) {
                GrandWitchSpellService.sendObscureActionbars(world, instinctObscureTicks);
                obscureActionbarTicks = 20;
            }
            instinctObscureTicks--;
            obscureActionbarTicks--;
            shouldSync = instinctObscureTicks == 0 || instinctObscureTicks % 20 == 0;
        } else {
            obscureActionbarTicks = 0;
        }

        if (fearTicks > 0) {
            GrandWitchSpellService.tickFear(world, fearTicks);
            fearTicks--;
            shouldSync = shouldSync || fearTicks == 0;
        }

        component.applyGrandWitchRuntimeState(new WitchWorldComponent.GrandWitchRuntimeState(
                instinctObscureTicks,
                obscureActionbarTicks,
                fearTicks
        ));
        if (shouldSync) {
            component.sync();
        }
    }
}
