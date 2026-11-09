package dev.caecorthus.sparkwitch.roles.civilian.bear.hearing;

import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.headset.CensEarplugsItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract (BEAR WP0): the hearing volume factor of the gear a player wears (H7, E2). Pure volume, never range
 * (the WP4 seams scale gain only). The two pieces add: Rys-T {@link BearRules#HEARING_HELMET} (-75%), earplugs
 * {@link BearRules#HEARING_EARPLUGS} (+50%); helmet alone 0.25, earplugs alone 1.5, both 0.75, neither 1. Item-based
 * (D1): whoever wears them, any role. Side-neutral; the client applies it to its own player.
 * 冻结契约（BEAR WP0）：玩家所戴装备的听力音量倍率（H7、E2）。只改音量，从不改范围（WP4 的接缝只缩放增益）。两件装备相加：
 * Rys-T {@link BearRules#HEARING_HELMET}（-75%），耳塞 {@link BearRules#HEARING_EARPLUGS}（+50%）；只戴头盔 0.25，只戴耳塞 1.5，
 * 两者都戴 0.75，都不戴 1。按物品判定（D1）：谁戴着就作用于谁，不限职业。两端通用；客户端对自己的玩家应用。
 */
public final class BearHearingRules {
    private BearHearingRules() {
    }

    /** {@code max(0, 1 + HEARING_HELMET·helmet + HEARING_EARPLUGS·earplugs)}. */
    public static float factor(boolean helmet, boolean earplugs) {
        float factor = 1.0F + (helmet ? BearRules.HEARING_HELMET : 0.0F) + (earplugs ? BearRules.HEARING_EARPLUGS : 0.0F);
        return Math.max(0.0F, factor);
    }

    /**
     * The factor for what {@code player} wears now: a Rys-T in the head slot and earplugs in the headset slot; 1 for
     * null. / {@code player} 当前所戴装备的倍率：头部槽中的 Rys-T 与耳机槽中的耳塞；null 时为 1。
     */
    public static float factorFor(@Nullable PlayerEntity player) {
        if (player == null) {
            return 1.0F;
        }
        return factor(wearsHelmet(player.getEquippedStack(EquipmentSlot.HEAD)),
                wearsEarplugs(HeadsetSlots.get(player)));
    }

    static boolean wearsHelmet(@Nullable ItemStack head) {
        return head != null && !head.isEmpty() && head.getItem() instanceof RysTHelmetItem;
    }

    static boolean wearsEarplugs(@Nullable ItemStack headset) {
        return headset != null && !headset.isEmpty() && headset.getItem() instanceof CensEarplugsItem;
    }
}
