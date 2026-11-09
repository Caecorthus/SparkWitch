package dev.caecorthus.sparkwitch.client.mixin.bear;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of the lightmap's CPU image, which {@code LightmapTextureManager#update} rewrites before every world
 * render. The BEAR smoke reads the colour at its clouds' block and sky light from it, so the smoke is lit exactly like
 * the terrain around it (night, gamma, flicker, Darkness, Night Vision) and never glows in the dark. Client-only
 * presentation; never written.
 * 光照图 CPU 图像的只读访问；{@code LightmapTextureManager#update} 在每次世界渲染前重写它。BEAR 烟雾据此读取烟团所在
 * 方块光与天空光对应的颜色，使烟的明暗与周围地形完全一致（夜晚、亮度、闪烁、黑暗、夜视），不会在黑暗中发光。
 * 纯客户端展示；从不写入。
 */
@Mixin(LightmapTextureManager.class)
public interface SmokeLightmapImageAccessor {
    @Accessor("image")
    NativeImage sparkwitch$smokeLightmapImage();
}
