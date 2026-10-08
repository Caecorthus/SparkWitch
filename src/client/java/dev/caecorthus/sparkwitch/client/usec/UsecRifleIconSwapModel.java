package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client only. Wraps one USEC rifle model with its 2-D inventory icon (see {@link UsecRifleModels}). Fail-safe: if the
 * icon is missing, is not an item/generated sprite, or cannot be baked, baking returns the plain 3-D model, which keeps
 * its own GUI/ground/fixed display entries as the fallback look. The wrapped 3-D models are baked directly (no
 * item/generated branch), so they must stay element models, and no model may use a wrapped id as its {@code parent}
 * (vanilla only accepts JSON parents).
 * 仅客户端。为一个 USEC 狙击步枪模型包上它的 2D 背包图标（见 {@link UsecRifleModels}）。失败安全：图标缺失、不是
 * item/generated 图标或无法烘焙时，直接返回原 3D 模型，其自带的背包/掉落物/展示框显示变换作为后备外观。被包装的 3D 模型直接
 * 烘焙（不走 item/generated 分支），因此必须保持为元素模型；任何模型都不得以被包装的 id 作为 {@code parent}（原版只接受
 * JSON 父模型）。
 */
final class UsecRifleIconSwapModel implements UnbakedModel {
    private final UnbakedModel held;
    private final Identifier iconId;

    UsecRifleIconSwapModel(UnbakedModel held, Identifier iconId) {
        this.held = held;
        this.iconId = iconId;
    }

    @Override
    public Collection<Identifier> getModelDependencies() {
        Set<Identifier> dependencies = new LinkedHashSet<>(held.getModelDependencies());
        dependencies.add(iconId);
        return dependencies;
    }

    @Override
    public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
        held.setParents(modelLoader);
        modelLoader.apply(iconId).setParents(modelLoader);
    }

    @Override
    public @Nullable BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter,
                                     ModelBakeSettings settings) {
        BakedModel heldModel = held.bake(baker, textureGetter, settings);
        if (heldModel == null) {
            return null;
        }
        try {
            // The missing model is its own root, so this also catches an absent icon JSON.
            // 缺失模型的根模型就是它自己，因此这里同样能发现缺失的图标 JSON。
            if (!(baker.getOrLoadModel(iconId) instanceof JsonUnbakedModel icon)
                    || icon.getRootModel() != ModelLoader.GENERATION_MARKER) {
                SparkWitch.LOGGER.warn("USEC rifle icon model {} is missing or not item/generated; showing the 3-D "
                        + "model everywhere", iconId);
                return heldModel;
            }
            BakedModel iconModel = baker.bake(iconId, settings);
            return iconModel == null ? heldModel : new Baked(heldModel, iconModel);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("USEC rifle icon model {} failed to bake; showing the 3-D model everywhere",
                    iconId, exception);
            return heldModel;
        }
    }

    /**
     * The baked pair. ItemRenderer applies {@link #getTransformation()} before it asks for quads, so the merged
     * transformation must take GUI/ground/fixed from the icon and every held context from the 3-D model, matching
     * {@link #showsIcon}. {@link #isSideLit()} (GUI lighting) and {@link #hasDepth()} (GUI depth, ground spread) are
     * only read in icon contexts, so they come from the icon too, as does the particle sprite (vanilla's trident
     * also cracks into its flat item texture).
     * 烘焙后的组合。ItemRenderer 先应用 {@link #getTransformation()} 再取四边形，因此合并后的变换中背包/掉落物/展示框取自图标，
     * 所有手持上下文取自 3D 模型，与 {@link #showsIcon} 一致。{@link #isSideLit()}（背包光照）与 {@link #hasDepth()}
     * （背包深度、掉落物散布）只在图标上下文中读取，因此同样取自图标；粒子贴图也取自图标（原版三叉戟的碎裂粒子同样使用平面
     * 物品贴图）。
     */
    static final class Baked implements BakedModel, FabricBakedModel {
        private final BakedModel held;
        private final BakedModel icon;
        private final ModelTransformation transformation;

        Baked(BakedModel held, BakedModel icon) {
            this.held = held;
            this.icon = icon;
            ModelTransformation hand = held.getTransformation();
            ModelTransformation flat = icon.getTransformation();
            this.transformation = new ModelTransformation(hand.thirdPersonLeftHand, hand.thirdPersonRightHand,
                    hand.firstPersonLeftHand, hand.firstPersonRightHand, hand.head, flat.gui, flat.ground, flat.fixed);
        }

        /** Vanilla's own trident/spyglass icon contexts. / 原版三叉戟/望远镜使用图标的上下文。 */
        static boolean showsIcon(ModelTransformationMode mode) {
            return mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.GROUND
                    || mode == ModelTransformationMode.FIXED;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
            (showsIcon(context.itemTransformationMode()) ? icon : held).emitItemQuads(stack, randomSupplier, context);
        }

        @Override
        public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                                   Supplier<Random> randomSupplier, RenderContext context) {
            held.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
            return held.getQuads(state, face, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return held.useAmbientOcclusion();
        }

        @Override
        public boolean hasDepth() {
            return icon.hasDepth();
        }

        @Override
        public boolean isSideLit() {
            return icon.isSideLit();
        }

        @Override
        public boolean isBuiltin() {
            return false;
        }

        @Override
        public Sprite getParticleSprite() {
            return icon.getParticleSprite();
        }

        @Override
        public ModelTransformation getTransformation() {
            return transformation;
        }

        @Override
        public ModelOverrideList getOverrides() {
            return held.getOverrides();
        }
    }
}
