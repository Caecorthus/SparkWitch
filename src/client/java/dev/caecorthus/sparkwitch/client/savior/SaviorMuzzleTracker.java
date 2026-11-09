package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorParticles;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Records each rendered player's pistol muzzle position and spawns the flash. The matrix conversion is copied from
 * Wathe's {@code MatrixUtils.matrixToVec}; the position map mirrors {@code WatheClient.particleMap} but only holds the
 * Savior pistol. Each position carries the world tick it was rendered on and is used only while fresh, so a shooter
 * who stopped being rendered (culled, out of range) gets the eye fallback instead of a stale barrel; the map is
 * cleared on connection edges by {@code SparkWitchClient.releaseClosedWorldPlayers}.
 * 记录每个被渲染玩家的手枪枪口位置并生成火焰。矩阵换算复制自 Wathe 的 {@code MatrixUtils.matrixToVec}；位置表对应
 * {@code WatheClient.particleMap}，但只记录救世主手枪。每个位置带有其渲染时的世界刻，只在仍新鲜时使用，因此已不再被渲染
 * （被剔除、超出范围）的射手改用眼部回退位置而不是过期的枪管位置；连接边界时由
 * {@code SparkWitchClient.releaseClosedWorldPlayers} 清空该表。
 */
public final class SaviorMuzzleTracker {
    /** Rendered every frame while visible; a few ticks absorbs low frame rates. / 可见时每帧都会渲染；几刻的余量可容纳低帧率。 */
    private static final long MAX_AGE_TICKS = 5L;
    private static final Map<UUID, Muzzle> MUZZLE_POSITIONS = new ConcurrentHashMap<>();

    private record Muzzle(Vec3d position, long renderedAt) {
    }

    private SaviorMuzzleTracker() {
    }

    public static void set(PlayerEntity player, Vec3d position) {
        MUZZLE_POSITIONS.put(player.getUuid(), new Muzzle(position, player.getWorld().getTime()));
    }

    /** Uses and consumes the last rendered muzzle position, like Wathe's muzzle packet. / 使用并消耗最后渲染的枪口位置。 */
    public static Vec3d take(PlayerEntity player) {
        return fresh(player, MUZZLE_POSITIONS.remove(player.getUuid()));
    }

    /** Uses the last rendered position without consuming it (the shooter's local flash). / 不消耗地使用最后渲染位置（射手的本地火焰）。 */
    public static Vec3d peek(PlayerEntity player) {
        return fresh(player, MUZZLE_POSITIONS.get(player.getUuid()));
    }

    /** Connection edges: drops every recorded position. / 连接边界：丢弃所有已记录位置。 */
    public static void clear() {
        MUZZLE_POSITIONS.clear();
    }

    private static Vec3d fresh(PlayerEntity player, Muzzle muzzle) {
        if (muzzle == null) {
            return null;
        }
        long age = player.getWorld().getTime() - muzzle.renderedAt();
        return age >= 0L && age <= MAX_AGE_TICKS ? muzzle.position() : null;
    }

    public static Vec3d fallback(PlayerEntity player) {
        return player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.6D));
    }

    public static void spawnLocalFlash(PlayerEntity player) {
        Vec3d position = peek(player);
        spawn(player.getWorld(), position != null ? position : fallback(player));
    }

    public static void spawnTrackedFlash(PlayerEntity player) {
        Vec3d position = take(player);
        spawn(player.getWorld(), position != null ? position : fallback(player));
    }

    private static void spawn(World world, Vec3d position) {
        world.addParticle(SaviorParticles.GUNSHOT, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
    }

    /** Copied from Wathe: translate to the muzzle offset, then transform the origin into world space. / 复制自 Wathe：平移到枪口偏移，再把原点变换到世界空间。 */
    public static Vec3d matrixToVec(MatrixStack matrices) {
        matrices.translate(0.0F, 0.075F, -0.25F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vector4f origin = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        matrix.transform(origin);
        Vec3d cameraPos = camera.getPos();
        return new Vec3d(cameraPos.x + origin.x(), cameraPos.y + origin.y(), cameraPos.z + origin.z());
    }
}
