package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecPlayerComponent;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Where each scoped USEC's objective lens was actually drawn in the current world pass, so the glint rides the rifle
 * model through the walk, sneaking, body yaw and any other mod's pose instead of a point fitted to the head.
 * {@code UsecScopeLensPlayerRenderMixin} opens a holder at {@code PlayerEntityRenderer#render} HEAD for a scoped player
 * (its render matrix and render origin). {@code UsecScopeLensItemRenderMixin} then sees that player's own main-hand
 * rifle drawn in a third-person hand mode, right after the display transform, and records the lens centre relative to
 * the holder's origin. Any outer transform of the pass (an Iris shadow pass) cancels out. {@link #beginPass} clears the
 * map at {@code WorldRenderEvents.START}, so the glint reads only lenses drawn in its own world pass. A player whose
 * rifle was not drawn (culled, held items hidden, another item shown) has no lens and no glint. Presentation only.
 * 每个开镜 USEC 的物镜在本次世界渲染中实际绘制的位置，使反光随步枪模型一起经历走路、潜行、身体偏航以及其他模组的姿势，
 * 而不是按头部拟合的一个点。{@code UsecScopeLensPlayerRenderMixin} 在 {@code PlayerEntityRenderer#render} HEAD 为开镜玩家
 * 打开持有者（其渲染矩阵与渲染原点）；{@code UsecScopeLensItemRenderMixin} 随后在该玩家自己的主手步枪以第三人称手持模式
 * 绘制、紧接显示变换之后，记录物镜中心相对持有者原点的位置。本次渲染的任何外层变换（Iris 阴影渲染）都会抵消。
 * {@link #beginPass} 在 {@code WorldRenderEvents.START} 清空表，因此反光只读取本次世界渲染中绘制的物镜。步枪未被绘制的
 * 玩家（被剔除、手持物被隐藏、显示为其他物品）没有物镜，也没有反光。仅为表现。
 */
public final class UsecScopeLensAnchors {
    private static final Int2ObjectOpenHashMap<Vec3d> LENSES = new Int2ObjectOpenHashMap<>();
    private static final Matrix4f HOLDER_ROOT_INVERSE = new Matrix4f();
    private static final Vector3f LENS_OFFSET = new Vector3f();
    @Nullable
    private static AbstractClientPlayerEntity holder;
    private static double originX;
    private static double originY;
    private static double originZ;

    private UsecScopeLensAnchors() {
    }

    static void beginPass() {
        LENSES.clear();
        holder = null;
    }

    /**
     * {@code PlayerEntityRenderer#render} HEAD: {@code root} is the dispatcher's translation to the render origin
     * (lerped position plus the renderer's position offset), under whatever transform the pass applies outside it.
     * {@code PlayerEntityRenderer#render} HEAD：{@code root} 是分发器平移到渲染原点（插值位置加渲染器的位置偏移）的矩阵，
     * 外面套着本次渲染自己的变换。
     */
    public static void beginPlayer(PlayerEntityRenderer renderer, AbstractClientPlayerEntity player, float tickDelta,
                                   Matrix4fc root) {
        holder = null;
        UsecPlayerComponent component = UsecPlayerComponent.KEY.getNullable(player);
        if (component == null || !component.isScoped() || !(Math.abs(root.determinant3x3()) > 1.0E-12)) {
            return;
        }
        root.invertAffine(HOLDER_ROOT_INVERSE);
        Vec3d offset = renderer.getPositionOffset(player, tickDelta);
        originX = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) + offset.x;
        originY = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + offset.y;
        originZ = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) + offset.z;
        holder = player;
    }

    /** {@code PlayerEntityRenderer#render} RETURN. */
    public static void endPlayer() {
        holder = null;
    }

    /**
     * {@code ItemRenderer#renderItem} right after the display transform and the -0.5 shift. Only the holder's own
     * main-hand stack (the same object, so a stand, display or substituted item never counts) in a third-person hand
     * mode is recorded.
     * {@code ItemRenderer#renderItem} 中紧接显示变换与 -0.5 平移之后。只记录持有者自己的主手物品（同一对象，因此盔甲架、
     * 展示实体或被替换的物品都不算）以第三人称手持模式绘制的情况。
     */
    public static void noteItem(ItemStack stack, ModelTransformationMode mode, Matrix4fc item) {
        AbstractClientPlayerEntity player = holder;
        if (player == null || stack != player.getMainHandStack()
                || mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                && mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || !UsecRifleClient.isRifle(stack)) {
            return;
        }
        UsecGlintRules.lensOffset(HOLDER_ROOT_INVERSE, item, LENS_OFFSET);
        LENSES.put(player.getId(), new Vec3d(originX + LENS_OFFSET.x, originY + LENS_OFFSET.y,
                originZ + LENS_OFFSET.z));
    }

    @Nullable
    static Vec3d lens(AbstractClientPlayerEntity player) {
        return LENSES.get(player.getId());
    }
}
