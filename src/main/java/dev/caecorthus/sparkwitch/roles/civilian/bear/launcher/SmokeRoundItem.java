package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.item.Item;

/**
 * The 40 mm smoke grenade ({@code sparkwitch:smoke_round_40mm}): a plain round loaded into the M32A1 with the cursor;
 * it cannot be thrown by hand (D11) and does no impact damage (G1). Stacks to {@link BearRules#SMOKE_ROUND_MAX_STACK}.
 * WP0 stub: the loading click is WP1.
 * 40 mm 烟雾榴弹（{@code sparkwitch:smoke_round_40mm}）：用光标装入 M32A1 的普通弹药；不能用手投掷（D11），没有撞击伤害
 * （G1）。最多堆叠 {@link BearRules#SMOKE_ROUND_MAX_STACK} 个。WP0 桩：装填点击归 WP1。
 */
public class SmokeRoundItem extends Item {
    public SmokeRoundItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(BearRules.SMOKE_ROUND_MAX_STACK);
    }
}
