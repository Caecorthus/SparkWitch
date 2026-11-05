package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * The smoke's tileable 3D noise texture ({@link SmokeNoise}, RGBA8, repeat, trilinear mipmaps). The voxels are
 * generated once on a worker thread, started at client init, and uploaded on the render thread by the first frame
 * that needs them; until then {@link #textureId()} is 0 and the smoke renders without erosion. Survives disconnects;
 * released on resource reload.
 * 烟雾的可平铺 3D 噪声纹理（{@link SmokeNoise}，RGBA8，重复寻址，三线性 mipmap）。体素在客户端初始化时于工作线程生成
 * 一次，由第一个需要它的帧在渲染线程上传；在此之前 {@link #textureId()} 为 0，烟雾不做侵蚀。断线时保留，资源重载时释放。
 */
final class SmokeNoiseTexture {
    @Nullable
    private static CompletableFuture<byte[]> voxels;
    private static int texture;
    private static boolean broken;

    private SmokeNoiseTexture() {
    }

    /** Starts generating the voxels off-thread (idempotent). / 在其他线程开始生成体素（幂等）。 */
    static synchronized void prefetch() {
        if (voxels == null) {
            voxels = CompletableFuture.supplyAsync(() -> SmokeNoise.generate(SmokeNoise.SIZE, SmokeNoise.SEED),
                    Util.getMainWorkerExecutor());
        }
    }

    /** GL texture id, or 0 while the voxels are not ready. Render thread. / GL 纹理 id；体素未就绪时为 0。渲染线程。 */
    static int textureId() {
        RenderSystem.assertOnRenderThread();
        if (texture != 0 || broken) {
            return texture;
        }
        prefetch();
        CompletableFuture<byte[]> pending = voxels;
        if (pending == null || !pending.isDone()) {
            return 0;
        }
        byte[] data;
        try {
            data = pending.join();
        } catch (RuntimeException exception) {
            broken = true;
            SparkWitch.LOGGER.error("BEAR smoke noise generation failed; smoke renders without erosion", exception);
            return 0;
        }
        upload(data);
        return texture;
    }

    /** Frees the texture; the voxels are kept for the next upload. / 释放纹理；体素保留供下次上传。 */
    static void release() {
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
    }

    private static void upload(byte[] data) {
        int size = SmokeNoise.SIZE;
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previous3d = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int previousSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int previousSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int previousImageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int previousSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
        try {
            buffer.put(data).flip();
            texture = GlStateManager._genTexture();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, size, size, size, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            GL30.glGenerateMipmap(GL12.GL_TEXTURE_3D);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousSkipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, previousImageHeight);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, previousSkipImages);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, previous3d);
            GL13.glActiveTexture(previousUnit);
            MemoryUtil.memFree(buffer);
        }
    }
}
