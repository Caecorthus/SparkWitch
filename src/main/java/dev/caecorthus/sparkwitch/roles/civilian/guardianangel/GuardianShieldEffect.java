package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class GuardianShieldEffect extends StatusEffect {
    public GuardianShieldEffect() {
        super(StatusEffectCategory.BENEFICIAL, GuardianAngelRules.COLOR);
    }
}
