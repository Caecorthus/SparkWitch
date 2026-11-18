package dev.caecorthus.sparkwitch.item.defense;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.UUID;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Tuning and pure rules of the Defense Vial ({@code sparkwitch:defense_vial}, PR #240). Drinking it (a 2.5 s hold)
 * gives the drinker one NoellesRoles whiskey shield layer and starts a 30 s item cooldown; pouring it into a Wathe
 * food platter / drinks tray gives one layer to the next player who takes a serving from that tray, then the tray is
 * clean again, like Wathe's own poison vial and the pre-2026 NoellesRoles bartender vial.
 * 防御药剂（{@code sparkwitch:defense_vial}，PR #240）的数值与纯规则。饮用（按住 2.5 秒）给饮用者一层 NoellesRoles
 * 威士忌护盾，并进入 30 秒物品冷却；倒进 Wathe 餐盘 / 饮料托盘后，下一个从该托盘取用一份的玩家获得一层护盾，随后托盘恢复
 * 干净，与 Wathe 自己的毒药瓶以及 2026 年前 NoellesRoles 调酒师的药剂一致。
 */
public final class DefenseVialRules {
    /** Registry id; stable, other mods may key on it. / 注册 ID；保持稳定，其他模组可按其引用。 */
    public static final Identifier ITEM_ID = SparkWitch.id("defense_vial");
    /** Hold time of a drink: 2.5 s, also the item's max use time. / 饮用所需按住时间：2.5 秒，也是物品最大使用时长。 */
    public static final int DRINK_TICKS = 50;
    /** Item cooldown after a finished drink: 30 s. / 喝完之后的物品冷却：30 秒。 */
    public static final int COOLDOWN_TICKS = 30 * 20;
    /** Replay {@code action} of a finished drink. / 喝完一瓶的回放 {@code action}。 */
    public static final String ACTION_DRINK = "drink";
    /** Replay {@code action} of a pour into a tray. / 倒入托盘的回放 {@code action}。 */
    public static final String ACTION_POUR = "pour";
    /** Replay {@code action} of the layer a poured vial gave its taker. / 倒入的药剂给取用者护盾的回放 {@code action}。 */
    public static final String ACTION_TRAY_SHIELD = "tray_shield";

    private DefenseVialRules() {
    }

    /**
     * A tray poured during {@code pourMatch} still holds its vial in {@code currentMatch}: same running match only, so
     * a vial never outlives the round it was poured in (Wathe rebuilds the train on the same coordinates each round).
     * 在 {@code pourMatch} 中倒入的托盘，在 {@code currentMatch} 中是否仍有药剂：只认同一场进行中的对局，因此药剂绝不会活过
     * 倒入它的那一局（Wathe 每局都在相同坐标重建列车）。
     */
    public static boolean pourStillLive(@Nullable UUID pourMatch, @Nullable UUID currentMatch) {
        return pourMatch != null && pourMatch.equals(currentMatch);
    }
}
