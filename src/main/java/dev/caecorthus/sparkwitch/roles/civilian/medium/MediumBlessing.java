package dev.caecorthus.sparkwitch.roles.civilian.medium;

import org.jetbrains.annotations.Nullable;

/**
 * The four blessings the dead choose between for 幽灵的加护. The ordinal is the stable wire index (vote packet, result
 * packet, window disabled mask, replay data), so the order must never change; new entries go at the end.
 * 幽灵的加护中死者可选的四种加护。序数即稳定的线上序号（投票包、结果包、窗口禁用掩码、回放数据），因此顺序绝不能变；
 * 新条目只能追加在末尾。
 */
public enum MediumBlessing {
    /** Speed I for 40 s. / 速度 I，持续 40 秒。 */
    SPEED("speed"),
    /** One NoellesRoles whiskey shield layer for the rest of the round. / 一层 NoellesRoles 威士忌护盾，持续整局。 */
    SHIELD("shield"),
    /** The real corpses existing at the grant stay highlighted. / 获得时已存在的真实尸体保持高亮。 */
    CORPSE_SIGHT("corpse_sight"),
    /** Sanity back to full; disabled for a caster without real sanity. / 理智回满；施放者没有真实理智时禁用。 */
    SANITY("sanity");

    private static final MediumBlessing[] VALUES = values();

    private final String translationKey;

    MediumBlessing(String path) {
        this.translationKey = "blessing.sparkwitch.medium." + path;
    }

    /** Stable wire index. / 稳定的线上序号。 */
    public int index() {
        return ordinal();
    }

    /** This blessing's bit in a disabled mask. / 该加护在禁用掩码中的位。 */
    public int mask() {
        return 1 << ordinal();
    }

    /** Lang key of the blessing's name. / 加护名称的语言键。 */
    public String translationKey() {
        return translationKey;
    }

    public static int count() {
        return VALUES.length;
    }

    /** Null for an unknown index, never a default. / 未知序号返回 null，绝不回退到默认值。 */
    public static @Nullable MediumBlessing byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
