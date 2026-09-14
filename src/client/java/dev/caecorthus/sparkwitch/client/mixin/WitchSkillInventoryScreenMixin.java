package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.text.WitchSkillClientTexts;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillHudRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillPresentationRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LimitedInventoryScreen.class)
public abstract class WitchSkillInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 8;
    private static final int LINE_GAP = 2;

    @Shadow
    @Final
    public ClientPlayerEntity player;

    public WitchSkillInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(
            method = "method_25394(Lnet/minecraft/class_332;IIF)V",
            at = @At("TAIL")
    )
    private void sparkwitch$renderOwnerSkill(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        if (skillId == null) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, skillId)) {
            return;
        }

        int x = PANEL_X;
        int y = PANEL_Y;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.sparkwitch.skills"), x, y, 0xFFFFFF);
        y += this.textRenderer.fontHeight + 4;
        if (GrandWitchClientPresentation.isGrandWitch(player)) {
            sparkwitch$renderGrandWitchSkills(context, mouseX, mouseY, x, y, component);
            return;
        }

        Text tag = WitchSkillClientTexts.tag(skillId);
        int tagWidth = this.textRenderer.getWidth(tag);
        context.drawTextWithShadow(this.textRenderer, tag, x, y, WitchSkillClientTexts.color(skillId));

        int hoverTop = y;
        y += this.textRenderer.fontHeight + LINE_GAP;
        Text state = stateText(component);
        int stateWidth = this.textRenderer.getWidth(state);
        context.drawTextWithShadow(this.textRenderer, state, x, y, 0xE7D8FF);

        int hoverWidth = Math.max(tagWidth, stateWidth);
        int hoverBottom = y + this.textRenderer.fontHeight;
        if (mouseX >= x && mouseX <= x + hoverWidth && mouseY >= hoverTop && mouseY <= hoverBottom) {
            context.drawTooltip(
                    this.textRenderer,
                    WitchSkillClientTexts.tooltip(
                            skillId,
                            component.getCooldownTicks(),
                            component.getActiveSkillWindowTicks(),
                            component.getGrandWitchCeremonialSwordTasks()
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    /** Only reached after the strict role/own-skill panel gate. / 仅在严格的身份及自有技能面板校验后进入。 */
    private void sparkwitch$renderGrandWitchSkills(DrawContext context, int mouseX, int mouseY,
                                                  int x, int y, WitchPlayerComponent component) {
        java.util.List<java.util.List<Text>> states = java.util.List.of(
                java.util.List.of(GrandWitchClientPresentation.factorState(player)),
                java.util.List.of(GrandWitchClientPresentation.recruitmentState(player)),
                GrandWitchClientPresentation.swordStates(player)
        );
        java.util.List<Text> titles = java.util.List.of(
                WitchSkillClientTexts.tag(GrandWitchClientPresentation.FACTOR_ID),
                Text.translatable("skill.sparkwitch.recruit_accomplice.name"),
                Text.translatable("skill.sparkwitch.ceremonial_sword.name")
        );
        java.util.List<java.util.List<Text>> tooltips = java.util.List.of(
                WitchSkillClientTexts.tooltip(GrandWitchClientPresentation.FACTOR_ID, component.getCooldownTicks()),
                java.util.List.of(Text.translatable("skill.sparkwitch.recruit_accomplice.description"),
                        Text.translatable("skill.sparkwitch.recruit_accomplice.inventory")),
                java.util.List.of(Text.translatable("skill.sparkwitch.ceremonial_sword.description"),
                        Text.translatable("skill.sparkwitch.ceremonial_sword.protection"))
        );
        java.util.List<Text> hovered = null;
        for (int i = 0; i < titles.size(); i++) {
            int top = y;
            int width = this.textRenderer.getWidth(titles.get(i));
            context.drawTextWithShadow(this.textRenderer, titles.get(i), x, y, GrandWitchClientPresentation.COLOR);
            y += this.textRenderer.fontHeight + LINE_GAP;
            for (Text state : states.get(i)) {
                width = Math.max(width, this.textRenderer.getWidth(state));
                context.drawTextWithShadow(this.textRenderer, state, x, y, 0xE7D8FF);
                y += this.textRenderer.fontHeight + LINE_GAP;
            }
            if (mouseX >= x && mouseX <= x + width && mouseY >= top && mouseY < y) {
                hovered = tooltips.get(i);
            }
            y += 4;
        }
        if (hovered != null) {
            java.util.List<net.minecraft.text.OrderedText> wrapped = new java.util.ArrayList<>();
            int width = Math.min(260, Math.max(100, this.width - 24));
            for (Text line : hovered) {
                wrapped.addAll(this.textRenderer.wrapLines(line, width));
            }
            context.drawOrderedTooltip(this.textRenderer, wrapped, mouseX, mouseY);
        }
    }

    private static Text stateText(WitchPlayerComponent component) {
        int activeTicks = component.getActiveSkillWindowTicks();
        if (activeTicks > 0) {
            return Text.translatable("gui.sparkwitch.skill.active", (int) Math.ceil(activeTicks / 20.0));
        }
        if (WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                component.getActiveSkillId(),
                component.getGrandWitchCeremonialSwordTasks(),
                activeTicks,
                component.getCooldownTicks()
        )) {
            return Text.translatable(
                    "gui.sparkwitch.skill.ceremonial_sword.locked",
                    component.getGrandWitchCeremonialSwordTasks(),
                    GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS
            );
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable("gui.sparkwitch.skill.cooldown", (int) Math.ceil(component.getCooldownTicks() / 20.0));
        }
        return Text.translatable("gui.sparkwitch.skill.ready");
    }
}
