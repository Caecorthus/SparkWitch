package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.client.render.WraithSteveProjection;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The look this viewer gets for a puppet (owner decision D6): every player-skin rule this client applies to the copied
 * player, resolved through that player's own entry points instead of copies of their logic. The skin descriptor comes
 * from {@code AbstractClientPlayerEntity#getSkinTextures} on the stand-in (Black Raven perception, Wraith projection,
 * Curser confusion and other mods' HEAD rules), the texture from the stand-in's {@code PlayerEntityRenderer#getTexture}
 * (the terminal Wraith and Black Raven Steve, Wathe psycho, NoellesRoles Jester Moment and Morphling, SparkTraits
 * Depression), and the cape follows {@code WraithCapeFeatureRendererMixin}. A renderer swap that is not a
 * {@code PlayerEntityRenderer} (SparkTraits Pig) or a failing third-party hook falls back to the raw skin, as before D6.
 * 本观察者看到的皮套外观（所有者决定 D6）：本客户端对被复制玩家应用的全部皮肤规则，均通过该玩家自己的入口解析，而非复制逻辑。
 * 皮肤描述取自替身的 {@code AbstractClientPlayerEntity#getSkinTextures}（黑羽鸦感知、冤魂投影、咒术师混乱及其他模组的 HEAD
 * 规则），贴图取自替身的 {@code PlayerEntityRenderer#getTexture}（终端冤魂与黑羽鸦 Steve、Wathe 疯魔、NoellesRoles
 * 小丑时刻与变形者、SparkTraits 抑郁），披风遵循 {@code WraithCapeFeatureRendererMixin}。渲染器被替换为非
 * {@code PlayerEntityRenderer}（SparkTraits 猪）或第三方钩子出错时，回退为 D6 之前的原始皮肤。
 */
public record MagicianPuppetAppearance(Identifier texture, SkinTextures.Model model, @Nullable Identifier cape) {
    public static MagicianPuppetAppearance resolve(MagicianPlaybackEntity puppet) {
        AbstractClientPlayerEntity standIn = MagicianPuppetStandIn.of(puppet);
        if (standIn == null) {
            SkinTextures fallback = DefaultSkinHelper.getSkinTextures(puppet.getUuid());
            return new MagicianPuppetAppearance(fallback.texture(), fallback.model(), null);
        }
        SkinTextures skin = standIn.getSkinTextures();
        return new MagicianPuppetAppearance(playerTexture(standIn, skin), skin.model(), cape(standIn, skin));
    }

    private static Identifier playerTexture(AbstractClientPlayerEntity standIn, SkinTextures skin) {
        try {
            EntityRenderer<? super AbstractClientPlayerEntity> renderer =
                    MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(standIn);
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                Identifier texture = playerRenderer.getTexture(standIn);
                if (texture != null) {
                    return texture;
                }
            }
        } catch (RuntimeException ignored) {
            // A third-party texture hook that cannot handle the stand-in keeps the raw skin.
            // 无法处理替身的第三方贴图钩子保留原始皮肤。
        }
        return skin.texture();
    }

    /** Same veto as {@code WraithCapeFeatureRendererMixin}. / 与 {@code WraithCapeFeatureRendererMixin} 相同的否决。 */
    private static @Nullable Identifier cape(AbstractClientPlayerEntity standIn, SkinTextures skin) {
        if (WraithSteveProjection.shouldAnonymizePlayer(standIn)
                || VendettaClientPresentation.isBoundKillerViewingVendetta(MinecraftClient.getInstance().player,
                standIn)) {
            return null;
        }
        return skin.capeTexture();
    }
}
