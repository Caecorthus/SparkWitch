package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Gunplay;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Replay;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Registers the Sniper's server side (Team Jiahao) outside the witch skill dispatcher (狮身豪 / 泽渡可可 hub model), in a
 * fixed order: the round-start M1903A4 kit ({@link SniperLoadoutService}), the money rules, which register the shop
 * themselves ({@link SniperEconomyService}, {@link SniperShopService}), the rifle's server sound state machine
 * ({@link M1903A4Gunplay}) and its replay lines ({@link M1903A4Replay}), then the gun-punishment exemption: a Sniper
 * firing a Wathe gun it picked up is never punished for an innocent victim, Team Jiahao parity with the Insider and
 * Sphinx Hao (D5). The M1903A4 is not a Wathe gun and never reaches that event. Never a Witch-skill role: nothing here
 * touches the witch skill panel.
 * 在魔女技能分发路径之外按固定顺序注册狙击手（嘉豪阵营）的服务端（狮身豪 / 泽渡可可注册中心模式）：开局 M1903A4 装备
 * （{@link SniperLoadoutService}）、金钱规则（自行注册商店，{@link SniperEconomyService}、{@link SniperShopService}）、步枪的
 * 服务端音效状态机（{@link M1903A4Gunplay}）与回放文本（{@link M1903A4Replay}），最后是开枪惩罚豁免：狙击手用捡到的 Wathe
 * 枪械击中无辜者时不受惩罚，与内应、狮身豪保持嘉豪阵营一致（D5）。M1903A4 不是 Wathe 枪械，从不触发该事件。不是魔女技能
 * 职业：这里不触及魔女技能面板。
 */
public final class SniperFeatureService {
    private static boolean registered;

    private SniperFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SniperLoadoutService.register();
        SniperEconomyService.register();
        M1903A4Gunplay.register();
        M1903A4Replay.register();
        ShouldPunishGunShooter.EVENT.register(SniperFeatureService::gunPunishment);
    }

    /**
     * Same shape as the Insider's and NoellesRoles' Corrupt Cop listener; Wathe's event is first-non-null-wins.
     * 与内应及 NoellesRoles 黑警监听形状相同；Wathe 该事件取第一个非 null 结果。
     */
    private static @Nullable ShouldPunishGunShooter.PunishResult gunPunishment(PlayerEntity shooter, PlayerEntity victim) {
        return SniperRules.isSniperRole(GameWorldComponent.KEY.get(shooter.getWorld()).getRole(shooter))
                ? ShouldPunishGunShooter.PunishResult.cancel() : null;
    }
}
