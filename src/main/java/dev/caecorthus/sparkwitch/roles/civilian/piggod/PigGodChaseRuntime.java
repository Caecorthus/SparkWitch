package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.event.PsychoType;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Owns Pig Chase freeze, psycho, sound, and active-window ticking.
 * 负责皮革噶追杀的冻结、疯魔、声音和生效窗口 tick。
 */
public final class PigGodChaseRuntime {
    private PigGodChaseRuntime() {
    }

    public static void begin(
            ServerPlayerEntity player,
            WitchPlayerComponent component,
            int freezeTicks,
            int chaseTicks
    ) {
        component.applyPigChaseState(new WitchPlayerComponent.PigChaseState(
                Math.max(0, freezeTicks),
                Math.max(0, chaseTicks),
                0,
                player.getX(),
                player.getY(),
                player.getZ(),
                false
        ));
        if (PigGodRules.shouldStartChaseImmediately(freezeTicks, chaseTicks)) {
            startChase(player, component);
        }
        component.sync();
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (!component.hasActivePigChaseState()) {
            return;
        }
        if (!PigGodRules.isPigGod(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            clear(player, component);
            return;
        }

        int activeBeforeTick = effectiveWindowTicks(component.pigChaseState());
        boolean shouldSync = tickActiveState(player, component);
        if (activeBeforeTick > 0 && effectiveWindowTicks(component.pigChaseState()) == 0) {
            component.startDeferredCooldownNow();
            shouldSync = true;
        }
        if (shouldSync) {
            component.sync();
        }
    }

    public static void clear(ServerPlayerEntity player, WitchPlayerComponent component) {
        clear(player, component, true);
    }

    public static void stopSoundBeforeComponentReset(
            ServerPlayerEntity player,
            WitchPlayerComponent component
    ) {
        WitchPlayerComponent.PigChaseState state = component.pigChaseState();
        if (effectiveWindowTicks(state) > 0 || state.ownsPsycho()) {
            stopSound(player, state);
        }
    }

    public static void clearPsychoBeforeComponentReset(
            ServerPlayerEntity player,
            WitchPlayerComponent component
    ) {
        clearPsycho(player, component);
    }

    private static boolean tickActiveState(ServerPlayerEntity player, WitchPlayerComponent component) {
        WitchPlayerComponent.PigChaseState state = component.pigChaseState();
        if (state.freezeTicks() > 0) {
            holdFreeze(player, state);
            int remaining = state.freezeTicks() - 1;
            component.applyPigChaseState(withFreezeTicks(state, remaining));
            if (remaining == 0) {
                startChase(player, component);
                return true;
            }
            return remaining % 20 == 0;
        }
        if (state.queuedTicks() > 0) {
            startChase(player, component);
            return true;
        }
        if (state.chaseTicks() <= 0) {
            return false;
        }

        int remaining = state.chaseTicks() - 1;
        component.applyPigChaseState(withChaseTicks(state, remaining));
        if (remaining == 0) {
            stopSound(player, state);
            clearPsycho(player, component);
            return true;
        }
        return remaining % 20 == 0;
    }

    private static void startChase(ServerPlayerEntity player, WitchPlayerComponent component) {
        WitchPlayerComponent.PigChaseState state = component.pigChaseState();
        int chaseTicks = state.queuedTicks();
        state = new WitchPlayerComponent.PigChaseState(
                state.freezeTicks(),
                0,
                chaseTicks,
                state.freezeX(),
                state.freezeY(),
                state.freezeZ(),
                state.ownsPsycho()
        );
        component.applyPigChaseState(state);
        if (chaseTicks <= 0) {
            return;
        }

        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        boolean ownsPsycho = state.ownsPsycho();
        if (psycho.getPsychoTicks() <= 0) {
            ownsPsycho = psycho.startPsycho(PsychoType.VISIBLE_QUIET);
        }
        if (!ownsPsycho && psycho.getPsychoTicks() <= 0) {
            stopSound(player, state);
            component.applyPigChaseState(withChaseTicks(state, 0));
            return;
        }
        component.applyPigChaseState(withPsychoOwnership(state, ownsPsycho));
        psycho.setPsychoTicks(chaseTicks);
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                chaseTicks,
                PigGodRules.SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    private static void clear(ServerPlayerEntity player, WitchPlayerComponent component, boolean sync) {
        WitchPlayerComponent.PigChaseState state = component.pigChaseState();
        if (effectiveWindowTicks(state) <= 0 && !state.ownsPsycho()) {
            return;
        }
        stopSound(player, state);
        clearPsycho(player, component);
        component.applyPigChaseState(new WitchPlayerComponent.PigChaseState(
                0,
                0,
                0,
                state.freezeX(),
                state.freezeY(),
                state.freezeZ(),
                false
        ));
        if (sync) {
            component.sync();
        }
    }

    private static void clearPsycho(ServerPlayerEntity player, WitchPlayerComponent component) {
        WitchPlayerComponent.PigChaseState state = component.pigChaseState();
        if (!state.ownsPsycho()) {
            return;
        }
        PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) {
            psycho.stopPsycho();
        }
        component.applyPigChaseState(withPsychoOwnership(state, false));
    }

    private static void holdFreeze(ServerPlayerEntity player, WitchPlayerComponent.PigChaseState state) {
        player.teleport(state.freezeX(), state.freezeY(), state.freezeZ(), false);
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
    }

    private static void stopSound(ServerPlayerEntity player, WitchPlayerComponent.PigChaseState state) {
        PigGodSkillService.stopChaseSound(
                player.getServerWorld(),
                state.freezeX(),
                state.freezeY(),
                state.freezeZ()
        );
    }

    private static int effectiveWindowTicks(WitchPlayerComponent.PigChaseState state) {
        return state.freezeTicks() + state.queuedTicks() + state.chaseTicks();
    }

    private static WitchPlayerComponent.PigChaseState withFreezeTicks(
            WitchPlayerComponent.PigChaseState state,
            int freezeTicks
    ) {
        return new WitchPlayerComponent.PigChaseState(
                freezeTicks,
                state.queuedTicks(),
                state.chaseTicks(),
                state.freezeX(),
                state.freezeY(),
                state.freezeZ(),
                state.ownsPsycho()
        );
    }

    private static WitchPlayerComponent.PigChaseState withChaseTicks(
            WitchPlayerComponent.PigChaseState state,
            int chaseTicks
    ) {
        return new WitchPlayerComponent.PigChaseState(
                state.freezeTicks(),
                state.queuedTicks(),
                chaseTicks,
                state.freezeX(),
                state.freezeY(),
                state.freezeZ(),
                state.ownsPsycho()
        );
    }

    private static WitchPlayerComponent.PigChaseState withPsychoOwnership(
            WitchPlayerComponent.PigChaseState state,
            boolean ownsPsycho
    ) {
        return new WitchPlayerComponent.PigChaseState(
                state.freezeTicks(),
                state.queuedTicks(),
                state.chaseTicks(),
                state.freezeX(),
                state.freezeY(),
                state.freezeZ(),
                ownsPsycho
        );
    }
}
