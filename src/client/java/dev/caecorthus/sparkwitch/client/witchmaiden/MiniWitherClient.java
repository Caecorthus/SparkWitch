package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherEntities;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net.ToggleMiniWitherC2SPayload;
import dev.caecorthus.sparkwitch.util.TickTime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Mini Wither: key 2 sends the toggle request (the server checks the unlock, cooldown and states and
 * answers a refusal on the action bar), the Wither and skull renderers, the Gohei left-click input, and the bottom-right
 * HUD lines.
 * 小凋灵的客户端部分：技能键 2 发送切换请求（解锁、冷却与各种状态由服务端校验，拒绝时在动作栏回复）、小凋灵与头颅的渲染、
 * 凋灵御币左键输入，以及右下角的 HUD 行。
 */
public final class MiniWitherClient {
    private static boolean registered;

    private MiniWitherClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SecondaryAbilityRegistry.register(WitchMaidenRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new ToggleMiniWitherC2SPayload());
                }
            }
        });
        // Fired once per player renderer (default and slim). / 每个玩家渲染器（默认与纤细）各触发一次。
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new MiniWitherRenderer.Feature(playerRenderer));
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(MiniWitherRenderer::renderCameraWither);
        EntityRendererRegistry.register(MiniWitherEntities.skull(), MiniWitherSkullEntityRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                WitherGoheiFireInput.reset();
                MiniWitherFollow.clear();
                return;
            }
            WitherGoheiFireInput.tick(client);
            MiniWitherFollow.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WitherGoheiFireInput.reset();
            MiniWitherFollow.clear();
        });
    }

    /**
     * The Witch Maiden's bottom-right lines, newest state first from the bottom: the Wither line, then (while the Wither
     * is out) the crimson skull line above it. Focused Footsteps keeps its avatar countdown and draws no line.
     * 巫女的右下角状态行，自下而上：小凋灵行，然后（小凋灵在场时）上方的赤首行。聚焦步伐仍使用头像倒计时，不绘制状态行。
     */
    public static void renderHud(DrawContext context, TextRenderer renderer, ClientPlayerEntity player, int rightX,
                                 int bottomY, int lineGap) {
        int y = bottomY;
        for (Text line : hudLines(player)) {
            context.drawTextWithShadow(renderer, line, rightX - renderer.getWidth(line), y, WitchMaidenRules.COLOR);
            y -= renderer.fontHeight + lineGap;
        }
    }

    static List<Text> hudLines(ClientPlayerEntity player) {
        MiniWitherPlayerComponent wither = MiniWitherPlayerComponent.KEY.get(player);
        List<Text> lines = new ArrayList<>(2);
        if (!wither.isUnlocked()) {
            lines.add(Text.translatable("hud.sparkwitch.mini_wither.locked", wither.getCompletedTasks(),
                    MiniWitherRules.UNLOCK_TASKS));
            return lines;
        }
        if (wither.getToggleCooldownTicks() > 0) {
            lines.add(Text.translatable("hud.sparkwitch.mini_wither.cooldown",
                    TickTime.ceilSecondsUnclamped(wither.getToggleCooldownTicks())));
        } else {
            lines.add(Text.translatable(wither.isActive() ? "hud.sparkwitch.mini_wither.dismiss"
                    : "hud.sparkwitch.mini_wither.summon", SecondaryAbilityController.secondaryKeyText()));
        }
        if (!wither.isActive()) {
            return lines;
        }
        int crimsonTicks = goheiCooldownTicks(player);
        if (crimsonTicks > 0) {
            lines.add(Text.translatable("hud.sparkwitch.mini_wither.crimson.cooldown",
                    TickTime.ceilSecondsUnclamped(crimsonTicks)));
        } else if (WitchPlayerComponent.KEY.get(player).getMana() < MiniWitherRules.CRIMSON_MANA_COST) {
            lines.add(Text.translatable("hud.sparkwitch.mini_wither.crimson.not_enough_mana",
                    MiniWitherRules.CRIMSON_MANA_COST));
        } else {
            lines.add(Text.translatable("hud.sparkwitch.mini_wither.crimson.ready"));
        }
        return lines;
    }

    /** The Gohei's own item cooldown, so any external change shows. / 御币自身的物品冷却，外部改动也会显示。 */
    private static int goheiCooldownTicks(ClientPlayerEntity player) {
        ItemCooldownManagerAccessor manager = (ItemCooldownManagerAccessor) player.getItemCooldownManager();
        Object entry = manager.sparkwitch$getEntries().get(SparkWitchItems.witherGohei());
        return entry instanceof ItemCooldownEntryAccessor cooldown
                ? Math.max(0, cooldown.sparkwitch$getEndTick() - manager.sparkwitch$getTick()) : 0;
    }
}
