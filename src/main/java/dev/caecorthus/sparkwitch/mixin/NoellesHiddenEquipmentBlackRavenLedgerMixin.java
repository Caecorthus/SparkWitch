package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenLedgerEquipmentFilter;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeEquipmentFilter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.util.HiddenEquipmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends Noelles' packet seam for SparkWitch's observer-private bound equipment. */
@Mixin(HiddenEquipmentHelper.class)
public abstract class NoellesHiddenEquipmentBlackRavenLedgerMixin {
    @Inject(method = "filterPacket", at = @At("RETURN"), cancellable = true)
    private static void sparkwitch$hideLedgerFromEveryObserver(
            EntityEquipmentUpdateS2CPacket original,
            PlayerEntity owner,
            ServerPlayerEntity observer,
            CallbackInfoReturnable<EntityEquipmentUpdateS2CPacket> cir
    ) {
        EntityEquipmentUpdateS2CPacket filteredByNoelles = cir.getReturnValue();
        EntityEquipmentUpdateS2CPacket filteredLedger = BlackRavenLedgerEquipmentFilter.filter(
                filteredByNoelles == null ? original : filteredByNoelles,
                owner,
                observer
        );
        EntityEquipmentUpdateS2CPacket afterLedger = filteredLedger == null
                ? (filteredByNoelles == null ? original : filteredByNoelles)
                : filteredLedger;
        EntityEquipmentUpdateS2CPacket filteredKnife = VendettaKnifeEquipmentFilter.filter(
                afterLedger,
                owner,
                observer
        );
        if (filteredKnife != null) {
            cir.setReturnValue(filteredKnife);
        } else if (filteredLedger != null) {
            cir.setReturnValue(filteredLedger);
        }
    }
}
