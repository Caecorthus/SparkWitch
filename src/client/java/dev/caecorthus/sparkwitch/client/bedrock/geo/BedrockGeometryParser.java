package dev.caecorthus.sparkwitch.client.bedrock.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Bedrock geometry JSON from a player's skin folder. Never trusts the file: limits, NaN checks and readable
 * {@link BedrockModelException} messages.
 * 从玩家皮肤文件夹读取 Bedrock 几何 JSON。从不信任文件内容：有数量上限、NaN 检查，并给出可读的
 * {@link BedrockModelException} 消息。
 */
public final class BedrockGeometryParser {
    public static final int MAX_BONES = 2048;
    public static final int MAX_CUBES = 16384;
    /** Largest accepted magnitude for any coordinate, angle or UV. / 坐标、角度、UV 可接受的最大绝对值。 */
    public static final double MAX_ABS_VALUE = 1_000_000.0;
    public static final int MAX_TEXTURE_SIZE = 65536;

    private static final String MODERN_KEY = "minecraft:geometry";
    private static final String LEGACY_PREFIX = "geometry.";
    private static final float[] ZERO3 = {0.0F, 0.0F, 0.0F};
    private static final float[] ZERO2 = {0.0F, 0.0F};

    private BedrockGeometryParser() {
    }

    /** Whether {@code root} holds Bedrock geometry at its top level (also inside a Java item-model shell). */
    public static boolean isGeometryFile(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return false;
        }
        JsonObject top = root.getAsJsonObject();
        JsonElement modern = top.get(MODERN_KEY);
        if (modern != null && modern.isJsonArray()) {
            for (JsonElement element : modern.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    return true;
                }
            }
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : top.entrySet()) {
            if (isLegacyKey(entry.getKey()) && entry.getValue().isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param identifier which geometry to take when the file has several ({@code description.identifier}); null takes
     *                   the first / 文件含多个几何时选用哪个；null 取第一个
     * @param glowPrefix bones whose own name or an ancestor's name starts with it (ignoring case) draw full-bright;
     *                   empty disables / 自身或祖先名字以此开头（忽略大小写）的骨骼全亮绘制；空字符串表示关闭
     */
    public static BedrockGeometry parse(JsonElement root, @Nullable String identifier, String glowPrefix)
            throws BedrockModelException {
        if (root == null || !root.isJsonObject()) {
            throw new BedrockModelException("Not a Bedrock geometry file: the top level must be a JSON object");
        }
        Candidate chosen = choose(candidates(root.getAsJsonObject()), identifier);
        String where = chosen.identifier().isEmpty() ? "geometry" : "geometry \"" + chosen.identifier() + "\"";

        int textureWidth;
        int textureHeight;
        if (chosen.legacy()) {
            // Blockbench legacy codec: texturewidth/textureheight default to 64. / 旧版默认 64。
            textureWidth = textureSize(chosen.body(), "texturewidth", 64, where);
            textureHeight = textureSize(chosen.body(), "textureheight", 64, where);
        } else {
            // Blockbench 1.12+ codec: texture_width/texture_height default to 16. / 1.12+ 默认 16。
            JsonObject description = object(chosen.body().get("description"), where + " \"description\"");
            textureWidth = textureSize(description, "texture_width", 16, where);
            textureHeight = textureSize(description, "texture_height", 16, where);
        }

        JsonArray bones = list(chosen.body().get("bones"), where + ": \"bones\"");
        int boneCount = bones.size();
        if (boneCount > MAX_BONES) {
            throw new BedrockModelException("Too many bones: " + boneCount + " (limit " + MAX_BONES + ")");
        }
        RawBone[] raw = new RawBone[boneCount];
        int totalCubes = 0;
        for (int i = 0; i < boneCount; i++) {
            raw[i] = readBone(bones.get(i), i);
            totalCubes += raw[i].cubes().size();
            if (totalCubes > MAX_CUBES) {
                throw new BedrockModelException("Too many cubes: more than " + MAX_CUBES);
            }
        }

        int[] order = parentFirstOrder(raw);
        int[] newIndex = new int[boneCount];
        for (int i = 0; i < boneCount; i++) {
            newIndex[order[i]] = i;
        }

        String prefix = glowPrefix == null ? "" : glowPrefix;
        String[] names = new String[boneCount];
        int[] parents = new int[boneCount];
        boolean[] glows = new boolean[boneCount];
        int[] cubeCounts = new int[boneCount];
        float[] pivots = new float[boneCount * 3];
        float[] rotations = new float[boneCount * 3];
        int[] quadOffsets = new int[boneCount + 1];
        int[] cornerOffsets = new int[boneCount + 1];
        FloatArrayList quads = new FloatArrayList();
        FloatArrayList corners = new FloatArrayList();
        BedrockCubeBaker baker = new BedrockCubeBaker(textureWidth, textureHeight, quads, corners);

        for (int b = 0; b < boneCount; b++) {
            RawBone bone = raw[order[b]];
            names[b] = bone.name();
            parents[b] = bone.parent() < 0 ? -1 : newIndex[bone.parent()];
            glows[b] = (!prefix.isEmpty() && bone.name().regionMatches(true, 0, prefix, 0, prefix.length()))
                    || (parents[b] >= 0 && glows[parents[b]]);
            cubeCounts[b] = bone.cubes().size();
            pivots[b * 3] = -bone.pivot()[0] / 16.0F;
            pivots[b * 3 + 1] = bone.pivot()[1] / 16.0F;
            pivots[b * 3 + 2] = bone.pivot()[2] / 16.0F;
            System.arraycopy(bone.rotation(), 0, rotations, b * 3, 3);
            quadOffsets[b] = quads.size() / BedrockGeometry.QUAD_STRIDE;
            cornerOffsets[b] = corners.size() / BedrockGeometry.CUBE_CORNER_STRIDE;
            for (int c = 0; c < bone.cubes().size(); c++) {
                readAndBakeCube(bone, c, baker);
            }
        }
        quadOffsets[boneCount] = quads.size() / BedrockGeometry.QUAD_STRIDE;
        cornerOffsets[boneCount] = corners.size() / BedrockGeometry.CUBE_CORNER_STRIDE;

        return new BedrockGeometry(chosen.identifier(), textureWidth, textureHeight, names, parents, glows, cubeCounts,
                pivots, rotations, quadOffsets, quads.toFloatArray(), cornerOffsets, corners.toFloatArray());
    }

    private record Candidate(String identifier, JsonObject body, boolean legacy) {
    }

    /** A bone as written in the file; {@code parent} is a file index or -1. / 文件中的骨骼；parent 为文件索引或 -1。 */
    private record RawBone(String name, String where, @Nullable String parentName, int parent, float[] pivot,
                           float[] rotation, boolean mirror, float inflate, boolean neverRender, JsonArray cubes) {
        RawBone withParent(int parentIndex) {
            return new RawBone(name, where, parentName, parentIndex, pivot, rotation, mirror, inflate, neverRender,
                    cubes);
        }
    }

    private static List<Candidate> candidates(JsonObject top) throws BedrockModelException {
        List<Candidate> result = new ArrayList<>();
        JsonElement modern = top.get(MODERN_KEY);
        if (modern != null) {
            if (!modern.isJsonArray()) {
                throw new BedrockModelException("\"" + MODERN_KEY + "\" must be a list");
            }
            for (JsonElement element : modern.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject body = element.getAsJsonObject();
                String id = "";
                JsonElement description = body.get("description");
                if (description != null && description.isJsonObject()) {
                    JsonElement idElement = description.getAsJsonObject().get("identifier");
                    if (idElement != null && idElement.isJsonPrimitive() && idElement.getAsJsonPrimitive().isString()) {
                        id = idElement.getAsString();
                    }
                }
                result.add(new Candidate(id, body, false));
            }
        } else {
            for (Map.Entry<String, JsonElement> entry : top.entrySet()) {
                if (isLegacyKey(entry.getKey()) && entry.getValue().isJsonObject()) {
                    // "geometry.child:geometry.parent" inherits in 1.8; only the child's own bones are read.
                    // 1.8 的 "子:父" 继承写法只读取子几何自身的骨骼。
                    String key = entry.getKey();
                    int colon = key.indexOf(':');
                    result.add(new Candidate(colon < 0 ? key : key.substring(0, colon),
                            entry.getValue().getAsJsonObject(), true));
                }
            }
        }
        if (result.isEmpty()) {
            throw new BedrockModelException("No Bedrock geometry found (expected \"" + MODERN_KEY
                    + "\" or a \"geometry.*\" key)");
        }
        return result;
    }

    private static Candidate choose(List<Candidate> candidates, @Nullable String identifier)
            throws BedrockModelException {
        if (identifier == null || identifier.isEmpty()) {
            return candidates.get(0);
        }
        for (Candidate candidate : candidates) {
            if (candidate.identifier().equals(identifier)) {
                return candidate;
            }
        }
        for (Candidate candidate : candidates) {
            if (candidate.identifier().equalsIgnoreCase(identifier)) {
                return candidate;
            }
        }
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < candidates.size() && i < 8; i++) {
            found.append(i == 0 ? "" : ", ").append('"').append(candidates.get(i).identifier()).append('"');
        }
        if (candidates.size() > 8) {
            found.append(", ...");
        }
        throw new BedrockModelException("No geometry \"" + identifier + "\" in this file (it has " + found + ")");
    }

    private static boolean isLegacyKey(String key) {
        return key.regionMatches(true, 0, LEGACY_PREFIX, 0, LEGACY_PREFIX.length());
    }

    private static RawBone readBone(JsonElement element, int fileIndex) throws BedrockModelException {
        if (element == null || !element.isJsonObject()) {
            throw new BedrockModelException("Bone " + (fileIndex + 1) + " is not a JSON object");
        }
        JsonObject bone = element.getAsJsonObject();
        JsonElement nameElement = bone.get("name");
        if (nameElement == null || !nameElement.isJsonPrimitive() || !nameElement.getAsJsonPrimitive().isString()) {
            throw new BedrockModelException("Bone " + (fileIndex + 1) + " has no \"name\"");
        }
        String name = nameElement.getAsString();
        String where = "bone \"" + name + "\"";
        String parentName = null;
        JsonElement parentElement = bone.get("parent");
        if (parentElement != null && !parentElement.isJsonNull()) {
            if (!parentElement.isJsonPrimitive() || !parentElement.getAsJsonPrimitive().isString()) {
                throw new BedrockModelException(where + ": \"parent\" must be a bone name");
            }
            parentName = parentElement.getAsString();
        }
        float inflate = optionalNumber(bone, "inflate", 0.0F, where);
        // locators, poly_mesh, texture_meshes, binding, material and bind_pose_rotation are ignored.
        // 忽略 locators、poly_mesh、texture_meshes、binding、material 与 bind_pose_rotation。
        return new RawBone(name, where, parentName, -1,
                vector(bone, "pivot", ZERO3, where),
                vector(bone, "rotation", ZERO3, where),
                bool(bone, "mirror", false, where),
                inflate,
                bool(bone, "neverRender", false, where),
                list(bone.get("cubes"), where + ": \"cubes\""));
    }

    /**
     * Resolves parents (exact name, then ignoring case, like Blockbench; unknown = root) and returns file indices in
     * depth-first order, so every parent precedes its children.
     * 解析父级（先精确、再忽略大小写，同 Blockbench；找不到视为根），返回深度优先顺序的文件索引，保证父级在子级之前。
     */
    private static int[] parentFirstOrder(RawBone[] raw) throws BedrockModelException {
        int count = raw.length;
        Map<String, Integer> exact = new HashMap<>();
        Map<String, Integer> folded = new HashMap<>();
        for (int i = 0; i < count; i++) {
            exact.putIfAbsent(raw[i].name(), i);
            folded.putIfAbsent(raw[i].name().toLowerCase(Locale.ROOT), i);
        }
        int[] childCount = new int[count];
        for (int i = 0; i < count; i++) {
            String parentName = raw[i].parentName();
            int parent = -1;
            if (parentName != null) {
                Integer match = exact.get(parentName);
                if (match == null) {
                    match = folded.get(parentName.toLowerCase(Locale.ROOT));
                }
                parent = match == null ? -1 : match;
            }
            if (parent == i) {
                throw new BedrockModelException(raw[i].where() + " is its own parent");
            }
            raw[i] = raw[i].withParent(parent);
            if (parent >= 0) {
                childCount[parent]++;
            }
        }
        int[][] children = new int[count][];
        for (int i = 0; i < count; i++) {
            children[i] = new int[childCount[i]];
            childCount[i] = 0;
        }
        for (int i = 0; i < count; i++) {
            int parent = raw[i].parent();
            if (parent >= 0) {
                children[parent][childCount[parent]++] = i;
            }
        }
        int[] order = new int[count];
        int written = 0;
        int[] stack = new int[count];
        for (int root = 0; root < count; root++) {
            if (raw[root].parent() >= 0) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            while (top > 0) {
                int bone = stack[--top];
                order[written++] = bone;
                int[] kids = children[bone];
                for (int k = kids.length - 1; k >= 0; k--) {
                    stack[top++] = kids[k];
                }
            }
        }
        if (written < count) {
            boolean[] seen = new boolean[count];
            for (int i = 0; i < written; i++) {
                seen[order[i]] = true;
            }
            for (int i = 0; i < count; i++) {
                if (!seen[i]) {
                    throw new BedrockModelException(raw[i].where() + " is part of a parent loop");
                }
            }
        }
        return order;
    }

    private static void readAndBakeCube(RawBone bone, int index, BedrockCubeBaker baker)
            throws BedrockModelException {
        String where = bone.where() + " cube " + (index + 1);
        JsonElement element = bone.cubes().get(index);
        if (element == null || !element.isJsonObject()) {
            throw new BedrockModelException(where + " is not a JSON object");
        }
        JsonObject cube = element.getAsJsonObject();
        float[] origin = vector(cube, "origin", ZERO3, where);
        float[] size = vector(cube, "size", ZERO3, where);
        float[] rotation = vector(cube, "rotation", ZERO3, where);
        JsonElement pivotElement = cube.get("pivot");
        float[] pivot = pivotElement == null || pivotElement.isJsonNull() ? null : vector(cube, "pivot", ZERO3, where);
        float inflate = optionalNumber(cube, "inflate", bone.inflate(), where);
        boolean mirror = bool(cube, "mirror", bone.mirror(), where);

        float boxU = 0.0F;
        float boxV = 0.0F;
        BedrockCubeBaker.FaceUv[] faces = null;
        JsonElement uv = cube.get("uv");
        if (uv != null && uv.isJsonObject()) {
            faces = new BedrockCubeBaker.FaceUv[6];
            JsonObject faceMap = uv.getAsJsonObject();
            for (int face = 0; face < 6; face++) {
                String faceName = BedrockCubeBaker.FACE_NAMES[face];
                JsonElement faceElement = faceMap.get(faceName);
                if (faceElement == null || faceElement.isJsonNull()) {
                    continue;
                }
                String faceWhere = where + " face \"" + faceName + "\"";
                if (!faceElement.isJsonObject()) {
                    throw new BedrockModelException(faceWhere + " is not a JSON object");
                }
                JsonObject faceObject = faceElement.getAsJsonObject();
                // material_instance is ignored. / 忽略 material_instance。
                float[] faceUv = vector2(faceObject, "uv", ZERO2, faceWhere);
                JsonElement sizeElement = faceObject.get("uv_size");
                float[] faceSize = sizeElement == null || sizeElement.isJsonNull()
                        ? new float[]{Float.NaN, Float.NaN}
                        : vector2(faceObject, "uv_size", ZERO2, faceWhere);
                float turns = optionalNumber(faceObject, "uv_rotation", 0.0F, faceWhere);
                faces[face] = new BedrockCubeBaker.FaceUv(faceUv[0], faceUv[1], faceSize[0], faceSize[1],
                        Math.floorMod(Math.round(turns / 90.0F), 4));
            }
        } else if (uv != null && !uv.isJsonNull()) {
            float[] box = vector2(cube, "uv", ZERO2, where);
            boxU = box[0];
            boxV = box[1];
        }
        if (bone.neverRender()) {
            return; // GeckoLib: neverRender hides the bone's own cubes only / 只隐藏该骨骼自身的方块
        }
        baker.bake(origin, size, inflate, pivot, rotation, faces, boxU, boxV, mirror);
    }

    private static int textureSize(@Nullable JsonObject object, String key, int fallback, String where)
            throws BedrockModelException {
        if (object == null) {
            return fallback;
        }
        float value = optionalNumber(object, key, fallback, where);
        int rounded = Math.round(value);
        if (rounded < 1 || rounded > MAX_TEXTURE_SIZE) {
            throw new BedrockModelException(where + ": \"" + key + "\" must be between 1 and " + MAX_TEXTURE_SIZE);
        }
        return rounded;
    }

    @Nullable
    private static JsonObject object(@Nullable JsonElement element, String where) throws BedrockModelException {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new BedrockModelException(where + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray list(@Nullable JsonElement element, String where) throws BedrockModelException {
        if (element == null || element.isJsonNull()) {
            return new JsonArray();
        }
        if (!element.isJsonArray()) {
            throw new BedrockModelException(where + " must be a list");
        }
        return element.getAsJsonArray();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback, String where)
            throws BedrockModelException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new BedrockModelException(where + ": \"" + key + "\" must be true or false");
        }
        return element.getAsBoolean();
    }

    private static float optionalNumber(JsonObject object, String key, float fallback, String where)
            throws BedrockModelException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return number(element, where + ": \"" + key + "\"");
    }

    private static float[] vector(JsonObject object, String key, float[] fallback, String where)
            throws BedrockModelException {
        return numbers(object, key, 3, fallback, where);
    }

    private static float[] vector2(JsonObject object, String key, float[] fallback, String where)
            throws BedrockModelException {
        return numbers(object, key, 2, fallback, where);
    }

    private static float[] numbers(JsonObject object, String key, int count, float[] fallback, String where)
            throws BedrockModelException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback.clone();
        }
        String field = where + ": \"" + key + "\"";
        if (!element.isJsonArray() || element.getAsJsonArray().size() < count) {
            throw new BedrockModelException(field + " must be a list of " + count + " numbers");
        }
        JsonArray array = element.getAsJsonArray();
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = number(array.get(i), field);
        }
        return result;
    }

    private static float number(JsonElement element, String field) throws BedrockModelException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new BedrockModelException(field + " must be a number");
        }
        double value;
        try {
            value = ((JsonPrimitive) element).getAsDouble();
        } catch (NumberFormatException exception) {
            throw new BedrockModelException(field + " is not a valid number");
        }
        if (!Double.isFinite(value)) {
            throw new BedrockModelException(field + " is not a finite number");
        }
        if (Math.abs(value) > MAX_ABS_VALUE) {
            throw new BedrockModelException(field + " is out of range (" + value + ")");
        }
        return (float) value;
    }
}
