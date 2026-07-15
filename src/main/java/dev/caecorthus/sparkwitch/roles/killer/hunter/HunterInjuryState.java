package dev.caecorthus.sparkwitch.roles.killer.hunter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, Minecraft-independent fracture/root timer state used by the Hunter component.
 * 猎人组件使用的纯骨折与定身计时状态，不依赖 Minecraft 运行时。
 */
public final class HunterInjuryState {
    private final List<Integer> fractureTimers = new ArrayList<>();
    private int rootTicks;

    public FractureApplication addFractureLayer(int durationTicks) {
        if (fractureTimers.size() >= HunterRules.MAX_FRACTURE_LAYERS) {
            return FractureApplication.AT_CAP;
        }
        fractureTimers.add(Math.max(1, durationTicks));
        return FractureApplication.ADDED;
    }

    public FractureApplication addFractureLayerWithBoneSetting(int durationTicks) {
        return FractureApplication.CONSUMED_BONE_SETTING;
    }

    public boolean healOneFractureLayer() {
        if (fractureTimers.isEmpty()) {
            return false;
        }
        fractureTimers.remove(fractureTimers.size() - 1);
        return true;
    }

    public void rootFor(int durationTicks) {
        rootTicks = Math.max(rootTicks, Math.max(0, durationTicks));
    }

    public TickResult tick() {
        boolean rootChanged = rootTicks > 0;
        if (rootChanged) {
            rootTicks--;
        }

        boolean fractureChanged = false;
        for (int index = fractureTimers.size() - 1; index >= 0; index--) {
            int remaining = fractureTimers.get(index) - 1;
            if (remaining <= 0) {
                fractureTimers.remove(index);
                fractureChanged = true;
            } else {
                fractureTimers.set(index, remaining);
            }
        }
        return new TickResult(rootChanged, fractureChanged);
    }

    public void clear() {
        fractureTimers.clear();
        rootTicks = 0;
    }

    public int fractureLayers() {
        return fractureTimers.size();
    }

    public List<Integer> fractureTimers() {
        return List.copyOf(fractureTimers);
    }

    public void restoreFractureTimers(List<Integer> timers) {
        fractureTimers.clear();
        if (timers == null) {
            return;
        }
        for (Integer timer : timers) {
            if (timer != null && timer > 0 && fractureTimers.size() < HunterRules.MAX_FRACTURE_LAYERS) {
                fractureTimers.add(timer);
            }
        }
    }

    public int rootTicks() {
        return rootTicks;
    }

    public void restoreRootTicks(int ticks) {
        rootTicks = Math.max(0, ticks);
    }

    public enum FractureApplication {
        ADDED,
        CONSUMED_BONE_SETTING,
        AT_CAP
    }

    public record TickResult(boolean rootWasActive, boolean fractureExpired) {
    }
}
