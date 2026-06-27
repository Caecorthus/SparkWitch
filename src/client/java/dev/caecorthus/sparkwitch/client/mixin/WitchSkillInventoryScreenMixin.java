package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.WitchSkillClientTexts;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
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

    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderOwnerSkill(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        if (skillId == null) {
            return;
        }

        int x = PANEL_X;
        int y = PANEL_Y;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.sparkwitch.skills"), x, y, 0xFFFFFF);
        y += this.textRenderer.fontHeight + 4;

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
                    WitchSkillClientTexts.tooltip(skillId, component.getCooldownTicks(), component.getCeremonialSwordTicks()),
                    mouseX,
                    mouseY
            );
        }
    }

    private static Text stateText(WitchPlayerComponent component) {
        if (component.getCeremonialSwordTicks() > 0) {
            return Text.translatable("gui.sparkwitch.skill.active", (int) Math.ceil(component.getCeremonialSwordTicks() / 20.0));
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable("gui.sparkwitch.skill.cooldown", (int) Math.ceil(component.getCooldownTicks() / 20.0));
        }
        return Text.translatable("gui.sparkwitch.skill.ready");
    }
}
