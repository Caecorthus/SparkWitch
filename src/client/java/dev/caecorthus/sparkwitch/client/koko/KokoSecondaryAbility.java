package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.roles.neutral.koko.net.KokoCorpseStreamC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Koko's second key: stream the corpse in front. 可可的第二技能键：直播正前方的尸体。 */
public final class KokoSecondaryAbility implements SecondaryAbilityHandler {
    @Override
    public void onPressed(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new KokoCorpseStreamC2SPayload());
    }
}
