package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

/** Renders the Orthopedist's private cooldown and aimed-target state. / 渲染骨科大夫私有冷却与准星目标状态。 */
public final class OrthopedistHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private OrthopedistHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text line = stateText(player);
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, OrthopedistRules.COLOR);
    }

    static Text stateText(ClientPlayerEntity player) {
        OrthopedistPlayerComponent component = OrthopedistPlayerComponent.KEY.get(player);
        if (component.getCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.orthopedist.cooldown",
                    seconds(component.getCooldownTicks())
            );
        }

        PlayerEntity target = aimedPlayer(player);
        if (target == null) {
            return Text.translatable("hud.sparkwitch.orthopedist.no_target");
        }
        if (OrthopedistPlayerComponent.KEY.get(target).hasBoneSettingActive()) {
            return Text.translatable(
                    "hud.sparkwitch.orthopedist.already_active",
                    target.getDisplayName()
            );
        }
        return Text.translatable(
                "hud.sparkwitch.orthopedist.ready",
                SparkWitchClient.abilityKeyText(),
                target.getDisplayName()
        );
    }

    private static PlayerEntity aimedPlayer(ClientPlayerEntity player) {
        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof PlayerEntity target)
                || target == player
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || !player.canSee(target)
                || player.squaredDistanceTo(target) > OrthopedistRules.TARGET_RANGE_SQUARED) {
            return null;
        }
        return target;
    }

    private static int seconds(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0D));
    }
}
