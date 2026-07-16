package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Hides only the bound ledger from every non-owner equipment observer. */
public final class BlackRavenLedgerEquipmentFilter {
    private BlackRavenLedgerEquipmentFilter() {
    }

    public static @Nullable EntityEquipmentUpdateS2CPacket filter(
            EntityEquipmentUpdateS2CPacket packet,
            PlayerEntity owner,
            ServerPlayerEntity observer
    ) {
        if (packet == null || owner == null || observer == null || owner == observer) {
            return null;
        }
        boolean changed = false;
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>(packet.getEquipmentList().size());
        for (Pair<EquipmentSlot, ItemStack> pair : packet.getEquipmentList()) {
            EquipmentSlot slot = pair.getFirst();
            ItemStack stack = pair.getSecond();
            if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                    && BlackRavenInventoryRules.isLedger(stack)) {
                equipment.add(Pair.of(slot, ItemStack.EMPTY));
                changed = true;
            } else {
                equipment.add(pair);
            }
        }
        return changed ? new EntityEquipmentUpdateS2CPacket(packet.getEntityId(), equipment) : null;
    }
}
