package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * D2: before its promotion a Bewitched earns money like an accomplice (starting money, passive and kill income through
 * the accomplice-like economy rules) but cannot shop. Its shop list is emptied from the exact synced role on both
 * sides (never round state: Wathe caches stock limits during STARTING), and because an empty shop hides the balance
 * in Wathe's default, a living Bewitched is explicitly allowed to see it. The promotion rebuilds the shop for the new
 * role.
 * D2：晋升前魔化使像共犯一样赚钱（经共犯类经济规则获得开局金币、被动收入与击杀奖励），但不能使用商店。商店列表按已同步
 * 的精确职业在双端清空（从不依赖对局状态：Wathe 在 STARTING 阶段缓存库存上限）；由于 Wathe 默认在商店为空时隐藏余额，
 * 因此显式允许存活的魔化使看到余额。晋升时会按新身份重建商店。
 */
public final class BewitchedShopService {
    /**
     * Ordered after the default phase, so entries any default-phase listener adds (role or trait shops, whatever the
     * mod load order) are cleared too.
     * 排在默认阶段之后，因此任何默认阶段监听器（职业或词条商店，无论模组加载顺序）追加的条目也会被清空。
     */
    static final Identifier CLEAR_PHASE = SparkWitch.id("bewitched_shop_clear");
    private static boolean registered;

    private BewitchedShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, CLEAR_PHASE);
        BuildShopEntries.EVENT.register(CLEAR_PHASE, BewitchedShopService::buildEntries);
        CanSeeMoney.EVENT.register(player -> player == null ? null : BewitchedRules.moneyVisibility(
                GameFunctions.isPlayerPlayingAndAlive(player), isBewitched(player)));
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (isBewitched(player)) {
            context.clearEntries();
        }
    }

    /** Real role (never a Black Raven acting overlay). / 读取真实身份（不读黑羽鸦伪装覆盖层）。 */
    private static boolean isBewitched(PlayerEntity player) {
        return BewitchedRules.isBewitched(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
