package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;

public final class ProphetSkillService {
    private ProphetSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!ProphetRules.isProphet(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(context.player());
        if (shop.getBalance() < ProphetRules.COIN_COST) {
            return WitchSkillUseResult.fail(
                    "message.sparkwitch.skill.death_omen.not_enough_money"
            );
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        shop.setBalance(shop.getBalance() - ProphetRules.COIN_COST);
        component.beginDeathOmenWindow(ProphetRules.ACTIVE_TICKS);
        return WitchSkillUseResult.successAfterActiveWindow(
                ProphetRules.POST_COOLDOWN_TICKS,
                "message.sparkwitch.skill.death_omen.activated"
        );
    }
}
