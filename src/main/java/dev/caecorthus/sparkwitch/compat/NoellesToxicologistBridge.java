package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.item.AntidoteItem;

/**
 * Isolates Witch Maiden's exact Toxicologist antidote contract behind the NoellesRoles boundary.
 * 将巫女对毒理学家解毒剂的精确兼容契约隔离在 NoellesRoles 边界之后。
 */
public final class NoellesToxicologistBridge {
    private NoellesToxicologistBridge() {
    }

    public static boolean isExactToxicologist(Role role) {
        return role == Noellesroles.TOXICOLOGIST;
    }

    public static boolean holdsAntidote(PlayerEntity player) {
        return player != null && player.getMainHandStack().isOf(ModItems.ANTIDOTE);
    }

    public static boolean isAntidoteCoolingDown(PlayerEntity player) {
        return player != null && player.getItemCooldownManager().isCoolingDown(ModItems.ANTIDOTE);
    }

    public static void startPlateAntidoteCooldown(PlayerEntity player) {
        player.getItemCooldownManager().set(ModItems.ANTIDOTE, AntidoteItem.COOLDOWN_TICKS);
    }

    public static Identifier antidoteItemId() {
        return Registries.ITEM.getId(ModItems.ANTIDOTE);
    }
}
