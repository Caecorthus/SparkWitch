package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.headset.CensEarplugsItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32A1Item;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.SmokeRoundItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract (WP0): registers and exposes the four BEAR items. They are registered when this class initialises,
 * which {@link #register()} forces during mod init right after {@code SparkWitchItems} (the data components are
 * registered before both), like {@code SparkWitchDataComponents}. Code that may run before mod init or in unit tests
 * uses {@link BearRules} ids and {@link BearRules#isBearItemType} instead of touching these fields. "BEAR item" means
 * bound to its holder's life only (owner 2026-11-05, the AXMC #199 rule): any holder may carry and use it, it moves
 * under Wathe's own item rules, and it is deleted (never dropped) when its holder dies.
 * 冻结契约（WP0）：注册并公开四种 BEAR 物品。物品在本类初始化时注册，{@link #register()} 在模组初始化期间紧随
 * {@code SparkWitchItems} 强制触发（数据组件在两者之前注册），做法与 {@code SparkWitchDataComponents} 相同。可能早于模组初始化
 * 运行的代码或单元测试改用 {@link BearRules} 中的 id 与 {@link BearRules#isBearItemType}，不访问这些字段。“BEAR 物品”只表示与
 * 持有者的生命绑定（所有者 2026-11-05，沿用 AXMC #199 规则）：任何持有者都能携带和使用，按 Wathe 自身物品规则移动，持有者
 * 死亡时被删除（绝不掉落）。
 */
public final class BearItems {
    /** M32A1 grenade launcher, {@code sparkwitch:m32a1}. / M32A1 榴弹发射器。 */
    public static final Item M32A1 = register(BearRules.M32A1_ITEM_ID, new M32A1Item(M32A1Item.createSettings()));
    /** 40 mm smoke grenade, {@code sparkwitch:smoke_round_40mm}. / 40mm 烟雾榴弹。 */
    public static final Item SMOKE_ROUND = register(BearRules.SMOKE_ROUND_ITEM_ID,
            new SmokeRoundItem(SmokeRoundItem.createSettings()));
    /** Rys-T helmet, {@code sparkwitch:rys_t}. / Rys-T 头盔。 */
    public static final Item RYS_T = register(BearRules.RYS_T_ITEM_ID, new RysTHelmetItem(RysTHelmetItem.createSettings()));
    /** CENS ProFlex DX5 tactical earplugs, {@code sparkwitch:cens_proflex_dx5}. / CENS ProFlex DX5 战术耳塞。 */
    public static final Item EARPLUGS = register(BearRules.EARPLUGS_ITEM_ID,
            new CensEarplugsItem(CensEarplugsItem.createSettings()));

    private BearItems() {
    }

    /** Forces class initialisation during mod init. / 在模组初始化时强制加载本类。 */
    public static void register() {
    }

    /** One of the four BEAR items (by class). / 是否为四种 BEAR 物品之一（按类判断）。 */
    public static boolean isBearItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && BearRules.isBearItemType(stack.getItem().getClass());
    }

    private static Item register(net.minecraft.util.Identifier id, Item item) {
        return Registry.register(Registries.ITEM, id, item);
    }
}
