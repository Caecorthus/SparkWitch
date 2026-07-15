package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintPlayerState;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Builds the owner-only Saint Hellfire or Karma countdown line.
 * 构造仅本人可见的圣徒业火或业障倒计时文本。
 */
public final class SaintHudRenderer {
    private SaintHudRenderer() {
    }

    public static List<Text> getHudLines(ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        SaintPlayerState state = component.getSaintState();
        List<Text> lines = new ArrayList<>(2);
        if (SaintRules.isSaint(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            Text hellfireLine = hellfireLine(state);
            lines.add(hellfireLine);
        }

        if (state.karmaCooldownTicks() > 0) {
            lines.add(Text.translatable(
                    "hud.sparkwitch.saint.karma.active",
                    seconds(state.karmaCooldownTicks())
            ));
        }
        return List.copyOf(lines);
    }

    private static Text hellfireLine(SaintPlayerState state) {
        if (state.isHellfireActive()) {
            return Text.translatable(
                    "hud.sparkwitch.saint.hellfire.active",
                    seconds(state.hellfireActiveTicks())
            );
        }
        if (state.hellfireCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.saint.hellfire.cooldown",
                    seconds(state.hellfireCooldownTicks())
            );
        }
        return Text.translatable(
                "hud.sparkwitch.saint.hellfire.ready",
                SparkWitchClient.abilityKeyText()
        );
    }

    private static int seconds(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0));
    }
}
