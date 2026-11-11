package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.api.client.SecondarySkillKeyApi;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.emma.EmmaVisibleName;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Two bottom-right lines for the Specter, drawn from the {@code HudRenderCallback} that {@code SpecterClientModule}
 * registers: skill one (mark → shriek, or its cooldown) on the bottom row and the Otherworld state (locked / ready /
 * active / cooldown) above it. Both rows sit in the shared SparkWitch corner slot, which
 * {@code SaintHudLayoutRules} reserves two rows high for the Specter so the Karma line stacks above. Reads only the
 * local player's raw synced role and owner-synced Specter state; the server re-validates every press.
 * 幽魂右下角两行提示，由 {@code SpecterClientModule} 注册的 {@code HudRenderCallback} 绘制：下行为技能一（标记 → 尖啸，或其
 * 冷却），上行为里世界状态（未解锁 / 可用 / 进行中 / 冷却）。两行都位于 SparkWitch 共享右下角位置，
 * {@code SaintHudLayoutRules} 为幽魂预留两行高度，使业障行叠放在其上方。只读取本地玩家真实同步的身份与仅同步给本人的
 * 幽魂状态；每次按键都由服务端重新校验。
 */
public final class SpecterHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int LINE_GAP = 2;
    private static final int COLOR = 0xFF000000 | SpecterRules.PRIMARY_HUD_COLOR;

    private SpecterHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isActiveSpecter(player)) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int bottomY = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        drawRight(context, renderer, skillOneText(player, SpecterPlayerComponent.KEY.get(player)), bottomY);
        drawRight(context, renderer, otherworldText(SpecterSecondSkillComponent.KEY.get(player)),
                bottomY - renderer.fontHeight - LINE_GAP);
    }

    /**
     * Local player is the Specter (raw synced role) on a confirmed SparkWitch server, alive, playing, not swallowed.
     * 本地玩家在已确认的 SparkWitch 服务器上是幽魂（真实同步身份），存活、参与对局、未被吞下。
     */
    static boolean isActiveSpecter(@Nullable ClientPlayerEntity player) {
        return player != null
                && SpecterRules.isSpecter(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && SparkWitchServerConnection.isConfirmedServer()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    private static Text skillOneText(ClientPlayerEntity player, SpecterPlayerComponent component) {
        if (component.isOnCooldown()) {
            return Text.translatable(
                    "hud.sparkwitch.specter.cooldown",
                    TickTime.ceilSeconds(component.getCooldownTicks())
            );
        }
        if (component.hasActiveMark()) {
            return Text.translatable(
                    "hud.sparkwitch.specter.shriek",
                    SparkWitchClient.abilityKeyText(),
                    markedName(player, component.getMarkedTarget()),
                    TickTime.ceilSeconds(component.getMarkRemainingTicks())
            );
        }
        PlayerEntity target = SpecterRules.findMarkTarget(player);
        if (target == null || target.isCreative()) {
            return Text.translatable("hud.sparkwitch.specter.no_target");
        }
        return Text.translatable(
                "hud.sparkwitch.specter.mark",
                SparkWitchClient.abilityKeyText(),
                visibleName(target)
        );
    }

    private static Text otherworldText(SpecterSecondSkillComponent component) {
        if (component.isActive()) {
            return Text.translatable(
                    "hud.sparkwitch.specter.otherworld.active",
                    TickTime.ceilSeconds(component.getActiveTicks())
            );
        }
        if (component.getUnlockTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.specter.otherworld.locked",
                    TickTime.ceilSeconds(component.getUnlockTicks())
            );
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.specter.otherworld.cooldown",
                    TickTime.ceilSeconds(component.getCooldownTicks())
            );
        }
        return Text.translatable("hud.sparkwitch.specter.otherworld.ready", SecondarySkillKeyApi.boundKeyText());
    }

    /** Marked player's visible name, or a generic label when that player is not loaded here. / 被标记者的可见名，未加载时用通用文案。 */
    private static Text markedName(ClientPlayerEntity viewer, @Nullable UUID targetUuid) {
        PlayerEntity target = targetUuid == null ? null : viewer.getWorld().getPlayerByUuid(targetUuid);
        return target == null ? Text.translatable("hud.sparkwitch.specter.marked_unknown") : visibleName(target);
    }

    /**
     * The name other players see: Wathe's psycho scramble first, then {@link EmmaVisibleName} (disguises, corpse mode,
     * invisibility), the same rule as the Dominator HUD.
     * 其他玩家看到的名字：先套用 Wathe 疯魔名牌乱码，再交给 {@link EmmaVisibleName}（伪装、尸体模式、隐身），与支配者 HUD 规则一致。
     */
    private static Text visibleName(PlayerEntity target) {
        if (PlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0) {
            return Text.literal("urscrewed").formatted(Formatting.OBFUSCATED, Formatting.DARK_RED);
        }
        return EmmaVisibleName.of(target);
    }

    private static void drawRight(DrawContext context, TextRenderer renderer, Text text, int y) {
        context.drawTextWithShadow(
                renderer,
                text,
                Math.max(0, context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(text)),
                y,
                COLOR
        );
    }
}
