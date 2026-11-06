package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A raw GL program for the smoke occlusion passes. Vanilla's shader programs bind every sampler as
 * {@code GL_TEXTURE_2D}, so they cannot sample the 3D density volumes. Sources are read from the SparkWitch jar itself
 * ({@code assets/sparkwitch/shaders/smoke/}), never through the resource manager, so a resource pack cannot swap the
 * mask for a no-op and see through smoke.
 * 烟雾遮挡步骤用的原生 GL 程序。原版着色器程序把所有采样器都按 {@code GL_TEXTURE_2D} 绑定，无法采样 3D 浓度体。
 * 源码直接从 SparkWitch jar 读取（{@code assets/sparkwitch/shaders/smoke/}），从不经过资源管理器，因此资源包无法把遮罩
 * 换成空操作来透视烟雾。
 */
final class SmokeGlProgram {
    private static final String SOURCE_ROOT = "assets/sparkwitch/shaders/smoke/";
    private static int emptyVertexArray;

    private final int program;
    private final Map<String, Integer> locations = new HashMap<>();

    private SmokeGlProgram(int program) {
        this.program = program;
    }

    /** Compiles and links; throws with the driver log on failure. / 编译并链接；失败时连同驱动日志抛出。 */
    static SmokeGlProgram load(String vertex, String fragment) {
        return load(vertex, fragment, "");
    }

    /**
     * As {@link #load(String, String)} with {@code defines} ({@code #define} lines) inserted after the fragment's
     * {@code #version} line. / 同上，并在片元着色器的 {@code #version} 行之后插入 {@code defines}（{@code #define} 行）。
     */
    static SmokeGlProgram load(String vertex, String fragment, String defines) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex, "");
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragment, defines);
        } catch (RuntimeException exception) {
            GL20.glDeleteShader(vs);
            throw exception;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL30.glBindFragDataLocation(program, 0, "fragColor");
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("smoke occlusion program " + fragment + " failed to link: " + log);
        }
        return new SmokeGlProgram(program);
    }

    void use() {
        GlStateManager._glUseProgram(program);
    }

    void delete() {
        GL20.glDeleteProgram(program);
    }

    void setInt(String name, int value) {
        GL20.glUniform1i(location(name), value);
    }

    void setFloat(String name, float value) {
        GL20.glUniform1f(location(name), value);
    }

    void setFloatArray(String name, float[] values) {
        GL20.glUniform1fv(location(name), values);
    }

    void setVec2(String name, float x, float y) {
        GL20.glUniform2f(location(name), x, y);
    }

    void setVec3Array(String name, float[] values) {
        GL20.glUniform3fv(location(name), values);
    }

    void setMatrix(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20.glUniformMatrix4fv(location(name), false, buffer);
        }
    }

    /** Draws the attribute-less fullscreen triangle of {@code fullscreen.vsh}. / 绘制无属性全屏三角形。 */
    static void drawFullscreenTriangle() {
        if (emptyVertexArray == 0) {
            emptyVertexArray = GlStateManager._glGenVertexArrays();
        }
        GL30.glBindVertexArray(emptyVertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private int location(String name) {
        return locations.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key));
    }

    private static int compile(int type, String file, String defines) {
        String source = read(file);
        if (!defines.isEmpty()) {
            int newline = source.indexOf('\n');
            source = source.substring(0, newline + 1) + defines + source.substring(newline + 1);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("smoke occlusion shader " + file + " failed to compile: " + log);
        }
        return shader;
    }

    private static String read(String file) {
        Path path = FabricLoader.getInstance().getModContainer("sparkwitch")
                .flatMap(container -> container.findPath(SOURCE_ROOT + file))
                .orElseThrow(() -> new IllegalStateException("missing smoke occlusion shader " + file));
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("unreadable smoke occlusion shader " + file, exception);
        }
    }
}
