package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of Sphinx Hao's shop: one entry, 拳头子弹, {@link SphinxHaoRules#FIST_AMMO_PRICE} hao each, unlimited stock,
 * needing a free hotbar slot. Lang keys of the readable refusals live here.
 * 狮身豪商店的纯规则：唯一条目拳头子弹，每发 {@link SphinxHaoRules#FIST_AMMO_PRICE} 豪度值，库存不限，需要一个空快捷栏位。
 * 可读的拒绝提示语言键位于此处。
 */
public final class SphinxHaoShopRules {
    public static final String NAME_KEY = "shop.sparkwitch.sphinx_hao_fist_ammo";
    public static final String DESCRIPTION_KEY = NAME_KEY + ".description";
    public static final String UNAVAILABLE_KEY = "shop.error.sparkwitch.sphinx_hao.unavailable";
    public static final String HOTBAR_FULL_KEY = "shop.error.sparkwitch.sphinx_hao.hotbar_full";
    public static final String NOT_ENOUGH_HAO_KEY = "shop.error.sparkwitch.sphinx_hao.not_enough_hao";

    private SphinxHaoShopRules() {
    }

    /**
     * The refusal of a fist-ammo purchase, or null when it may go through: a living participant first, then a free
     * hotbar slot, then enough hao.
     * 拳头子弹购买的拒绝原因；可以购买时为 null：先判存活参与者，再判空快捷栏位，最后判豪度值。
     */
    public static @Nullable String fistAmmoDenial(boolean mayBuy, boolean hasRoom, int hao) {
        if (!mayBuy) {
            return UNAVAILABLE_KEY;
        }
        if (!hasRoom) {
            return HOTBAR_FULL_KEY;
        }
        return hao >= SphinxHaoRules.FIST_AMMO_PRICE ? null : NOT_ENOUGH_HAO_KEY;
    }
}
