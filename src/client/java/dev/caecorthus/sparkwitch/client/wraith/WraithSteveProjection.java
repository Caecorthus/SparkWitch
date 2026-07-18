package dev.caecorthus.sparkwitch.client.wraith;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Applies the local Wraith's anonymous wide-Steve view without mutating profiles or server state. / 仅在本地冤魂视角应用宽臂 Steve 匿名显示，不修改档案或服务端状态。 */
public final class WraithSteveProjection {
    private static final Identifier STEVE_TEXTURE =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static final SkinTextures STEVE_SKIN_TEXTURES = new SkinTextures(
            STEVE_TEXTURE,
            null, null, null,
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
        return viewer != null
                && target != null
                && WraithClientState.isActive(viewer)
                && !viewer.getUuid().equals(target.getUuid());
    }

    public static boolean shouldAnonymizeCorpses() {
        return WraithClientState.isActive(MinecraftClient.getInstance().player);
    }
}
