package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.CopiedFields;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.Insert;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.NativeEntry;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.Op;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.Remove;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.Replace;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules.StampEntry;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Restricted Time Stealer shop: a role-gated {@code BuildShopEntries} rewrite and a deny-only, role-gated
 * {@code ShopPurchase.BEFORE}. Other killers' shops are untouched.
 * 受限的窃时者商店：按职业门控的 {@code BuildShopEntries} 改写，以及按职业门控、只拒绝的 {@code ShopPurchase.BEFORE}。
 * 其他杀手的商店不受影响。
 *
 * <p>Kidnapper-style deny-list: only poison vial and scorpion are removed; grenade and psycho_mode are replaced in place
 * by 0-coin stamp entries and "+1 minute" is inserted after psycho, so every other entry (blackout, SparkTraits
 * entries, SparkStrength's final appends) keeps its exact coin price, stock, cooldown and callback. The stamp grenade
 * shows the normal Wathe grenade, so SparkStrength still appends its 100-coin M67 (owner decision Q6). Stamp entries
 * cost 0 coins, so SparkTraits Charisma never wraps them and they are identified by id alone.
 * 绑架者式黑名单：只删除毒药瓶与蝎子；手雷与 psycho_mode 原位替换为 0 金币邮票商品，并在疯魔后插入“+1 分钟”，
 * 因此其余商品（停电、SparkTraits 商品、SparkStrength 的末尾追加）保持原有金币价格、库存、冷却与回调。邮票手雷显示
 * 普通 Wathe 手雷，因此 SparkStrength 照常追加 100 金币的 M67（所有者决定 Q6）。邮票商品为 0 金币，SparkTraits 魅力
 * 从不包装它们，因此仅按 id 识别。
 *
 * <p>Authority: the list is built on both sides from the synced role only; the server alone charges stamps, inside
 * each entry's {@code onBuy} via {@link TimeStealerStampService#purchase}. BEFORE only explains refusals and never
 * returns {@code allow}, so later listeners (SparkTraits restrictions) still run and the price stays 0.
 * 权威：两端仅根据已同步的职业构建列表；只有服务端在各商品的 {@code onBuy} 中经 {@link TimeStealerStampService#purchase}
 * 扣除邮票。BEFORE 只负责说明拒绝原因、从不返回允许，因此后续监听器（SparkTraits 限制）仍会运行且价格保持 0。
 */
public final class TimeStealerShopService {
    private static final int SHOP_DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private TimeStealerShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(TimeStealerShopService::buildEntries);
        ShopPurchase.BEFORE.register(TimeStealerShopService::beforePurchase);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!TimeStealerRules.isTimeStealer(role)) {
            return;
        }
        List<NativeEntry> natives = new ArrayList<>(context.size());
        for (int index = 0; index < context.size(); index++) {
            ShopEntry entry = context.getEntry(index);
            natives.add(new NativeEntry(entry.id(), isPoisonDisplay(entry)));
        }
        for (Op op : TimeStealerShopRules.plan(natives)) {
            if (op instanceof Remove remove) {
                context.removeEntry(remove.index());
            } else if (op instanceof Replace replace) {
                context.setEntry(replace.index(), stampEntry(replace.entry(), context.getEntry(replace.index())));
            } else if (op instanceof Insert insert) {
                context.addEntry(insert.index(), stampEntry(insert.entry(), null));
            }
        }
    }

    /**
     * Deny-only: exact role first (every other role returns null untouched), then readable refusals; never allow.
     * {@code onBuy} stays the authoritative backstop because another listener may answer first.
     * 只拒绝：先判精确职业（其他职业一律原样返回 null），再给出可读的拒绝原因；从不允许。
     * 由于其他监听器可能先返回结果，{@code onBuy} 仍是权威的最后防线。
     */
    private static @Nullable ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry,
                                                                        int index) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!TimeStealerRules.isTimeStealer(game.getRole(player))) {
            return null;
        }
        int cost = TimeStealerShopRules.stampCost(entry.id());
        if (cost <= 0) {
            return null;
        }
        if (TimeStampInventory.balance(player) < cost) {
            return ShopPurchase.PurchaseResult.deny("shop.error.sparkwitch.not_enough_time_stamps");
        }
        if (TimeStealerShopRules.PSYCHO_ENTRY_ID.equals(entry.id()) && isPsychoActive(player)) {
            return ShopPurchase.PurchaseResult.deny("shop.error.sparkwitch.time_stealer_psycho_active");
        }
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return ShopPurchase.PurchaseResult.deny("shop.error.sparkwitch.time_stealer_round_over");
        }
        return null;
    }

    private static ShopEntry stampEntry(StampEntry kind, @Nullable ShopEntry nativeEntry) {
        CopiedFields fields = TimeStealerShopRules.copiedFields(kind, nativeEntry == null ? null
                : new CopiedFields(nativeEntry.cooldownTicks(), nativeEntry.initialCooldownTicks(),
                nativeEntry.maxStock()));
        ShopEntry.Builder builder = switch (kind) {
            // Always the normal grenade display (Q6): SparkStrength's M67 keys on it.
            // 始终使用普通手雷展示（Q6）：SparkStrength 的 M67 依赖它。
            case GRENADE -> new ShopEntry.Builder(kind.id(), WatheItems.GRENADE.getDefaultStack(), 0,
                    ShopEntry.Type.WEAPON);
            case PSYCHO -> new ShopEntry.Builder(kind.id(), nativeEntry == null
                    ? WatheItems.PSYCHO_MODE.getDefaultStack() : nativeEntry.displayStack().copy(), 0,
                    nativeEntry == null ? ShopEntry.Type.WEAPON : nativeEntry.type());
            case ADD_TIME -> new ShopEntry.Builder(kind.id(), addTimeDisplay(), 0, ShopEntry.Type.TOOL);
        };
        Predicate<PlayerEntity> effect = switch (kind) {
            // The native grenade onBuy inserts a GRENADE into a free hotbar slot. / 原生手雷 onBuy 将手雷放入空快捷栏格。
            case GRENADE -> nativeEntry != null ? nativeEntry::onBuy
                    : buyer -> ShopEntry.insertStackInFreeSlot(buyer, WatheItems.GRENADE.getDefaultStack());
            case PSYCHO -> TimeStealerShopService::startPsycho;
            case ADD_TIME -> TimeStealerShopService::addTime;
        };
        String id = kind.id();
        int cost = kind.cost();
        return builder
                .cooldown(fields.cooldownTicks())
                .initialCooldown(fields.initialCooldownTicks())
                .stock(fields.maxStock())
                .onBuy(buyer -> buyer instanceof ServerPlayerEntity serverBuyer
                        && TimeStealerStampService.purchase(serverBuyer, id, cost, effect))
                .build();
    }

    /**
     * Re-entering Psycho while active would double-count Wathe's psychosActive counter, so an active Psycho fails.
     * 疯魔进行中再次进入会重复计入 Wathe 的 psychosActive 计数，因此疯魔进行中时失败。
     */
    private static boolean startPsycho(PlayerEntity buyer) {
        return !isPsychoActive(buyer) && PlayerShopComponent.usePsychoMode(buyer);
    }

    private static boolean addTime(PlayerEntity buyer) {
        GameWorldComponent game = GameWorldComponent.KEY.get(buyer.getWorld());
        GameTimeComponent time = GameTimeComponent.KEY.get(buyer.getWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || time.getTime() <= 0) {
            return false;
        }
        time.addTime(TimeStealerRules.STAMP_ADD_TIME_TICKS);
        return true;
    }

    private static boolean isPsychoActive(PlayerEntity player) {
        return PlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0;
    }

    private static boolean isPoisonDisplay(ShopEntry entry) {
        ItemStack display = entry.displayStack();
        return display != null && (display.isOf(WatheItems.POISON_VIAL) || display.isOf(WatheItems.SCORPION));
    }

    private static ItemStack addTimeDisplay() {
        ItemStack stack = Items.CLOCK.getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkwitch.time_stealer.add_time"));
        // Wathe's shop screen renders display-stack lore as the entry description. / Wathe 商店界面将展示物品的 lore 渲染为商品描述。
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkwitch.time_stealer.add_time.description")
                        .styled(style -> style.withItalic(false).withColor(SHOP_DESCRIPTION_COLOR)))));
        return stack;
    }
}
