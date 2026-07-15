package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Owns Saint's initial cooldown, Hellfire activation, and post-survival cooldown.
 * 持有圣徒的开局冷却、业火启动和存活后的冷却逻辑。
 */
public final class SaintAbilityService {
    private SaintAbilityService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        SaintPlayerState state = component.getSaintState();
        if (SaintRules.isSaint(role)) {
            if (state.initializeAbility()) {
                component.sync();
            }
            GameWorldComponent.KEY.get(player.getServerWorld()).addToPreventGunPickup(player);
        } else if (state.clearAbility()) {
            component.sync();
        }
    }

    public static boolean use(ServerPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        SaintPlayerState state = component.getSaintState();
        if (state.isHellfireActive()) {
            send(player, "message.sparkwitch.saint.hellfire.active");
            return false;
        }
        if (state.hellfireCooldownTicks() > 0) {
            send(
                    player,
                    "message.sparkwitch.saint.hellfire.cooldown",
                    seconds(state.hellfireCooldownTicks())
            );
            return false;
        }
        state.activateHellfire();
        component.sync();
        send(player, "message.sparkwitch.saint.hellfire.activated");
        return true;
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!SaintRules.isSaint(role)) {
            if (component.getSaintState().clearAbility()) {
                component.sync();
            }
            return;
        }
        if (component.getSaintState().tickAbility()) {
            component.sync();
        }
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }

    private static void send(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }
}
