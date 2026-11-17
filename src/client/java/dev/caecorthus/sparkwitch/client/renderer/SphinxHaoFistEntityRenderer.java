package dev.caecorthus.sparkwitch.client.renderer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.mixin.scope.ScopeGameRendererInvoker;
import dev.caecorthus.sparkwitch.entity.SphinxHaoFistEntity;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

/**
 * Draws the Sphinx Hao spring fist: a 3D boxing fist ({@link ProjectileModels}) whose knuckles travel from the gun's
 * muzzle to the shot's end point ({@link SphinxHaoFistEntity#getTarget}) by the entity's extension, and a neon-yellow
 * scissor lattice (lazy tongs) from the muzzle to the fist's wrist that opens as the fist goes out and folds back as
 * it returns ({@link SphinxHaoLatticeGeometry}). Visual only. Nothing is drawn at extension 0, without a loaded owner,
 * or for an owner the viewer cannot see; the owner always sees their own fist.
 * 绘制狮身豪的弹簧拳头：3D 拳套（{@link ProjectileModels}），指节按实体的伸展量从枪口移动到射击终点
 * （{@link SphinxHaoFistEntity#getTarget}）；以及从枪口到拳头腕部的荧光黄剪刀架（伸缩夹），拳头伸出时张开、收回时折叠
 * （{@link SphinxHaoLatticeGeometry}）。纯表现。伸展量为 0、射手未加载、或观察者看不见射手时什么都不画；射手自己始终能看到。
 */
public final class SphinxHaoFistEntityRenderer extends EntityRenderer<SphinxHaoFistEntity> {
    /**
     * Standalone JSON model (no item points at it): pivot (8, 8, 8), knuckles north; its texture is stitched into the
     * block atlas. / 独立 JSON 模型（没有物品引用）：枢轴 (8, 8, 8)，指节朝北；贴图拼在方块图集中。
     */
    static final Identifier FIST_MODEL_ID = SparkWitch.id("item/projectile/sphinx_hao_fist");
    /**
     * Fist size relative to the model: the held gun shows the same 6x6x5 fist at display scale 0.5 (both views), so
     * the flying fist matches it. Also scales the 3/16 knuckle and wrist offsets. Tune freely.
     * 拳头相对模型的缩放：手持的枪以显示缩放 0.5（两种视角）展示同样 6x6x5 的拳头，因此飞出的拳头与之一致。也缩放 3/16 的指节与
     * 腕部偏移。可自由调整。
     */
    static final float FIST_SCALE = 0.5F;
    /** Slats and rivets reuse swatches of the gun texture (64x64). / 剪刀片与铆钉复用手枪贴图（64x64）上的色块。 */
    private static final Identifier GUN_TEXTURE_ID = SparkWitch.id("item/sphinx_hao_hand_gun");
    private static final RenderLayer LAYER = RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    private static final float TEXTURE_SIZE = 64.0F;
    /**
     * Texel rectangles {u0, v0, u1, v1}, inset half a texel inside the solid swatches (yellow slat u 56..60, rivet
     * u 60..64, both v 60..64) so no edge ever samples a neighbour.
     * 像素矩形 {u0, v0, u1, v1}，在纯色块内缩进半个像素（黄色剪刀片 u 56..60、铆钉 u 60..64，均为 v 60..64），边缘不会采到相邻像素。
     */
    private static final float[] SLAT_TEXELS = {56.5F, 60.5F, 59.5F, 63.5F};
    private static final float[] RIVET_TEXELS = {60.5F, 60.5F, 63.5F, 63.5F};
    /**
     * Light is probed this far back from the knuckles (toward the muzzle), so a fist flush with a wall is not lit as the
     * inside of the block. / 光照取样点从指节向枪口后退这么远，避免贴墙的拳头按方块内部的光照变黑。
     */
    private static final double LIGHT_PROBE_BACKOFF = 0.3D;
    /** 64 blocks like the grappling hook. / 与钩爪一样为 64 格。 */
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;
    /** Covers the lattice height and the first-person muzzle offset. / 覆盖剪刀架高度与第一人称枪口偏移。 */
    private static final double BOX_MARGIN = 1.5D;
    private static final double MIN_RAY_LENGTH = 1.0E-3D;

    public SphinxHaoFistEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    /** Adds the fist model to the model bakery; call once from client init. / 把拳头模型加入模型烘焙；客户端初始化时调用一次。 */
    public static void registerFistModel() {
        ProjectileModels.register(FIST_MODEL_ID);
    }

    @Override
    public void render(
            SphinxHaoFistEntity fist,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        float extension = fist.extension(tickDelta);
        if (!(extension > 0.0F)) {
            return;
        }
        PlayerEntity owner = fist.getOwnerPlayer();
        if (owner == null || hidesOwner(owner)) {
            return;
        }
        // The matrix origin is the entity: the shooter's eye at fire time, never moving.
        // 矩阵原点即实体位置：开火时射手的眼睛，从不移动。
        Vec3d origin = fist.getLerpedPos(tickDelta);
        Vec3d target = fist.getTarget();
        Vec3d anchor = muzzleAnchor(owner, target, tickDelta);
        Vec3d ray = target.subtract(anchor);
        double distance = ray.length();
        if (!(distance > MIN_RAY_LENGTH)) {
            return;
        }
        Vec3d direction = ray.multiply(1.0D / distance);
        double knuckle = distance * extension;
        // Open cells next to the wrist, one partial cell unfolding out of the muzzle (no refolding, no dense band).
        // 靠近腕部的张开单元，以及从枪口展开的一个不完整单元（不重新折叠，没有密集色带）。
        SphinxHaoLatticeGeometry.Lattice lattice = SphinxHaoLatticeGeometry.Lattice.of(
                SphinxHaoLatticeGeometry.span(knuckle, FIST_SCALE));

        World world = fist.getWorld();
        Vec3d lightProbe = anchor.add(direction.multiply(knuckle - Math.min(LIGHT_PROBE_BACKOFF, knuckle)));
        int fistLight = sampleLight(world, lightProbe);
        int anchorLight = brighter(sampleLight(world, anchor), sampleLight(world, owner.getCameraPosVec(tickDelta)));

        matrices.push();
        matrices.translate(anchor.x - origin.x, anchor.y - origin.y, anchor.z - origin.z);
        // One frame for everything: the ray is local -Z, the lattice plane holds +Y, like the fist model's up.
        // 所有部件共用一个坐标系：射线为局部 -Z，剪刀架平面包含 +Y，与拳头模型的上方一致。
        ProjectileModels.faceAlong(matrices, direction.x, direction.y, direction.z);
        VertexConsumer consumer = vertexConsumers.getBuffer(LAYER);
        Sprite sprite = MinecraftClient.getInstance()
                .getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                .apply(GUN_TEXTURE_ID);
        MatrixStack.Entry entry = matrices.peek();
        renderLattice(lattice,
                new BoxVertices(consumer, entry, sprite, SLAT_TEXELS),
                new BoxVertices(consumer, entry, sprite, RIVET_TEXELS),
                anchorLight,
                fistLight);

        matrices.push();
        matrices.translate(0.0D, 0.0D, -SphinxHaoLatticeGeometry.fistPivotDistance(knuckle, FIST_SCALE));
        matrices.scale(FIST_SCALE, FIST_SCALE, FIST_SCALE);
        ProjectileModels.render(ProjectileModels.model(FIST_MODEL_ID), matrices, consumer, fistLight);
        matrices.pop();
        matrices.pop();
        super.render(fist, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * Draw while the muzzle, the fist's path or its end can be in view: the box spanning the owner, the entity (the
     * shooter's eye at fire time) and the target holds the whole assembly.
     * 只要枪口、拳头路径或终点可能在视野内就绘制：覆盖射手、实体（开火时射手的眼睛）与终点的包围盒包含整个装置。
     */
    @Override
    public boolean shouldRender(SphinxHaoFistEntity fist, Frustum frustum, double x, double y, double z) {
        PlayerEntity owner = fist.getOwnerPlayer();
        if (owner == null) {
            return false;
        }
        Vec3d target = fist.getTarget();
        if (fist.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ
                && owner.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ
                && target.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }
        return frustum.isVisible(fist.getBoundingBox()
                .union(owner.getBoundingBox())
                .union(new Box(target, target))
                .expand(BOX_MARGIN));
    }

    /** The fist model and the lattice swatches live in the block atlas. / 拳头模型与剪刀架色块都在方块图集中。 */
    @Override
    public Identifier getTexture(SphinxHaoFistEntity fist) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }

    /**
     * An owner the viewer cannot see (invisibility) gets no fist or lattice, which would point straight at them; the
     * owner always sees their own.
     * 观察者看不见的射手（隐身）不画拳头与剪刀架，否则会直接指向其位置；射手自己始终可见。
     */
    private static boolean hidesOwner(PlayerEntity owner) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        return viewer != null && owner != viewer && owner.isInvisibleTo(viewer);
    }

    /**
     * The muzzle of the gun as drawn. First person (the local owner with the camera on them) places it on screen where
     * the held item's muzzle is, scaled for the world FOV; any other view (third person, a remote camera such as the
     * Seeker's) uses the body's straight-arm hold. A point-blank target pulls it back toward the eye.
     * 所绘手枪的枪口。第一人称（本地射手且镜头在其身上）把它放在手持物品枪口所在的屏幕位置，并按世界视场角缩放；其他视角
     * （第三人称、搜寻者等远程镜头）使用身体的平举持枪姿势。贴脸的终点会把它朝眼睛拉回。
     */
    private Vec3d muzzleAnchor(PlayerEntity owner, Vec3d target, float tickDelta) {
        int side = gunSide(owner);
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = this.dispatcher.camera;
        Vec3d eye;
        Vector3d offset;
        if (this.dispatcher.gameOptions.getPerspective().isFirstPerson()
                && owner == client.player
                && camera.getFocusedEntity() == owner) {
            eye = camera.getPos();
            // Read-only invoker of GameRenderer#getFov: the world projection's FOV this frame (options x speed/zoom).
            // GameRenderer#getFov 的只读调用器：本帧世界投影的视场角（选项 x 速度/缩放）。
            double worldFov = ((ScopeGameRendererInvoker) client.gameRenderer)
                    .sparkwitch$scopeFov(camera, tickDelta, true);
            offset = SphinxHaoLatticeGeometry.firstPersonMuzzleOffset(camera.getHorizontalPlane(),
                    camera.getVerticalPlane(), camera.getDiagonalPlane(), worldFov, side);
        } else {
            eye = owner.getCameraPosVec(tickDelta);
            offset = SphinxHaoLatticeGeometry.thirdPersonMuzzleOffset(
                    MathHelper.lerpAngleDegrees(tickDelta, owner.prevBodyYaw, owner.bodyYaw),
                    MathHelper.lerpAngleDegrees(tickDelta, owner.prevHeadYaw, owner.headYaw),
                    owner.getPitch(tickDelta),
                    side,
                    owner.getScale());
        }
        double pull = SphinxHaoLatticeGeometry.anchorPull(offset.length(), eye.distanceTo(target));
        return eye.add(offset.x * pull, offset.y * pull, offset.z * pull);
    }

    /**
     * +1 for the right arm, -1 for the left: the main arm unless only the offhand holds the gun.
     * 右臂为 +1，左臂为 -1：默认主手一侧，只有副手持枪时取另一侧。
     */
    private static int gunSide(PlayerEntity owner) {
        int side = owner.getMainArm() == Arm.RIGHT ? 1 : -1;
        Item gun = Registries.ITEM.get(SphinxHaoRules.HAND_GUN_ID);
        if (gun != Items.AIR && !owner.getMainHandStack().isOf(gun) && owner.getOffHandStack().isOf(gun)) {
            side = -side;
        }
        return side;
    }

    /**
     * The partial cell, the open cells with their crossing rivets and the pivot rivets, light lerped from the muzzle to
     * the fist. / 不完整单元、张开单元及其交叉铆钉与铰接铆钉，光照从枪口插值到拳头。
     */
    private static void renderLattice(
            SphinxHaoLatticeGeometry.Lattice lattice,
            BoxVertices slats,
            BoxVertices rivets,
            int anchorLight,
            int fistLight
    ) {
        double span = lattice.span();
        slats.light = anchorLight;
        rivets.light = anchorLight;
        lattice.emitPartial(slats, rivets);
        for (int cell = 0; cell < lattice.fullCells(); cell++) {
            int light = lerpLight(fraction(lattice.crossingX(cell), span), anchorLight, fistLight);
            slats.light = light;
            rivets.light = light;
            lattice.emitCell(cell, slats, rivets);
        }
        if (lattice.fullCells() == 0 && !lattice.hasPartial()) {
            return;
        }
        for (int pivot = 0; pivot <= lattice.fullCells(); pivot++) {
            rivets.light = lerpLight(fraction(lattice.pivotX(pivot), span), anchorLight, fistLight);
            lattice.emitPivotRivets(pivot, rivets);
        }
    }

    private static float fraction(double x, double span) {
        return span > 0.0D ? (float) MathHelper.clamp(x / span, 0.0D, 1.0D) : 0.0F;
    }

    private static int sampleLight(World world, Vec3d pos) {
        return WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(pos));
    }

    private static int brighter(int first, int second) {
        return LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getBlockLightCoordinates(first),
                        LightmapTextureManager.getBlockLightCoordinates(second)),
                Math.max(LightmapTextureManager.getSkyLightCoordinates(first),
                        LightmapTextureManager.getSkyLightCoordinates(second))
        );
    }

    private static int lerpLight(float t, int from, int to) {
        int block = (int) MathHelper.lerp(t, LightmapTextureManager.getBlockLightCoordinates(from),
                LightmapTextureManager.getBlockLightCoordinates(to));
        int sky = (int) MathHelper.lerp(t, LightmapTextureManager.getSkyLightCoordinates(from),
                LightmapTextureManager.getSkyLightCoordinates(to));
        return LightmapTextureManager.pack(block, sky);
    }

    /**
     * Writes lattice box corners as entity-cutout vertices: one texel rectangle of the gun sprite per face, white,
     * no overlay, the current light. / 把剪刀架盒体顶点写成 entity-cutout 顶点：每个面取手枪贴图上的一个像素矩形，白色、
     * 无覆盖层、当前光照。
     */
    private static final class BoxVertices implements SphinxHaoLatticeGeometry.BoxSink {
        private final VertexConsumer consumer;
        private final MatrixStack.Entry entry;
        private final float minU;
        private final float minV;
        private final float maxU;
        private final float maxV;
        private int light;

        private BoxVertices(VertexConsumer consumer, MatrixStack.Entry entry, Sprite sprite, float[] texels) {
            this.consumer = consumer;
            this.entry = entry;
            // Sprite#getFrameU/V take 0..1 of the sprite in 1.21.1. / 1.21.1 中 Sprite#getFrameU/V 取 0..1 的贴图比例。
            this.minU = sprite.getFrameU(texels[0] / TEXTURE_SIZE);
            this.minV = sprite.getFrameV(texels[1] / TEXTURE_SIZE);
            this.maxU = sprite.getFrameU(texels[2] / TEXTURE_SIZE);
            this.maxV = sprite.getFrameV(texels[3] / TEXTURE_SIZE);
        }

        @Override
        public void corner(float x, float y, float z, float nx, float ny, float nz, int index) {
            consumer.vertex(entry.getPositionMatrix(), x, y, z)
                    .color(255, 255, 255, 255)
                    .texture(index < 2 ? minU : maxU, index == 0 || index == 3 ? minV : maxV)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry, nx, ny, nz);
        }
    }
}
