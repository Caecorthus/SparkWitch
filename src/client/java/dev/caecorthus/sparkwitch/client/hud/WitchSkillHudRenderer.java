package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.emma.EmmaClientModule;
import dev.caecorthus.sparkwitch.client.gui.OwnerInventoryPresenter;
import dev.caecorthus.sparkwitch.client.text.WitchSkillClientTexts;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillHudRules;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Renders the active witch skill status in the same bottom-right area as wathe role abilities.
 * 在右下角显示魔女主动技能状态，位置和 wathe / NoellesRoles 的技能提示保持一致。
 */
public final class WitchSkillHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int LINE_GAP = 2;

    private WitchSkillHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }

        if (EmmaClientModule.isEmma(player)) {
            EmmaClientModule.renderHud(context, player);
            return;
        }
        // While the owner inventory card lays out the whole skill section it shows these same states (last frame's
        // state; the HUD draws before the screen), so the bottom-right lines would only duplicate it and, for the
        // Grand Witch's four lines, draw under the card and poke out around it. Emma's HUD above is never hidden.
        // 背包卡片完整显示技能分节时已包含相同状态（取上一帧状态，HUD 先于界面绘制），右下角文字只会重复，且大魔女的四行会压在卡片下方
        // 并从四周露出，因此跳过；上方艾玛的 HUD 从不隐藏。
        if (OwnerInventoryPresenter.showsSkillSection(MinecraftClient.getInstance().currentScreen)) {
            return;
        }
        if (GrandWitchClientPresentation.isGrandWitch(player)) {
            GrandWitchClientPresentation.renderHud(context, MinecraftClient.getInstance().textRenderer, player);
            return;
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        boolean hasSkillLine = skillId != null && !FocusedFootstepsRules.SKILL_ID.equals(skillId);
        renderProphetSenseLine(context, renderer, player, hasSkillLine ? y - renderer.fontHeight - LINE_GAP : y);
        if (skillId == null) {
            return;
        }
        if (FocusedFootstepsRules.SKILL_ID.equals(skillId)) {
            return;
        }

        int balance = PlayerShopComponent.KEY.get(player).getBalance();
        Text line = stateText(component, skillId, balance);
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        context.drawTextWithShadow(renderer, line, x, y, WitchSkillClientTexts.color(skillId));
    }

    /**
     * Draws the passive Death Sense countdown just above the skill line. The owner-only component is cleared by the
     * server whenever its holder is not a Prophet, so its running flag is the only gate needed.
     * 在技能行上方绘制被动死亡感知倒计时。服务端会在持有者不是先知时清空这个仅所有者组件，因此只需检查其运行标记。
     */
    private static void renderProphetSenseLine(DrawContext context, TextRenderer renderer, ClientPlayerEntity player, int y) {
        ProphetPlayerComponent prophet = ProphetPlayerComponent.KEY.get(player);
        if (!prophet.isSenseRunning()) {
            return;
        }
        // Below 20 TPS the client countdown can reach zero before the server pulse; show "imminent", never "0s".
        // 服务端低于 20 TPS 时客户端倒计时可能先归零；此时显示“即将发动”，而不是“0 秒”。
        int senseSeconds = seconds(prophet.senseRemainingTicks());
        Text line = senseSeconds > 0
                ? Text.translatable("hud.sparkwitch.prophet.sense.countdown", senseSeconds)
                : Text.translatable("hud.sparkwitch.prophet.sense.imminent");
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        context.drawTextWithShadow(renderer, line, x, y, ProphetRules.CORPSE_HIGHLIGHT_COLOR);
    }

    private static Text stateText(WitchPlayerComponent component, Identifier skillId, int balance) {
        int activeTicks = component.getActiveSkillWindowTicks();
        if (activeTicks > 0) {
            if (MurderousWitchDeathRayRules.isDeathRaySkill(skillId) && component.hasActiveDeathRay()) {
                return Text.translatable(
                        "hud.sparkwitch.skill.death_ray.active",
                        WitchSkillClientTexts.name(skillId),
                        seconds(component.getDeathRayTicks()),
                        component.getDeathRayCharges()
                );
            }
            return Text.translatable(
                    "hud.sparkwitch.skill.active",
                    WitchSkillClientTexts.name(skillId),
                    seconds(activeTicks)
            );
        }
        if (WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                skillId,
                component.getGrandWitchCeremonialSwordTasks(),
                activeTicks,
                component.getCooldownTicks()
        )) {
            return Text.translatable(
                    "hud.sparkwitch.skill.ceremonial_sword.locked",
                    component.getGrandWitchCeremonialSwordTasks(),
                    GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS
            );
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.skill.cooldown",
                    WitchSkillClientTexts.name(skillId),
                    seconds(component.getCooldownTicks())
            );
        }
        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        int manaCost = skill == null ? 0 : skill.manaCost();
        if (WitchSkillHudRules.shouldShowManaRequirement(
                skillId,
                component.getMana(),
                manaCost,
                activeTicks,
                component.getCooldownTicks(),
                component.getGrandWitchCeremonialSwordTasks()
        )) {
            return Text.translatable(
                    "hud.sparkwitch.skill.not_enough_mana",
                    manaCost
            );
        }
        if (WitchSkillHudRules.shouldShowPigChaseCoinRequirement(
                skillId,
                balance,
                activeTicks,
                component.getCooldownTicks()
        )) {
            return Text.translatable(
                    "hud.sparkwitch.skill.pig_chase.not_enough_money",
                    PigGodRules.COIN_COST
            );
        }
        if (WitchSkillHudRules.shouldShowProphetCoinCost(
                skillId,
                activeTicks,
                component.getCooldownTicks()
        )) {
            return Text.translatable(
                    "hud.sparkwitch.skill.prophecy.coin_cost",
                    ProphetRules.PROPHECY_COIN_COST
            );
        }
        return Text.translatable(
                "hud.sparkwitch.skill.ready",
                WitchSkillClientTexts.name(skillId),
                SparkWitchClient.abilityKeyText()
        );
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}
