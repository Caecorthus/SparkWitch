package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkManager;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the Seeker's own body out of chunks this client does not have (unlimited range, owner decision 2026-10-04).
 * While a session lives the server centres the owner's chunk view on the device, so the chunks under a far body are
 * unloaded here; the body still ticks and, being kept {@code isCamera()} by {@code SeekerRemoteLocalPlayerMixin}, still
 * sends movement packets. 1.21.1's {@code ClientWorld.isChunkLoaded} is always true, so vanilla would let it free-fall
 * through the empty chunk and report that to the server (which compares the body against its anchor). Real presence
 * is read from the {@code ClientChunkManager} instead. {@code mixin/seeker/SeekerBodyFreezeMixin} cancels the body's
 * own SELF moves while {@link #freezes} holds: during the view only while the hitbox chunks are missing; after the view
 * (the camera returns before the server re-streams the body's chunks) until the 3x3 chunks around it are back, capped
 * at {@link SeekerRemoteViewRules#BODY_SETTLE_MAX_TICKS}. That post-view wait is covered by a RETURNING panel so the
 * owner never stares into the empty world. Client-only presentation and prediction; the server stays authoritative.
 * 让搜寻者自己的本体远离本客户端尚未拥有的区块（无限距离，所有者决定 2026-10-04）。会话期间服务端把拥有者的区块视野以设备
 * 为中心，因此远处本体下方的区块会在本客户端卸载；本体仍会 tick，且由于 {@code SeekerRemoteLocalPlayerMixin} 让其
 * {@code isCamera()} 保持为真，仍会发送移动包。1.21.1 的 {@code ClientWorld.isChunkLoaded} 恒为 true，原版会让本体穿过空区块
 * 自由下落并上报给服务端（服务端会把本体与锚点比较）。因此改从 {@code ClientChunkManager} 读取真实的加载状态。
 * {@link #freezes} 成立时，{@code mixin/seeker/SeekerBodyFreezeMixin} 取消本体自身的 SELF 移动：观看期间仅在碰撞箱所在区块
 * 缺失时；观看结束后（相机先于服务端重新推送本体区块回到本体）保持到周围 3x3 区块送达，上限为
 * {@link SeekerRemoteViewRules#BODY_SETTLE_MAX_TICKS}。这段等待由“正在返回本体”面板遮住，拥有者不会看到空白世界。
 * 仅为客户端展示与预测；服务端始终权威。
 */
public final class SeekerBodyHold {
    /** Horizontal reach of the "surroundings" probe: the body's chunk and its eight neighbours. / 周围探测范围：本体区块及其八个相邻区块。 */
    private static final double SURROUNDINGS = 16.0;
    private static boolean registered;
    @Nullable
    private static ClientPlayerEntity settleBody;
    private static int settleTicks;

    private SeekerBodyHold() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register(SeekerBodyHold::renderReturning);
    }

    /**
     * {@code SeekerBodyFreezeMixin} hook (client main thread): true while the local body's own moves must not run.
     * Only the session's own body instance is held; a respawned player is a new instance.
     * {@code SeekerBodyFreezeMixin} 钩子（客户端主线程）：本地本体自身移动不得执行时为 true。只保持会话自己的本体实例；
     * 重生后的玩家是新实例。
     */
    public static boolean freezes(ClientPlayerEntity player) {
        if (player != MinecraftClient.getInstance().player) {
            return false;
        }
        boolean viewing = SeekerRemoteViewClient.isPossessingBody(player);
        boolean settling = isSettling(player);
        if (!viewing && !settling) {
            return false;
        }
        return SeekerRemoteViewRules.holdsBody(viewing, settling, player.isSpectator(),
                viewing && chunksLoaded(player.getWorld(), player.getBoundingBox()),
                !viewing && surroundingsLoaded(player));
    }

    /**
     * Every chunk a box overlaps is really present in the world's chunk manager (never {@code World.isChunkLoaded},
     * which the client world always answers true). Also used by the car driver.
     * 方框覆盖的每个区块都真实存在于世界的区块管理器中（绝不使用客户端世界恒为 true 的 {@code World.isChunkLoaded}）。
     * 小车驾驶器同样使用。
     */
    static boolean chunksLoaded(World world, Box box) {
        ChunkManager chunks = world.getChunkManager();
        int minX = ChunkSectionPos.getSectionCoord(box.minX);
        int maxX = ChunkSectionPos.getSectionCoord(box.maxX);
        int minZ = ChunkSectionPos.getSectionCoord(box.minZ);
        int maxZ = ChunkSectionPos.getSectionCoord(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!chunks.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** End edge of a view: hold the body until its surroundings are back. / 视角结束沿：保持本体直到其周围区块送达。 */
    static void beginSettle(@Nullable ClientPlayerEntity body) {
        settleBody = body;
        settleTicks = body == null ? 0 : SeekerRemoteViewRules.BODY_SETTLE_MAX_TICKS;
    }

    /** A new view takes over (it holds the body itself while viewing). / 新视角接管（观看期间由其自行保持本体）。 */
    static void reset() {
        settleBody = null;
        settleTicks = 0;
    }

    /** START_CLIENT_TICK, before the body's movement tick. / START_CLIENT_TICK，早于本体的移动刻。 */
    static void tick(MinecraftClient client) {
        if (settleTicks <= 0) {
            return;
        }
        ClientPlayerEntity body = client.player;
        boolean sameBody = body != null && body == settleBody;
        if (SeekerRemoteViewRules.settleEnds(sameBody, --settleTicks, sameBody && surroundingsLoaded(body))) {
            reset();
        }
    }

    private static boolean isSettling(ClientPlayerEntity player) {
        return settleTicks > 0 && player == settleBody;
    }

    /**
     * The entity's chunk and its eight neighbours are present (the body after a view, the focus before it is revealed).
     * 实体所在区块及其八个相邻区块均已存在（观看结束后的本体、揭示之前的焦点）。
     */
    static boolean surroundingsLoaded(Entity entity) {
        return chunksLoaded(entity.getWorld(), entity.getBoundingBox().expand(SURROUNDINGS, 0.0, SURROUNDINGS));
    }

    /**
     * RETURNING panel while the settled body is held (live check, so it never outlasts the chunks). Like the SIGNAL
     * LOST mask it ignores F1: it hides an empty world, not a decoration.
     * 本体处于稳定期保持时显示“正在返回本体”面板（实时判断，绝不比区块到达更久）。与“信号丢失”遮罩一样不受 F1 影响：
     * 它遮挡的是空白世界，而不是装饰元素。
     */
    private static void renderReturning(DrawContext context, RenderTickCounter tickCounter) {
        if (settleTicks <= 0 || SeekerRemoteViewClient.isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !SparkWitchServerConnection.isConfirmedServer()
                || !freezes(player)) {
            return;
        }
        SeekerCctvOverlay.renderPanel(context, client.textRenderer, player.age,
                Text.translatable("hud.sparkwitch.seeker.view.returning"), SeekerCctvRules.CAMERA_FRAME_COLOR, null,
                false);
    }
}
