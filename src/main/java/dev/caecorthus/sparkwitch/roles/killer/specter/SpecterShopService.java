package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * The Specter's role shop: one ghost-trigger entry, 50 coins, poison-style frame, inserted between the shared shop's
 * weapon/poison group and its tool group. Registered in the DEFAULT phase like every other killer shop (Hunter, Ninja,
 * Bell Ringer, Time Stealer, Witch Maiden): Wathe seeds the base entries into the context before the event runs, so
 * the insertion point exists, and SparkTraits' {@code sparktraits:charisma_discount} phase (ordered after DEFAULT)
 * still discounts this entry for a Charisma Specter.
 * 幽魂职业商店：一个鬼魂触发器条目，50 金币，毒药类边框，插在通用商店的武器/毒药组与工具组之间。与其他杀手商店（猎人、忍者、
 * 敲钟人、窃时者、巫女）一样注册在默认阶段：Wathe 在事件运行前已把基础条目放入上下文，因此插入点已存在；SparkTraits 排在默认
 * 阶段之后的 {@code sparktraits:charisma_discount} 阶段仍会为拥有魅力的幽魂打折本条目。
 *
 * <p>The same listener drops Wathe's native Psycho Mode, Poison Vial and Scorpion from the Specter's shop (owner
 * decision 2026-11-10: the Specter sells none of the three). The removal is role-scoped on purpose: other roles sell,
 * re-price or re-skin entries under these ids (Grand Witch, Witch Maiden, 夏目安安, Bell Ringer, Hunter, the Time
 * Stealer's stamp Psycho, NoellesRoles' Silencer), and SparkTraits / SparkStrength read {@code psycho_mode} in the
 * final list, so a filter on {@code ShopUtils.getShopEntriesForPlayer} would break them. Runs on both sides, so
 * purchase indexes match.
 * <p>同一监听器会从幽魂商店移除 Wathe 原生的疯魔模式、毒药瓶与蝎子（所有者 2026-11-10 决定：幽魂三者都不出售）。移除范围刻意
 * 限定在本职业：其他职业会以这些 id 出售、改价或换皮（大魔女、巫女、夏目安安、敲钟人、猎人、窃时者的邮票疯魔、NoellesRoles
 * 的静语者），SparkTraits / SparkStrength 也会在最终列表中读取 {@code psycho_mode}，因此不能在
 * {@code ShopUtils.getShopEntriesForPlayer} 上做全局过滤。双端都会执行，购买下标保持一致。
 */
public final class SpecterShopService {
    public static final String ENTRY_ID = "ghost_trigger";
    /** Ids emitted by Wathe's {@code KillerShopBuilder}. / Wathe {@code KillerShopBuilder} 产出的条目 id。 */
    static final Set<String> REMOVED_NATIVE_ENTRY_IDS = Set.of("psycho_mode", "poison_vial", "scorpion");
    private static final int PRICE = 50;
    private static boolean registered;

    private SpecterShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SpecterShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!SpecterRules.isSpecter(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        // Before the insert, so the Ghost Trigger still lands on the weapon/tool seam. / 先移除再插入，触发器仍落在武器与工具之间。
        context.getEntries().removeIf(nativeEntry -> isRemovedNativeEntry(nativeEntry.id()));
        ShopEntry entry = new ShopEntry.Builder(
                ENTRY_ID,
                new ItemStack(SparkWitchItems.ghostTrigger()),
                PRICE,
                ShopEntry.Type.POISON
        ).build();
        context.addEntry(toolGroupIndex(context), entry);
    }

    /** True for the three native entries the Specter does not sell. / 幽魂不出售的三个原生条目返回 true。 */
    static boolean isRemovedNativeEntry(String entryId) {
        return entryId != null && REMOVED_NATIVE_ENTRY_IDS.contains(entryId);
    }

    /** First tool entry, i.e. the seam after weapons/poisons; the end when no tool entry exists. / 第一个工具条目。 */
    private static int toolGroupIndex(BuildShopEntries.ShopContext context) {
        for (int index = 0; index < context.size(); index++) {
            if (context.getEntry(index).type() == ShopEntry.Type.TOOL) {
                return index;
            }
        }
        return context.size();
    }
}
