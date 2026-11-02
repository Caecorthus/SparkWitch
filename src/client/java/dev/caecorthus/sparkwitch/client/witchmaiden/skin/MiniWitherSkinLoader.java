package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import com.google.gson.JsonElement;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;
import dev.caecorthus.sparkwitch.client.bedrock.anim.BedrockAnimation;
import dev.caecorthus.sparkwitch.client.bedrock.anim.BedrockAnimationParser;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockGeometry;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockGeometryParser;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockModelRenderer;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinFolder.InvalidSkinException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a resolved {@link MiniWitherSkinSpec} into a drawable {@link MiniWitherSkin}: parses geometry and animations,
 * binds states and the aim bone, measures the bind-pose bounds, then decodes and registers the textures as
 * {@code sparkwitch:dynamic/mini_wither_skin/<id>/<generation>/<name>}. Render thread only (texture upload). Everything
 * is parsed and decoded before the first texture is registered, and a failure frees what it made.
 * 把已解析的 {@link MiniWitherSkinSpec} 变成可绘制的 {@link MiniWitherSkin}：解析几何与动画，绑定状态与瞄准骨骼，计算绑定
 * 姿态包围盒，然后解码并注册贴图为 {@code sparkwitch:dynamic/mini_wither_skin/<id>/<generation>/<name>}。仅限渲染线程（需上传
 * 贴图）。注册第一张贴图前已完成全部解析与解码，失败时释放已创建的资源。
 */
final class MiniWitherSkinLoader {
    private MiniWitherSkinLoader() {
    }

    /** A loaded skin with the textures it owns. / 已加载的皮肤及其拥有的贴图。 */
    record Loaded(MiniWitherSkin skin, List<Identifier> textures, List<String> warnings) {
    }

    /** A model parsed but not yet given textures. / 已解析但尚未分配贴图的模型。 */
    private record Parsed(MiniWitherSkinSpec.Model spec, BedrockGeometry geometry,
                          Map<String, BedrockAnimation> animations, Map<MiniWitherAnimationState, List<String>> states,
                          int headBone, BedrockBounds restBounds) {
    }

    static Loaded load(MiniWitherSkinSpec spec, int generation) throws InvalidSkinException {
        List<String> warnings = new ArrayList<>(spec.warnings());
        Parsed body = spec.body() == null ? null : parse(spec, spec.body(), false, warnings);
        Parsed skull = spec.skull() == null ? null : parse(spec, spec.skull(), true, warnings);

        Map<String, Path> files = new LinkedHashMap<>();
        if (body != null) {
            files.put("body", body.spec().texture());
        }
        if (skull != null) {
            files.put("skull", skull.spec().texture());
            if (skull.spec().blessedTexture() != null) {
                files.put("skull_blessed", skull.spec().blessedTexture());
            }
            if (skull.spec().crimsonTexture() != null) {
                files.put("skull_crimson", skull.spec().crimsonTexture());
            }
        }

        Map<String, NativeImage> images = new LinkedHashMap<>();
        List<Identifier> registered = new ArrayList<>();
        boolean done = false;
        try {
            for (Map.Entry<String, Path> file : files.entrySet()) {
                images.put(file.getKey(), decode(spec, file.getValue()));
            }
            TextureManager textures = MinecraftClient.getInstance().getTextureManager();
            Map<String, Identifier> ids = new LinkedHashMap<>();
            for (String name : List.copyOf(images.keySet())) {
                Identifier id = textureId(spec.id(), generation, name);
                NativeImage image = images.remove(name);
                NativeImageBackedTexture texture;
                try {
                    // The texture owns the image from here on. / 此后图像归贴图所有。
                    texture = new NativeImageBackedTexture(image);
                } catch (RuntimeException exception) {
                    image.close();
                    throw exception;
                }
                textures.registerTexture(id, texture);
                registered.add(id);
                ids.put(name, id);
            }
            MiniWitherSkinModel bodyModel = body == null ? null : model(body, ids.get("body"), null, null);
            MiniWitherSkinModel skullModel = skull == null ? null
                    : model(skull, ids.get("skull"), ids.get("skull_blessed"), ids.get("skull_crimson"));
            MiniWitherSkin skin = new MiniWitherSkin(spec.id(), spec.displayName(), bodyModel, skullModel,
                    spec.soundCount());
            done = true;
            return new Loaded(skin, List.copyOf(registered), List.copyOf(warnings));
        } finally {
            for (NativeImage image : images.values()) {
                image.close();
            }
            if (!done) {
                destroy(registered);
            }
        }
    }

    /** {@code sparkwitch:dynamic/mini_wither_skin/<id>/<generation>/<name>}. */
    static Identifier textureId(String skinId, int generation, String name) {
        return SparkWitch.id("dynamic/mini_wither_skin/" + skinId + "/" + generation + "/" + name);
    }

    /** Frees textures this loader registered (render thread). / 释放本加载器注册的贴图（渲染线程）。 */
    static void destroy(List<Identifier> textures) {
        if (textures.isEmpty()) {
            return;
        }
        TextureManager manager = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : textures) {
            try {
                manager.destroyTexture(id);
            } catch (RuntimeException exception) {
                SparkWitch.LOGGER.warn("Unable to free the Mini Wither skin texture {}", id, exception);
            }
        }
    }

    private static Parsed parse(MiniWitherSkinSpec spec, MiniWitherSkinSpec.Model model, boolean skull,
                                List<String> warnings) throws InvalidSkinException {
        String where = skull ? "skull " : "";
        String modelName = spec.shown(model.model());
        BedrockGeometry geometry;
        try {
            geometry = BedrockGeometryParser.parse(MiniWitherSkinFolder.readJson(model.model(), modelName),
                    model.geometry(), model.glowPrefix());
        } catch (BedrockModelException exception) {
            throw new InvalidSkinException(modelName + ": " + exception.getMessage(), exception);
        }

        Map<String, BedrockAnimation> animations = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : model.animations()) {
            String fileName = spec.shown(file);
            JsonElement json = MiniWitherSkinFolder.readJson(file, fileName);
            Map<String, BedrockAnimation> parsed;
            try {
                parsed = BedrockAnimationParser.parse(json);
            } catch (BedrockModelException exception) {
                throw new InvalidSkinException(fileName + ": " + exception.getMessage(), exception);
            }
            for (Map.Entry<String, BedrockAnimation> animation : parsed.entrySet()) {
                String previous = sources.put(animation.getKey(), fileName);
                if (previous != null) {
                    warnings.add(fileName + ": animation \"" + animation.getKey() + "\" replaces the one in "
                            + previous);
                }
                animations.put(animation.getKey(), animation.getValue());
            }
        }

        Map<MiniWitherAnimationState, List<String>> states = MiniWitherSkinStates.resolve(model.states(),
                animations.keySet(), where, warnings);
        int head = skull ? -1 : MiniWitherSkinStates.headBone(geometry::boneIndex, model.head(), warnings);
        BedrockBounds bounds = BedrockModelRenderer.bounds(geometry, null);
        if (bounds.isEmpty() || !finite(bounds)) {
            throw new InvalidSkinException(modelName + " has no cubes to draw");
        }
        return new Parsed(model, geometry, Collections.unmodifiableMap(animations), states, head, bounds);
    }

    private static MiniWitherSkinModel model(Parsed parsed, Identifier texture, @Nullable Identifier blessed,
                                             @Nullable Identifier crimson) {
        return new MiniWitherSkinModel(parsed.geometry(), texture, blessed, crimson, parsed.animations(),
                parsed.states(), parsed.headBone(), parsed.spec().translucent(), parsed.restBounds());
    }

    private static NativeImage decode(MiniWitherSkinSpec spec, Path file) throws InvalidSkinException {
        String shown = spec.shown(file);
        byte[] bytes = MiniWitherSkinFolder.readPng(file, shown);
        NativeImage image;
        try {
            // Not read(byte[]): that copies onto the 64 KiB LWJGL stack. / 不用 read(byte[])：它会复制到 64 KiB 的 LWJGL 栈上。
            image = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException exception) {
            throw new InvalidSkinException(shown + " is not a readable PNG image", exception);
        }
        try {
            MiniWitherSkinFolder.checkImageSize(image.getWidth(), image.getHeight(), shown);
        } catch (InvalidSkinException exception) {
            image.close();
            throw exception;
        }
        return image;
    }

    private static boolean finite(BedrockBounds bounds) {
        return Float.isFinite(bounds.minX()) && Float.isFinite(bounds.minY()) && Float.isFinite(bounds.minZ())
                && Float.isFinite(bounds.maxX()) && Float.isFinite(bounds.maxY()) && Float.isFinite(bounds.maxZ());
    }
}
