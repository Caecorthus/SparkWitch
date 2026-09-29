package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import java.util.Locale;

/**
 * Every possible catch with its owner-approved weight out of 100. Declaration order is the roll order.
 * 所有可能的渔获及所有者批准的权重（总和 100）。声明顺序即抽取顺序。
 */
public enum FisherCatch {
    SKUNK(10, false),
    SALMON(25, true),
    COD(15, true),
    CLOWNFISH(15, true),
    GOLDFISH(10, true),
    KEY_FISH(10, true),
    PUFFERFISH(5, false),
    SWORDFISH(5, true),
    GLIMMERFISH(5, true);

    private final int weight;
    private final boolean givesItem;

    FisherCatch(int weight, boolean givesItem) {
        this.weight = weight;
        this.givesItem = givesItem;
    }

    public int weight() {
        return weight;
    }

    /** Whether the catch needs inventory room; a skunk or a pufferfish never does. / 是否需要背包空位。 */
    public boolean givesItem() {
        return givesItem;
    }

    /** Lang key of the catch name shown in the action bar. / 动作栏里显示的渔获名称键。 */
    public String translationKey() {
        return "message.sparkwitch.fisher.catch." + name().toLowerCase(Locale.ROOT);
    }
}
