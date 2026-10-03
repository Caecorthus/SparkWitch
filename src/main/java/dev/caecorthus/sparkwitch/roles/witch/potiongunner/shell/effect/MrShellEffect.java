package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * GW-MR: gold deduction scaled by falloff, clamped at a zero balance. The coins are destroyed, never paid to the
 * gunner (D7). It acts on the target's live Wathe wallet, so a disguised Black Raven loses from its current
 * disguise wallet.
 * GW-MR：按衰减扣除金币，余额不低于 0。扣掉的金币直接销毁，不归药炮手（D7）。作用于目标当前的 Wathe 钱包，
 * 因此伪装中的黑羽鸦扣的是其当前伪装身份的钱包。
 */
public final class MrShellEffect {
    static final String BURNED_MESSAGE = "message.sparkwitch.potion_gunner.mr_burned";

    private MrShellEffect() {
    }

    public static void apply(PotionBlastContext context) {
        for (PotionBlastHit hit : context.hits()) {
            ServerPlayerEntity target = hit.target();
            // The resolver already dropped allies and the gunner; both flags are re-checked fail-closed.
            // 判定器已剔除队友与药炮手本人；此处再按失败即关闭的原则复查两项标记。
            if (hit.ally() || PotionShellTargets.isSelf(context, hit) || !PotionShellTargets.isLiving(target)) {
                continue;
            }
            PlayerShopComponent shop = PlayerShopComponent.KEY.get(target);
            int balance = shop.getBalance();
            int taken = MrShellEffectRules.amount(hit.factor(), balance);
            if (taken <= 0) {
                continue;
            }
            // setBalance syncs the owner's shop component. / setBalance 会同步持有者的商店组件。
            shop.setBalance(balance - taken);
            target.sendMessage(Text.translatable(BURNED_MESSAGE, taken), true);
        }
    }
}
