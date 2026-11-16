package dev.caecorthus.sparkwitch.client.render.model;

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
 * Client only. Shared by the USEC rifle ({@code client.usec.UsecRifleModels}) and the M32A1
 * ({@code client.bear.launcher.M32LauncherClient}): wraps one held 3-D item model with its 2-D inventory icon for GUI,
 * ground and item frames, the vanilla trident/spyglass split. Fail-safe: if the icon is missing, is not an
 * item/generated sprite, or cannot be baked, baking returns the plain 3-D model, which keeps its own GUI/ground/fixed
 * display entries as the fallback look; {@code diagnosticName} only labels that warning. The wrapped 3-D models are
 * baked directly (no item/generated branch), so they must stay element models, and no model may use a wrapped id as
 * its {@code parent} (vanilla only accepts JSON parents). Each caller skips a model that is already an
 * {@code InventoryIconSwapModel} (never double-wraps); the USEC and M32A1 model ids are disjoint, so neither caller
 * ever sees the other's wrapper.
 * 仅客户端。由 USEC 狙击步枪（{@code client.usec.UsecRifleModels}）与 M32A1（{@code client.bear.launcher.M32LauncherClient}）
 * 共用：为一个手持 3D 物品模型包上它的 2D 背包图标，用于背包、掉落物与物品展示框，即原版三叉戟/望远镜的分流。失败安全：
 * 图标缺失、不是 item/generated 图标或无法烘焙时，直接返回原 3D 模型，其自带的背包/掉落物/展示框显示变换作为后备外观；
 * {@code diagnosticName} 只用于标注该警告。被包装的 3D 模型直接烘焙（不走 item/generated 分支），因此必须保持为元素模型；
 * 任何模型都不得以被包装的 id 作为 {@code parent}（原版只接受 JSON 父模型）。各调用方会跳过已是
 * {@code InventoryIconSwapModel} 的模型（从不重复包装）；USEC 与 M32A1 的模型 id 互不重叠，因此任一调用方都不会遇到另一方的包装。
 */
public final class InventoryIconSwapModel implements UnbakedModel {
    private final UnbakedModel held;
    private final Identifier iconId;
    private final String diagnosticName;

    public InventoryIconSwapModel(UnbakedModel held, Identifier iconId, String diagnosticName) {
        this.held = held;
        this.iconId = iconId;
        this.diagnosticName = diagnosticName;
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
                SparkWitch.LOGGER.warn("{} icon model {} is missing or not item/generated; showing the 3-D "
                        + "model everywhere", diagnosticName, iconId);
                return heldModel;
            }
            BakedModel iconModel = baker.bake(iconId, settings);
            return iconModel == null ? heldModel : new Baked(heldModel, iconModel);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("{} icon model {} failed to bake; showing the 3-D model everywhere",
                    diagnosticName, iconId, exception);
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
