package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Sends the knife only to its owner and exact bound killer, never to spectators or ordinary observers. */
public final class VendettaKnifeEquipmentFilter {
    private VendettaKnifeEquipmentFilter() {
    }

    public static @Nullable EntityEquipmentUpdateS2CPacket filter(
            EntityEquipmentUpdateS2CPacket packet,
            PlayerEntity owner,
            ServerPlayerEntity observer
    ) {
        if (packet == null || owner == null || observer == null) {
            return null;
        }
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.maybeGet(owner).orElse(null);
        UUID killerUuid = component == null ? null : component.getBoundKillerUuid();
        boolean boundKillerView = component != null
                && component.isActive()
                && killerUuid != null
                && killerUuid.equals(observer.getUuid());
        if (VendettaPresentationRules.canSeeKnifeEquipment(
                owner == observer,
                boundKillerView,
                observer.isSpectator()
        )) {
            return null;
        }

        boolean changed = false;
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>(packet.getEquipmentList().size());
        for (Pair<EquipmentSlot, ItemStack> pair : packet.getEquipmentList()) {
            EquipmentSlot slot = pair.getFirst();
            ItemStack stack = pair.getSecond();
            if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                    && VendettaKnifeInventoryRules.isKnife(stack)) {
                equipment.add(Pair.of(slot, ItemStack.EMPTY));
                changed = true;
            } else {
                equipment.add(pair);
            }
        }
        return changed ? new EntityEquipmentUpdateS2CPacket(packet.getEntityId(), equipment) : null;
    }
}
