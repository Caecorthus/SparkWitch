package dev.caecorthus.sparkwitch.client.renderer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.entity.NinjaGrapplingHookEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Ninja Grappling Hook: a camera-facing head sprite (like the vanilla fishing bobber) and a steel chain from
 * the head to the owner's hand (vanilla leash strip, thinner, in alternating dark/light links). Taut while LATCHED or
 * PULLING, a slight sag while FLYING. Nothing is drawn without a loaded owner.
 * 绘制忍者钩爪：始终朝向镜头的钩头贴图（同原版浮漂）与从钩头连到主人手上的钢链（原版拴绳条带，更细，深浅链节交替）。
 * 钩住或拉拽时绷直，飞行中略微下垂。主人未加载时什么都不画。
 */
public final class NinjaGrapplingHookEntityRenderer extends EntityRenderer<NinjaGrapplingHookEntity> {
    private static final Identifier TEXTURE = SparkWitch.id("textures/entity/ninja_grappling_hook.png");
    private static final RenderLayer HEAD_LAYER = RenderLayer.getEntityCutout(TEXTURE);
    private static final float HEAD_SIZE = 0.5F;
    /**
     * The chain meets the head at the ring drawn at the bottom of the 16x16 sprite (pixel row ~13.5), in blocks along
     * the camera's up axis. Re-check it if the sprite is redrawn.
     * 链条连接在 16x16 贴图底部的圆环处（约第 13.5 行像素），单位为沿镜头上方向的方块数。重绘贴图时需复核。
     */
    private static final float HEAD_EYE_OFFSET = -0.17F;
    /** A 0.02 wide chain, thinner than the vanilla leash's 0.025. / 链宽 0.02，比原版拴绳的 0.025 更细。 */
    private static final float CHAIN_HALF_WIDTH = 0.01F;
    /**
     * Light is probed this far toward the owner, so a hook flush with a wall is not lit as the inside of the block.
     * 光照取样点向主人方向偏移这么远，避免贴墙的钩头按方块内部的光照变黑。
     */
    private static final double LIGHT_PROBE_BACKOFF = 0.3D;
    /**
     * 64 blocks like the vanilla bobber; {@code Entity#shouldRender} would cut a 0.25 box at 16 blocks.
     * 与原版浮漂一样为 64 格；{@code Entity#shouldRender} 会让 0.25 的碰撞箱在 16 格外就不再绘制。
     */
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;
    /** Covers the sag and the first-person hand offset. / 覆盖下垂与第一人称手部偏移。 */
    private static final double CHAIN_BOX_MARGIN = 1.0D;
    private static final int HEAD_HIDDEN_AGE = 2;
    private static final double HEAD_HIDDEN_DISTANCE_SQ = 3.5D * 3.5D;
    private static final float[] DARK_STEEL = {0.29F, 0.30F, 0.33F};
    private static final float[] LIGHT_STEEL = {0.63F, 0.65F, 0.69F};

    public NinjaGrapplingHookEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(
            NinjaGrapplingHookEntity hook,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        PlayerEntity owner = hook.getOwnerPlayer();
        if (owner == null || hidesOwner(owner)) {
            return;
        }
        float halfHeight = hook.getHeight() / 2.0F;
        Vec3d center = hook.getLerpedPos(tickDelta).add(0.0D, halfHeight, 0.0D);
        Vec3d hand = handPos(owner, tickDelta);
        World world = hook.getWorld();
        Vec3d lightProbe = center.add(hand.subtract(center).normalize().multiply(LIGHT_PROBE_BACKOFF));
        int hookLight = brighter(light, sampleLight(world, lightProbe));
        int handLight = sampleLight(world, owner.getCameraPosVec(tickDelta));
        Quaternionf facing = this.dispatcher.getRotation();

        matrices.push();
        matrices.translate(0.0D, halfHeight, 0.0D);
        // Like vanilla thrown items: skip the head for its first ticks right in front of the camera, so the sprite does
        // not flash across the thrower's view. / 同原版投掷物：刚出手且紧贴镜头时不画钩头，避免贴图闪过投掷者视野。
        if (hook.age >= HEAD_HIDDEN_AGE
                || this.dispatcher.camera.getPos().squaredDistanceTo(center) >= HEAD_HIDDEN_DISTANCE_SQ) {
            renderHead(matrices, vertexConsumers, facing, hookLight);
        }
        Vector3f eye = facing.transform(new Vector3f(0.0F, HEAD_EYE_OFFSET, 0.0F));
        ChainStrip strip = new ChainStrip(
                vertexConsumers.getBuffer(RenderLayer.getLeash()),
                matrices.peek().getPositionMatrix(),
                eye,
                (float) (hand.x - center.x) - eye.x,
                (float) (hand.y - center.y) - eye.y,
                (float) (hand.z - center.z) - eye.z,
                hookLight,
                handLight
        );
        strip.render(hook.getState() == NinjaGrapplingHookEntity.State.FLYING);
        matrices.pop();
        super.render(hook, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * Draw while either end or the chain between them can be in view: the head alone may be off-screen while the
     * chain crosses the screen. The box spanning both ends contains the whole chain.
     * 只要任一端或其间的链条可能在视野内就绘制：钩头本身可能在屏幕外而链条横穿画面。覆盖两端的包围盒包含整条链。
     */
    @Override
    public boolean shouldRender(NinjaGrapplingHookEntity hook, Frustum frustum, double x, double y, double z) {
        PlayerEntity owner = hook.getOwnerPlayer();
        if (owner == null) {
            return false;
        }
        if (hook.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ
                && owner.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }
        return frustum.isVisible(hook.getBoundingBox().union(owner.getBoundingBox()).expand(CHAIN_BOX_MARGIN));
    }

    /**
     * An owner the viewer cannot see (invisibility) gets no hook or chain, which would point straight at them; the
     * owner always sees their own.
     * 观察者看不见的持有者（隐身）不画钩爪与铁链，否则会直接指向其位置；持有者自己始终可见。
     */
    private static boolean hidesOwner(PlayerEntity owner) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        return viewer != null && owner != viewer && owner.isInvisibleTo(viewer);
    }

    @Override
    public Identifier getTexture(NinjaGrapplingHookEntity hook) {
        return TEXTURE;
    }

    private static void renderHead(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Quaternionf facing,
            int light
    ) {
        matrices.push();
        matrices.scale(HEAD_SIZE, HEAD_SIZE, HEAD_SIZE);
        matrices.multiply(facing);
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer consumer = vertexConsumers.getBuffer(HEAD_LAYER);
        headVertex(consumer, entry, light, 0.0F, 0.0F, 0.0F, 1.0F);
        headVertex(consumer, entry, light, 1.0F, 0.0F, 1.0F, 1.0F);
        headVertex(consumer, entry, light, 1.0F, 1.0F, 1.0F, 0.0F);
        headVertex(consumer, entry, light, 0.0F, 1.0F, 0.0F, 0.0F);
        matrices.pop();
    }

    private static void headVertex(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            int light,
            float x,
            float y,
            float u,
            float v
    ) {
        consumer.vertex(entry, x - 0.5F, y - 0.5F, 0.0F)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Port of vanilla {@code FishingBobberEntityRenderer#getHandPos} for the hand holding the grappling hook (the
     * other hand when the main hand does not hold it). First person also requires the camera to sit on the owner, so a
     * remote camera (Seeker) falls back to the drawn body's hand.
     * 移植自原版 {@code FishingBobberEntityRenderer#getHandPos}，锚定在持有钩爪的那只手（主手未持有时取另一只手）。
     * 第一人称还要求镜头位于主人身上，因此远程镜头（搜寻者）会退回到被绘制身体的手部位置。
     */
    private Vec3d handPos(PlayerEntity owner, float tickDelta) {
        float swing = MathHelper.sin(MathHelper.sqrt(owner.getHandSwingProgress(tickDelta)) * (float) Math.PI);
        int side = owner.getMainArm() == Arm.RIGHT ? 1 : -1;
        if (!owner.getMainHandStack().isOf(SparkWitchItems.ninjaGrapplingHook())) {
            side = -side;
        }
        if (this.dispatcher.gameOptions.getPerspective().isFirstPerson()
                && owner == MinecraftClient.getInstance().player
                && this.dispatcher.camera.getFocusedEntity() == owner) {
            double fovScale = 960.0D / this.dispatcher.gameOptions.getFov().getValue().intValue();
            Vec3d offset = this.dispatcher.camera.getProjection()
                    .getPosition(side * 0.525F, -0.1F)
                    .multiply(fovScale)
                    .rotateY(swing * 0.5F)
                    .rotateX(-swing * 0.7F);
            return owner.getCameraPosVec(tickDelta).add(offset);
        }
        float bodyYaw = MathHelper.lerp(tickDelta, owner.prevBodyYaw, owner.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        double sin = MathHelper.sin(bodyYaw);
        double cos = MathHelper.cos(bodyYaw);
        float scale = owner.getScale();
        double sideways = side * 0.35D * scale;
        double forward = 0.8D * scale;
        float sneak = owner.isInSneakingPose() ? -0.1875F : 0.0F;
        return owner.getCameraPosVec(tickDelta).add(
                -cos * sideways - sin * forward,
                sneak - 0.45D * scale,
                -sin * sideways + cos * forward
        );
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
     * One {@code RenderLayer.getLeash()} triangle strip, like vanilla's leash: out along one ribbon, back along a second
     * ribbon at right angles. Each link writes its own start and end pair, so shades change in hard steps (chain links)
     * instead of blending, and the two ribbons are out of phase like links turned 90 degrees.
     * 一条 {@code RenderLayer.getLeash()} 三角形条带，同原版拴绳：沿一条带状面出去，再沿垂直的第二条带状面返回。
     * 每个链节都写入自己的起止顶点对，因此明暗是硬切换（链节）而非渐变；两条带状面相位相反，像相互转 90 度的链环。
     */
    private record ChainStrip(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vector3f start,
            float dx,
            float dy,
            float dz,
            int hookLight,
            int handLight
    ) {
        void render(boolean slack) {
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(length > 1.0E-3F)) {
                return;
            }
            int links = NinjaGrapplingChainGeometry.linkCount(length);
            float sag = NinjaGrapplingChainGeometry.sagDepth(length, slack);
            Vector3f side = NinjaGrapplingChainGeometry.sideAxis(dx, dy, dz, CHAIN_HALF_WIDTH);
            Vector3f cross = NinjaGrapplingChainGeometry.crossAxis(dx, dy, dz, side, CHAIN_HALF_WIDTH);
            for (int link = 0; link < links; link++) {
                boolean bright = (link & 1) == 0;
                pair((float) link / links, sag, side, bright);
                pair((float) (link + 1) / links, sag, side, bright);
            }
            for (int link = links - 1; link >= 0; link--) {
                boolean bright = (link & 1) == 1;
                pair((float) (link + 1) / links, sag, cross, bright);
                pair((float) link / links, sag, cross, bright);
            }
        }

        private void pair(float t, float sag, Vector3f axis, boolean bright) {
            float x = start.x + dx * t;
            float y = start.y + dy * t + NinjaGrapplingChainGeometry.sagOffset(t, sag);
            float z = start.z + dz * t;
            float[] color = bright ? LIGHT_STEEL : DARK_STEEL;
            int light = lerpLight(t, hookLight, handLight);
            consumer.vertex(matrix, x - axis.x, y - axis.y, z - axis.z)
                    .color(color[0], color[1], color[2], 1.0F)
                    .light(light);
            consumer.vertex(matrix, x + axis.x, y + axis.y, z + axis.z)
                    .color(color[0], color[1], color[2], 1.0F)
                    .light(light);
        }
    }
}
