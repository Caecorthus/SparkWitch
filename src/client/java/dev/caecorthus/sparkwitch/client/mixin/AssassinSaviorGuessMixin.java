package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * External NoellesRoles seam (client presentation): hides the Savior from the Assassin's guess panel by filtering the
 * list {@code AssassinScreen#getAllGuessableRoles} returns, leaving every other role and its order untouched. The
 * server half ({@code mixin/savior/NoellesAssassinSaviorGuessMixin}) still drops a guess that names the Savior for a
 * target who really is one, so a modified client gains nothing. Pinned in {@code watheClientMixinContracts}.
 * 外部 NoellesRoles 接缝（客户端展示）：过滤 {@code AssassinScreen#getAllGuessableRoles} 返回的列表，把救世主从刺客猜测面板中
 * 隐藏，其他职业及其顺序保持不变。服务端部分（{@code mixin/savior/NoellesAssassinSaviorGuessMixin}）仍会丢弃对真正救世主
 * 点名救世主的猜测，因此改过的客户端也得不到任何好处。已固定在 {@code watheClientMixinContracts} 中。
 */
@Mixin(AssassinScreen.class)
public abstract class AssassinSaviorGuessMixin {
    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @Inject(method = "getAllGuessableRoles", at = @At("RETURN"), cancellable = true, require = 1)
    private void sparkwitch$hideSaviorFromAssassinGuess(CallbackInfoReturnable<List<Role>> cir) {
        List<Role> roles = cir.getReturnValue();
        if (roles == null || roles.isEmpty()) {
            return;
        }
        boolean found = false;
        for (Role role : roles) {
            if (SaviorRules.isSavior(role)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        List<Role> filtered = new ArrayList<>(roles.size());
        for (Role role : roles) {
            if (!SaviorRules.isSavior(role)) {
                filtered.add(role);
            }
        }
        cir.setReturnValue(filtered);
    }
}
