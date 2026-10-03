package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * The four Potion Gunner shells. The serialized key is stored on a loaded launcher and must stay stable.
 * 药炮手的四种炮弹。序列化 key 存在已装填的炮筒上，必须保持稳定。
 */
public enum PotionShellType {
    /** Blindness + Slowness II, non-allies only. / 失明 + 缓慢 II，仅非己方。 */
    DK("dk", "gw_dk_shell", 75, 5, false, 0x4B3B8F),
    /** Extra skill and item cooldown, non-allies only. / 额外技能与物品冷却，仅非己方。 */
    AC("ac", "gw_ac_shell", 125, 7, false, 0x2FB8C9),
    /** Gold deduction, non-allies only. / 扣除金币，仅非己方。 */
    MR("mr", "gw_mr_shell", 150, 3, false, 0xE0B23A),
    /** Kills everyone, the gunner and witch teammates included. / 炸死所有人，含药炮手本人与魔女队友。 */
    TR("tr", "tr_shell", 300, 5, true, 0xD8432F);

    private final String key;
    private final String path;
    private final int price;
    private final int size;
    private final boolean hitsEveryone;
    private final int color;

    PotionShellType(String key, String path, int price, int size, boolean hitsEveryone, int color) {
        this.key = key;
        this.path = path;
        this.price = price;
        this.size = size;
        this.hitsEveryone = hitsEveryone;
        this.color = color;
    }

    /** Stable serialized key. / 稳定的序列化 key。 */
    public String key() {
        return key;
    }

    public Identifier itemId() {
        return SparkWitch.id(path);
    }

    public String shopEntryId() {
        return "sparkwitch_potion_gunner_" + path;
    }

    public int price() {
        return price;
    }

    /** Edge length N of the N×N×N blast cube. / N×N×N 爆炸立方体的边长 N。 */
    public int size() {
        return size;
    }

    public boolean hitsEveryone() {
        return hitsEveryone;
    }

    /** Trail and debris tint. / 烟迹与碎片颜色。 */
    public int color() {
        return color;
    }

    public static Optional<PotionShellType> byKey(String key) {
        for (PotionShellType type : values()) {
            if (type.key.equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
