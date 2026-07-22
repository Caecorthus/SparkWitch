package dev.caecorthus.sparkwitch.client.curser;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.curser.UseCurserAbilityC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Client sender for the shared ability key; authority stays in CurserFeatureService. / 共用技能键的客户端发送端；权威仍在 CurserFeatureService。 */
public final class CurserClientHooks {
    private CurserClientHooks() {
    }

    public static boolean canUse(ClientPlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && CurserPlayerComponent.KEY.get(player).getCooldownTicks() <= 0;
    }

    public static void use() {
        if (ClientPlayNetworking.canSend(UseCurserAbilityC2SPacket.ID)) {
            ClientPlayNetworking.send(new UseCurserAbilityC2SPacket());
        }
    }

    public static boolean isLocallyConfused() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && CurserPlayerComponent.KEY.get(player).isConfused();
    }
}
