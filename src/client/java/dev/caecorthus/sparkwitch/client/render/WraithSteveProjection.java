package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Replaces identities seen by a Wraith with the default wide Steve appearance.
 * 将冤魂看到的身份统一替换为默认宽臂 Steve 外观。
 */
public final class WraithSteveProjection {
    private static final Identifier STEVE_TEXTURE =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static final SkinTextures STEVE_SKIN_TEXTURES = new SkinTextures(
            STEVE_TEXTURE,
            null,
            null,
            null,
            SkinTextures.Model.WIDE,
            true
    );

    private WraithSteveProjection() {
    }

    public static Identifier steveTexture() {
        return STEVE_TEXTURE;
    }

    public static SkinTextures steveSkinTextures() {
        return STEVE_SKIN_TEXTURES;
    }

    public static boolean shouldAnonymizePlayer(PlayerEntity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        boolean localWraithProjection = viewer != null
                && target != null
                && WraithClientState.isActive(viewer)
                && !viewer.getUuid().equals(target.getUuid());
        // A true spectator may see the invisible Vendetta, but never the bound killer's real-skin view.
        // 真旁观者可以看见隐身仇杀客，但不能获得绑定凶手专属的真实皮肤视角。
        return localWraithProjection
                || VendettaClientPresentation.shouldProjectSpectatorSteve(viewer, target);
    }

    public static boolean shouldAnonymizeCorpses() {
        return WraithClientState.isActive(MinecraftClient.getInstance().player);
    }
}
