package dev.caecorthus.sparkwitch.client.mixin.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owner request 2026-11-02: the Stowaway's round-start line is "not welcome aboard" instead of Wathe's shared
 * "welcome aboard". Only this role's announcement text is replaced; every other role keeps the vanilla line.
 * 所有者 2026-11-02 需求：偷渡客的开场台词改为「不欢迎登车」，而不是 Wathe 共用的「欢迎登车」。只替换这个职业的公告文本，
 * 其他职业保持原样。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts$RoleAnnouncementText", remap = false)
public abstract class StowawayWelcomeTextMixin {
    @Shadow
    @Final
    public Identifier identifier;

    @Shadow
    @Final
    public Text roleText;

    @Shadow
    @Final
    @Mutable
    public Text welcomeText;

    @Inject(method = "<init>(Lnet/minecraft/util/Identifier;I)V", at = @At("RETURN"))
    private void sparkwitch$stowawayIsNotWelcome(Identifier identifier, int color, CallbackInfo ci) {
        if (StowawayRules.ROLE_ID.equals(this.identifier)) {
            this.welcomeText = Text.translatable("announcement.stowaway.welcome", this.roleText)
                    .withColor(15790320);
        }
    }
}
