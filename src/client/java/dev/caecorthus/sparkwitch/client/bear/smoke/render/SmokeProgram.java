package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A raw GL program for the smoke passes, compiled from {@code assets/sparkwitch/shaders/bear_smoke/*}. Vanilla's
 * JSON {@code ShaderProgram} cannot bind {@code sampler3D}, so these programs bypass it; {@link SmokeMarchRules}
 * constants are injected as {@code #define}s right after the {@code #version} line. Sources are read from the
 * SparkWitch jar itself, never through the resource manager (like the occlusion passes'
 * {@code occlusion.SmokeGlProgram}), so a client resource pack cannot replace the march or the composite with a no-op
 * and see through smoke, nor break them with a bad override. Uniform locations are cached by name. Render thread only.
 * 烟雾各步骤使用的原生 GL 程序，由 {@code assets/sparkwitch/shaders/bear_smoke/*} 编译。原版 JSON
 * {@code ShaderProgram} 无法绑定 {@code sampler3D}，因此绕开它；{@link SmokeMarchRules} 常量以 {@code #define} 形式
 * 注入到 {@code #version} 行之后。源码直接从 SparkWitch jar 读取，从不经过资源管理器（与遮挡步骤的
 * {@code occlusion.SmokeGlProgram} 相同），因此客户端资源包无法把步进或合成换成空操作来透视烟雾，也无法用错误的覆盖文件
 * 弄坏它们。uniform 位置按名称缓存。仅限渲染线程。
 */
final class SmokeProgram {
    private static final String SOURCE_ROOT = "assets/sparkwitch/shaders/bear_smoke/";
    private final int id;
    private final Map<String, Integer> uniforms = new HashMap<>();

    private SmokeProgram(int id) {
        this.id = id;
    }

    /**
     * Compiles and links {@code vertex} + {@code fragment}; fragment outputs are bound to colour attachments in the
     * order given. Throws {@link IllegalStateException} with the GL info log on failure.
     * 编译并链接 {@code vertex} + {@code fragment}；片元输出按给定顺序绑定到颜色附件。失败时抛出带 GL 日志的
     * {@link IllegalStateException}。
     */
    static SmokeProgram load(String vertex, String fragment, String... outputs) throws IOException {
        return load(vertex, fragment, Map.of(), outputs);
    }

    /**
     * As {@link #load(String, String, String...)} with extra {@code #define}s after the shared ones.
     * 同上，另在共享定义之后加入额外的 {@code #define}。
     */
    static SmokeProgram load(String vertex, String fragment, Map<String, String> extra, String... outputs)
            throws IOException {
        String defines = defineBlock(SmokeMarchRules.shaderDefines()) + defineBlock(extra);
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex, inject(read(vertex), defines));
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragment, inject(read(fragment), defines));
        } catch (RuntimeException exception) {
            GL20.glDeleteShader(vs);
            throw exception;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        for (int i = 0; i < outputs.length; i++) {
            GL30.glBindFragDataLocation(program, i, outputs[i]);
        }
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("BEAR smoke program " + fragment + " failed to link: " + log);
        }
        return new SmokeProgram(program);
    }

    int id() {
        return id;
    }

    int uniform(String name) {
        Integer cached = uniforms.get(name);
        if (cached == null) {
            cached = GL20.glGetUniformLocation(id, name);
            uniforms.put(name, cached);
        }
        return cached;
    }

    /** Binds a sampler uniform to a texture unit; the program must be in use. / 把采样器 uniform 绑到纹理单元；程序须已启用。 */
    void sampler(String name, int unit) {
        GL20.glUniform1i(uniform(name), unit);
    }

    void delete() {
        GL20.glDeleteProgram(id);
    }

    static String defineBlock(Map<String, String> defines) {
        StringBuilder block = new StringBuilder();
        defines.forEach((name, value) -> block.append("#define ").append(name).append(' ').append(value).append('\n'));
        return block.toString();
    }

    /** Inserts {@code defines} after the first ({@code #version}) line. / 在第一行（{@code #version}）之后插入 {@code defines}。 */
    static String inject(String source, String defines) {
        int newline = source.indexOf('\n');
        if (!source.startsWith("#version") || newline < 0) {
            throw new IllegalStateException("smoke shader must start with #version");
        }
        return source.substring(0, newline + 1) + defines + source.substring(newline + 1);
    }

    /**
     * The mod's own copy of a shader (jar, or the dev source-set roots), which no resource pack can override.
     * 模组自带的着色器文件（jar，或开发环境的源集目录），任何资源包都无法覆盖。
     */
    private static String read(String name) throws IOException {
        Path path = FabricLoader.getInstance().getModContainer(SparkWitch.MOD_ID)
                .flatMap(container -> container.findPath(SOURCE_ROOT + name))
                .orElseThrow(() -> new IOException("missing BEAR smoke shader " + name));
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int compile(int type, String name, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("BEAR smoke shader " + name + " failed to compile: " + log);
        }
        return shader;
    }
}
