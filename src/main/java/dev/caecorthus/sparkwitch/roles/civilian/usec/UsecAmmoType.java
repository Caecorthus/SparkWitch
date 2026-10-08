package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.item.Item;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The two .338 round types. {@link #id()} is the stable serialized id stored on rifles and magazines: never rename it.
 * 两种 .338 子弹。{@link #id()} 是存放在步枪与弹匣上的稳定序列化 id：不得改名。
 */
public enum UsecAmmoType {
    /** Full metal jacket: straight, stops at the first block. / 全金属被甲弹：直线飞行，遇到第一个方块即停。 */
    FMJ("fmj", 0xC87533, false),
    /** Armour piercing: penetrates blocks on an energy budget and sinks. / 穿甲弹：按能量预算穿透方块，并会下坠。 */
    AP("ap", 0xD8443A, true);

    private final String id;
    private final int color;
    private final boolean penetrates;

    UsecAmmoType(String id, int color, boolean penetrates) {
        this.id = id;
        this.color = color;
        this.penetrates = penetrates;
    }

    /** Stable serialized id. / 稳定的序列化 id。 */
    public String id() {
        return id;
    }

    /** Label tint in names, tooltips and the HUD. / 名称、提示框与 HUD 中的标签颜色。 */
    public int color() {
        return color;
    }

    public boolean penetrates() {
        return penetrates;
    }

    /**
     * Shield layers this round pierces before one blocks it (O3): FMJ 2, AP 5 ({@link UsecRules}).
     * 本弹种在被某层护盾挡下之前可击穿的护盾层数（O3）：FMJ 2，AP 5（{@link UsecRules}）。
     */
    public int shieldPierce() {
        return switch (this) {
            case FMJ -> UsecRules.FMJ_SHIELD_PIERCE;
            case AP -> UsecRules.AP_SHIELD_PIERCE;
        };
    }

    /** Registry id of this round's item ({@code sparkwitch:usec_338_<id>}). / 本弹种物品的注册 id。 */
    public Identifier itemId() {
        return SparkWitch.id("usec_338_" + id);
    }

    /** Short label lang key ("FMJ" / "AP"). / 短标签的语言键（"FMJ" / "AP"）。 */
    public String labelKey() {
        return "item.sparkwitch.usec_ammo.label." + id;
    }

    /** Short label tinted with {@link #color()}. / 以 {@link #color()} 着色的短标签。 */
    public MutableText label() {
        return Text.translatable(labelKey()).withColor(color);
    }

    /** The registered round item; throws before item registration. / 已注册的子弹物品；物品注册前调用会抛出异常。 */
    public Item item() {
        return SparkWitchItems.usecAmmo(this);
    }

    /** Tolerant lookup: null and unknown ids return null. / 宽容查找：null 与未知 id 返回 null。 */
    public static @Nullable UsecAmmoType fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (UsecAmmoType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
