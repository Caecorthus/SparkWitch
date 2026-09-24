package dev.caecorthus.sparkwitch.client.emma;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.factor.WitchFactorClientHooks;
import dev.caecorthus.sparkwitch.client.factor.WitchFactorOutlineRules;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.EmmaFactorC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Role-owned presentation/dispatch only; the server owns target eligibility and spending. / 职业自有展示与派发；目标资格及消耗由服务端决定。 */
public final class EmmaClientModule {
    private EmmaClientModule() {
    }

    public static void register() {
        WitchFactorClientHooks.registerPrivateRevealProvider((viewer, target) -> {
            if (!isEmma(viewer) || !EmmaPlayerComponent.KEY.get(viewer).hasRevealedGrandWitch(target.getUuid())) {
                return -1;
            }
            // Permanent private evidence keeps the attempting role's color even after its role changes.
            // 永久私有证据保持尝试招募时的大魔女职业色，不随对方后续转职改变。
            return SparkWitchRoles.grandWitch().color();
        });
    }

    public static boolean isEmma(PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && EmmaRules.ROLE_ID.equals(role.identifier());
    }

    /** Called only by the existing shared-key bridge, never a second wasPressed consumer. / 只由已有共用技能键桥调用，不另读按键队列。 */
    public static void use(ClientPlayerEntity player) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !isEmma(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !ClientPlayNetworking.canSend(EmmaFactorC2SPacket.ID)) {
            return;
        }
        PlayerEntity target = EmmaClientTargeting.findTarget(player);
        ClientPlayNetworking.send(new EmmaFactorC2SPacket(target == null ? null : target.getUuid()));
    }

    /** Bottom-right role HUD, intentionally outside the witch-only inventory skill panel. / 右下角职业 HUD，刻意不接入魔女专属背包技能面板。 */
    public static void renderHud(DrawContext context, ClientPlayerEntity player) {
        WitchPlayerComponent state = WitchPlayerComponent.KEY.get(player);
        PlayerEntity target = EmmaClientTargeting.findTarget(player);
        Text line = switch (EmmaHudRules.state(state.getCooldownTicks(), state.getMana(),
                EmmaRules.MANA_COST, target != null)) {
            case COOLDOWN -> Text.translatable("hud.sparkwitch.emma.factor.cooldown",
                    (int) Math.ceil(state.getCooldownTicks() / 20.0));
            case NEED_MANA -> Text.translatable("hud.sparkwitch.emma.factor.need_mana", EmmaRules.MANA_COST);
            case NO_TARGET -> Text.translatable("hud.sparkwitch.emma.factor.no_target");
            case TRANSFER -> Text.translatable("hud.sparkwitch.emma.factor.transfer", EmmaVisibleName.of(target));
        };
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(renderer, line,
                context.getScaledWindowWidth() - 5 - renderer.getWidth(line),
                context.getScaledWindowHeight() - 5 - renderer.fontHeight, WitchFactorOutlineRules.EMMA_COLOR);
    }
}
