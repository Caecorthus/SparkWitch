package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The skin folder contract (plan "Skin folder contract"), with no Minecraft dependency: scanning the skins directory,
 * its bilingual README, and resolving one folder plus its optional {@value #MANIFEST} into a
 * {@link MiniWitherSkinSpec}. Never trusts the folder: ids are checked, every path must stay inside the skin folder
 * (no absolute paths, no {@code ..}, no symlink out) and every file obeys the size limits. Problems are
 * {@link InvalidSkinException}s with a short English message naming the file and key, which the settings screen shows.
 * 皮肤文件夹约定（计划「Skin folder contract」），不依赖 Minecraft：扫描皮肤目录、生成双语 README，并把单个文件夹及其可选的
 * {@value #MANIFEST} 解析为 {@link MiniWitherSkinSpec}。从不信任文件夹内容：检查 id，所有路径必须留在皮肤文件夹内（不允许绝对
 * 路径、{@code ..} 或指向外部的符号链接），所有文件都受大小限制。问题以 {@link InvalidSkinException} 抛出，附带指明文件与键的
 * 简短英文消息，设置界面会直接显示。
 */
public final class MiniWitherSkinFolder {
    public static final String MANIFEST = "skin.json";
    public static final String README = "README.txt";
    public static final String SKULL_FOLDER = "skull";
    public static final String SOUNDS_FOLDER = "sounds";
    public static final String DEFAULT_GLOW_PREFIX = "ysmGlow";
    public static final long MAX_JSON_BYTES = 16L << 20;
    public static final long MAX_PNG_BYTES = 16L << 20;
    public static final int MAX_PNG_SIDE = 4096;
    public static final long MAX_OGG_BYTES = 8L << 20;
    public static final int MAX_SOUND_FILES = 32;
    /**
     * Per model: more {@code *.animation.json} files are ignored with a warning (the first ones are kept).
     * 每个模型：超出数量的 {@code *.animation.json} 文件会被忽略并给出警告（保留前面的）。
     */
    public static final int MAX_ANIMATION_FILES = 8;
    /**
     * Per skin: every file read (JSON, PNG, ogg) together; more is an error. Bounds the synchronous load.
     * 每个皮肤：所有读取的文件（JSON、PNG、ogg）总和；超出即报错。用于限制同步加载的工作量。
     */
    public static final long MAX_SKIN_BYTES = 48L << 20;
    /**
     * Per skin: {@code *.json} files opened to find the models (folder and {@code skull/} together); more is an error.
     * 每个皮肤：为查找模型而打开的 {@code *.json} 文件数（文件夹与 {@code skull/} 合计）；超出即报错。
     */
    public static final int MAX_MODEL_CANDIDATES = 32;
    /** Display names longer than this are cut. / 超过此长度的显示名会被截断。 */
    public static final int MAX_NAME_LENGTH = 64;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");
    /** Skull-folder textures picked as the per-kind skins by default. / 头颅文件夹中默认作为分类贴图的文件。 */
    private static final String BLESSED_STEM = "blessed";
    private static final String CRIMSON_STEM = "crimson";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private MiniWitherSkinFolder() {
    }

    /** One skin folder found by {@link #scan}. / {@link #scan} 找到的一个皮肤文件夹。 */
    public record Found(String id, String displayName, Path folder) {
    }

    /** A skin folder that cannot be used; the message is shown as is. / 无法使用的皮肤文件夹；消息原样显示。 */
    public static final class InvalidSkinException extends Exception {
        public InvalidSkinException(String message) {
            super(message);
        }

        public InvalidSkinException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The id of a folder name (lower-cased), or null when it has other characters. / 文件夹名对应的 id，非法时为 null。 */
    @Nullable
    public static String idOf(String folderName) {
        String id = folderName.toLowerCase(Locale.ROOT);
        return isValidId(id) ? id : null;
    }

    /** {@code [a-z0-9_.-]+}, also an identifier-safe sound and texture tag. / 同时可安全用作标识符中的标记。 */
    public static boolean isValidId(@Nullable String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * Creates {@code root} and its README when missing, then lists every skin folder sorted by id. Hidden folders
     * (leading {@code .}) are skipped quietly; a name with other characters, or a second folder with the same id, is
     * skipped and logged. Never throws: an unreadable root lists nothing.
     * {@code root} 或其 README 缺失时创建它们，然后按 id 排序列出所有皮肤文件夹。隐藏文件夹（以 {@code .} 开头）静默跳过；名字含
     * 其它字符的文件夹或 id 重复的第二个文件夹会被跳过并记录日志。从不抛出异常：无法读取的根目录返回空列表。
     */
    public static List<Found> scan(Path root) {
        try {
            Files.createDirectories(root);
            ensureReadme(root);
        } catch (IOException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to create the Mini Wither skins folder {}", root, exception);
        }
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(folders::add);
        } catch (IOException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to list the Mini Wither skins folder {}", root, exception);
            return List.of();
        }
        folders.sort(Comparator.comparing(path -> path.getFileName().toString()));
        Map<String, Found> found = new LinkedHashMap<>();
        for (Path folder : folders) {
            String name = folder.getFileName().toString();
            if (name.startsWith(".")) {
                continue;
            }
            String id = idOf(name);
            if (id == null) {
                SparkWitch.LOGGER.warn("Skipping the Mini Wither skin folder \"{}\": use only a-z, 0-9, _ . -", name);
            } else if (found.containsKey(id)) {
                SparkWitch.LOGGER.warn("Skipping the Mini Wither skin folder \"{}\": id \"{}\" is taken", name, id);
            } else {
                found.put(id, new Found(id, displayName(folder, id), folder));
            }
        }
        List<Found> sorted = new ArrayList<>(found.values());
        sorted.sort(Comparator.comparing(Found::id));
        return List.copyOf(sorted);
    }

    /**
     * Writes the bilingual {@value #README} into {@code root} only when no file of that name exists, so a README the
     * player edited is never overwritten. Returns whether it wrote.
     * 仅当 {@code root} 中没有同名文件时才写入双语 {@value #README}，玩家编辑过的 README 绝不会被覆盖。返回是否写入。
     */
    public static boolean ensureReadme(Path root) throws IOException {
        Path readme = root.resolve(README);
        if (Files.exists(readme, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        Files.createDirectories(root);
        Files.writeString(readme, MiniWitherSkinReadme.TEXT.replace("\n", System.lineSeparator()),
                StandardCharsets.UTF_8);
        return true;
    }

    /** {@code name} from the manifest, else the id; never throws. / 清单中的 name，否则为 id；从不抛出异常。 */
    public static String displayName(Path folder, String id) {
        try {
            Path manifest = folder.resolve(MANIFEST);
            if (!Files.isRegularFile(manifest)) {
                return id;
            }
            Path real = folder.toRealPath();
            if (!manifest.toRealPath().startsWith(real)) {
                return id;
            }
            JsonElement root = readJson(manifest, MANIFEST);
            if (root.isJsonObject() && root.getAsJsonObject().get("name") instanceof JsonPrimitive name
                    && name.isString()) {
                return cleanName(name.getAsString(), id);
            }
        } catch (InvalidSkinException | IOException | RuntimeException ignored) {
            // The load reports a broken manifest. / 清单错误由加载时报告。
        }
        return id;
    }

    /**
     * Resolves one skin folder. {@code isGeometry} decides which root {@code *.json} files are models (production
     * passes {@code BedrockGeometryParser::isGeometryFile}; tests a plain JSON check).
     * 解析一个皮肤文件夹。{@code isGeometry} 决定哪些根目录 {@code *.json} 是模型（正式代码传入
     * {@code BedrockGeometryParser::isGeometryFile}，测试传入简单的 JSON 检查）。
     */
    public static MiniWitherSkinSpec resolve(String id, Path folder, Predicate<JsonElement> isGeometry)
            throws InvalidSkinException {
        if (!isValidId(id)) {
            throw new InvalidSkinException("Invalid skin id \"" + id + "\": use only a-z, 0-9, _ . -");
        }
        Path root;
        try {
            root = folder.toRealPath();
        } catch (IOException | RuntimeException exception) {
            throw new InvalidSkinException("Skin folder not found: " + folder.getFileName(), exception);
        }
        if (!Files.isDirectory(root)) {
            throw new InvalidSkinException("Not a folder: " + folder.getFileName());
        }
        List<String> warnings = new ArrayList<>();
        Context context = new Context(root, isGeometry, warnings);
        JsonObject manifest = new JsonObject();
        Path manifestPath = root.resolve(MANIFEST);
        if (Files.exists(manifestPath)) {
            Path real = context.existingFile(manifestPath, MANIFEST);
            context.charge(real, MAX_JSON_BYTES);
            JsonElement element = readJson(real, MANIFEST);
            if (!element.isJsonObject()) {
                throw new InvalidSkinException(MANIFEST + " must be a JSON object");
            }
            manifest = element.getAsJsonObject();
        }
        warnUnknownKeys(manifest, "", Set.of("name", "model", "geometry", "texture", "animations", "states", "head",
                "glowPrefix", "translucent", "skull", "sounds"), warnings);

        String name = id;
        String manifestName = context.string(manifest, "name", "");
        if (manifestName != null) {
            name = cleanName(manifestName, id);
        }
        String glowPrefix = context.string(manifest, "glowPrefix", "");
        if (glowPrefix == null) {
            glowPrefix = DEFAULT_GLOW_PREFIX;
        }
        MiniWitherSkinSpec.Model body = context.model(manifest, root, "", false, glowPrefix);

        JsonObject skullSection = new JsonObject();
        JsonElement skullElement = manifest.get("skull");
        if (skullElement != null && !skullElement.isJsonNull()) {
            if (!skullElement.isJsonObject()) {
                throw new InvalidSkinException(MANIFEST + ": \"skull\" must be an object");
            }
            skullSection = skullElement.getAsJsonObject();
            warnUnknownKeys(skullSection, "skull.", Set.of("model", "geometry", "texture", "blessedTexture",
                    "crimsonTexture", "animations", "states", "glowPrefix", "translucent"), warnings);
        }
        Path skullDir = context.subFolder(SKULL_FOLDER);
        MiniWitherSkinSpec.Model skull = context.model(skullSection, skullDir, "skull.", true, glowPrefix);

        Map<MiniWitherSoundSlot, List<Path>> sounds = context.sounds(manifest);
        if (body == null && skull == null && sounds.isEmpty()) {
            throw new InvalidSkinException("Nothing to load: add a model (.json + .png), a skull/ folder or a sounds/"
                    + " folder");
        }
        return new MiniWitherSkinSpec(id, name, root, body, skull, sounds, warnings);
    }

    /**
     * Reads a JSON file of at most {@link #MAX_JSON_BYTES} (a leading BOM is ignored); {@code shown} names it in
     * errors.
     * 读取不超过 {@link #MAX_JSON_BYTES} 的 JSON 文件（忽略开头的 BOM）。{@code shown} 用于错误消息中的文件名。
     */
    public static JsonElement readJson(Path file, String shown) throws InvalidSkinException {
        checkSize(file, MAX_JSON_BYTES, shown);
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InvalidSkinException("Unable to read " + shown, exception);
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException | StackOverflowError exception) {
            throw new InvalidSkinException(shown + " is not valid JSON: " + shortMessage(exception), exception);
        }
    }

    /** Reads a PNG of at most {@link #MAX_PNG_BYTES}, checking its header size. / 读取 PNG，并检查头部尺寸。 */
    public static byte[] readPng(Path file, String shown) throws InvalidSkinException {
        checkSize(file, MAX_PNG_BYTES, shown);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new InvalidSkinException("Unable to read " + shown, exception);
        }
        checkPngHeader(bytes, shown);
        return bytes;
    }

    /** Checks the PNG signature and that both IHDR sides are 1..4096. / 检查 PNG 签名与 IHDR 尺寸。 */
    static void checkPngHeader(byte[] head, String shown) throws InvalidSkinException {
        if (head.length < 24) {
            throw new InvalidSkinException(shown + " is not a PNG image");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (head[i] != PNG_SIGNATURE[i]) {
                throw new InvalidSkinException(shown + " is not a PNG image");
            }
        }
        if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') {
            throw new InvalidSkinException(shown + " is not a PNG image");
        }
        long width = readUnsignedInt(head, 16);
        long height = readUnsignedInt(head, 20);
        checkImageSize(width, height, shown);
    }

    /** Both sides must be 1..{@link #MAX_PNG_SIDE}. / 两边都必须在 1..4096 之间。 */
    public static void checkImageSize(long width, long height, String shown) throws InvalidSkinException {
        if (width <= 0 || height <= 0 || width > MAX_PNG_SIDE || height > MAX_PNG_SIDE) {
            throw new InvalidSkinException(shown + " is " + width + "×" + height + " px; the limit is "
                    + MAX_PNG_SIDE + "×" + MAX_PNG_SIDE);
        }
    }

    private static long readUnsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    static long checkSize(Path file, long max, String shown) throws InvalidSkinException {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException exception) {
            throw new InvalidSkinException("Unable to read " + shown, exception);
        }
        if (size > max) {
            throw new InvalidSkinException(shown + " is too large (" + mebibytes(size) + "; the limit is "
                    + mebibytes(max) + ")");
        }
        return size;
    }

    private static String mebibytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (double) (1L << 20));
    }

    private static String cleanName(String raw, String id) {
        StringBuilder clean = new StringBuilder();
        raw.trim().codePoints().forEach(codePoint -> clean.appendCodePoint(
                Character.isISOControl(codePoint) ? ' ' : codePoint));
        String name = clean.toString().trim();
        if (name.isEmpty()) {
            return id;
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
        }
        return name;
    }

    private static void warnUnknownKeys(JsonObject section, String prefix, Set<String> known, List<String> warnings) {
        for (String key : section.keySet()) {
            if (!known.contains(key)) {
                warnings.add(MANIFEST + ": unknown key \"" + prefix + key + "\" ignored");
            }
        }
    }

    private static String shortMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static boolean hasExtension(Path file, String extension) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    /**
     * Resolution state of one folder, including the input budget: every distinct file is charged once, before it is
     * read.
     * 单个文件夹的解析状态，含输入预算：每个不同的文件在读取前计入一次。
     */
    private static final class Context {
        private final Path root;
        private final Predicate<JsonElement> isGeometry;
        private final List<String> warnings;
        private final Map<Path, Long> charged = new HashMap<>();
        private long chargedBytes;
        private int scannedCandidates;

        Context(Path root, Predicate<JsonElement> isGeometry, List<String> warnings) {
            this.root = root;
            this.isGeometry = isGeometry;
            this.warnings = warnings;
        }

        /**
         * Checks {@code file}'s own limit, then adds it to the skin's {@link #MAX_SKIN_BYTES} budget (once per file).
         * 检查文件自身的上限，再把它计入皮肤的 {@link #MAX_SKIN_BYTES} 预算（每个文件只计一次）。
         */
        void charge(Path file, long max) throws InvalidSkinException {
            long size = checkSize(file, max, shown(file));
            if (charged.putIfAbsent(file, size) != null) {
                return;
            }
            chargedBytes += size;
            if (chargedBytes > MAX_SKIN_BYTES) {
                throw new InvalidSkinException("The skin's files add up to more than " + mebibytes(MAX_SKIN_BYTES)
                        + " (at " + shown(file) + ")");
            }
        }

        /** {@code root}-relative, forward slashes. / 相对 root、使用正斜杠的路径。 */
        String shown(Path file) {
            Path relative = file.startsWith(root) ? root.relativize(file) : file.getFileName();
            return relative.toString().replace('\\', '/');
        }

        /**
         * A manifest path inside the skin folder: no absolute path, drive or {@code ..} segment, and, once it exists,
         * no symlink leading out.
         * 清单中的路径必须在皮肤文件夹内：不允许绝对路径、盘符或 {@code ..} 片段，且存在时不能经符号链接指向外部。
         */
        Path inside(String relative, String key) throws InvalidSkinException {
            String path = relative.replace('\\', '/');
            String quoted = "\"" + key + "\" (" + relative + ")";
            if (path.isBlank()) {
                throw new InvalidSkinException(MANIFEST + ": " + quoted + " is empty");
            }
            if (path.startsWith("/") || path.indexOf(':') >= 0) {
                throw new InvalidSkinException(MANIFEST + ": " + quoted + " must be a path inside the skin folder");
            }
            for (String segment : path.split("/")) {
                if (segment.equals("..")) {
                    throw new InvalidSkinException(MANIFEST + ": " + quoted + " must not use \"..\"");
                }
            }
            Path resolved;
            try {
                resolved = root.resolve(path).normalize();
            } catch (InvalidPathException exception) {
                throw new InvalidSkinException(MANIFEST + ": " + quoted + " is not a valid path", exception);
            }
            if (!resolved.startsWith(root) || resolved.equals(root)) {
                throw new InvalidSkinException(MANIFEST + ": " + quoted + " must be a path inside the skin folder");
            }
            if (!Files.isRegularFile(resolved)) {
                throw new InvalidSkinException(MANIFEST + ": " + quoted + " not found");
            }
            return existingFile(resolved, relative);
        }

        /** The real path of an existing file, refused when a symlink leads out. / 现有文件的真实路径；符号链接指向外部时拒绝。 */
        Path existingFile(Path file, String shown) throws InvalidSkinException {
            Path real;
            try {
                real = file.toRealPath();
            } catch (IOException | RuntimeException exception) {
                throw new InvalidSkinException("Unable to read " + shown, exception);
            }
            if (!real.startsWith(root)) {
                throw new InvalidSkinException(shown + " links outside the skin folder");
            }
            if (!Files.isRegularFile(real)) {
                throw new InvalidSkinException(shown + " is not a file");
            }
            return real;
        }

        /** A sub-folder's real path, or null when absent or leading out. / 子文件夹的真实路径；缺失或指向外部时为 null。 */
        @Nullable
        Path subFolder(String name) throws InvalidSkinException {
            Path dir = root.resolve(name);
            if (!Files.isDirectory(dir)) {
                return null;
            }
            try {
                Path real = dir.toRealPath();
                if (!real.startsWith(root)) {
                    throw new InvalidSkinException(name + "/ links outside the skin folder");
                }
                return real;
            } catch (IOException exception) {
                throw new InvalidSkinException("Unable to read " + name + "/", exception);
            }
        }

        /** Regular files directly in {@code dir}, real paths, sorted by name. / dir 中的普通文件（真实路径，按名排序）。 */
        List<Path> files(@Nullable Path dir, Predicate<Path> filter) throws InvalidSkinException {
            if (dir == null) {
                return List.of();
            }
            List<Path> listed = new ArrayList<>();
            try (Stream<Path> children = Files.list(dir)) {
                children.filter(filter).forEach(listed::add);
            } catch (IOException | RuntimeException exception) {
                throw new InvalidSkinException("Unable to list " + (dir.equals(root) ? "the skin folder"
                        : shown(dir) + "/"), exception);
            }
            listed.sort(Comparator.comparing(path -> path.getFileName().toString()));
            List<Path> files = new ArrayList<>();
            for (Path file : listed) {
                if (Files.isRegularFile(file)) {
                    files.add(existingFile(file, shown(file)));
                }
            }
            return files;
        }

        /** A string key; null when absent. / 字符串键；缺失时为 null。 */
        @Nullable
        String string(JsonObject section, String key, String prefix) throws InvalidSkinException {
            JsonElement value = section.get(key);
            if (value == null || value.isJsonNull()) {
                return null;
            }
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                return primitive.getAsString();
            }
            throw new InvalidSkinException(MANIFEST + ": \"" + prefix + key + "\" must be a string");
        }

        boolean bool(JsonObject section, String key, String prefix) throws InvalidSkinException {
            JsonElement value = section.get(key);
            if (value == null || value.isJsonNull()) {
                return false;
            }
            if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            throw new InvalidSkinException(MANIFEST + ": \"" + prefix + key + "\" must be true or false");
        }

        /** A string or an array of strings; null when absent. / 字符串或字符串数组；缺失时为 null。 */
        @Nullable
        List<String> strings(JsonObject section, String key, String shownKey) throws InvalidSkinException {
            JsonElement value = section.get(key);
            if (value == null || value.isJsonNull()) {
                return null;
            }
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                return List.of(primitive.getAsString());
            }
            if (value instanceof JsonArray array) {
                List<String> strings = new ArrayList<>();
                for (JsonElement element : array) {
                    if (!(element instanceof JsonPrimitive primitive && primitive.isString())) {
                        throw new InvalidSkinException(MANIFEST + ": \"" + shownKey
                                + "\" must be a string or a list of strings");
                    }
                    strings.add(primitive.getAsString());
                }
                return strings;
            }
            throw new InvalidSkinException(MANIFEST + ": \"" + shownKey + "\" must be a string or a list of strings");
        }

        @Nullable
        MiniWitherSkinSpec.Model model(JsonObject section, @Nullable Path dir, String prefix, boolean skull,
                                       String inheritedGlow) throws InvalidSkinException {
            String where = prefix.isEmpty() ? "" : prefix.substring(0, prefix.length() - 1) + " ";
            String modelKey = string(section, "model", prefix);
            Path model;
            if (modelKey != null) {
                model = inside(modelKey, prefix + "model");
                charge(model, MAX_JSON_BYTES);
            } else {
                model = discoverModel(dir, where, prefix);
                if (model == null) {
                    for (String key : List.of("texture", "blessedTexture", "crimsonTexture", "animations", "states",
                            "geometry")) {
                        if (section.has(key)) {
                            warnings.add(MANIFEST + ": \"" + prefix + key + "\" ignored: no " + where + "model");
                        }
                    }
                    return null;
                }
            }

            String geometry = string(section, "geometry", prefix);
            Path texture = texture(section, dir, prefix, where, model, skull);
            Path blessed = null;
            Path crimson = null;
            if (skull) {
                blessed = kindTexture(section, dir, prefix, "blessedTexture", BLESSED_STEM);
                crimson = kindTexture(section, dir, prefix, "crimsonTexture", CRIMSON_STEM);
            }

            List<Path> animations = new ArrayList<>();
            List<String> animationKeys = strings(section, "animations", prefix + "animations");
            int animationFiles;
            if (animationKeys != null) {
                List<String> distinct = animationKeys.stream().distinct().toList();
                animationFiles = distinct.size();
                for (String animation : distinct.subList(0, Math.min(distinct.size(), MAX_ANIMATION_FILES))) {
                    Path file = inside(animation, prefix + "animations");
                    if (!animations.contains(file)) {
                        animations.add(file);
                    }
                }
            } else {
                List<Path> found = files(dir, path -> isAnimationFile(path));
                animationFiles = found.size();
                animations.addAll(found.subList(0, Math.min(found.size(), MAX_ANIMATION_FILES)));
            }
            if (animationFiles > MAX_ANIMATION_FILES) {
                warnings.add(where + "animations: " + animationFiles + " files, only the first "
                        + MAX_ANIMATION_FILES + " are used");
            }
            for (Path file : animations) {
                charge(file, MAX_JSON_BYTES);
            }

            Map<MiniWitherAnimationState, List<String>> states = states(section, prefix);
            String head = skull ? null : string(section, "head", prefix);
            String glowPrefix = string(section, "glowPrefix", prefix);
            boolean translucent = bool(section, "translucent", prefix);
            return new MiniWitherSkinSpec.Model(model, geometry, texture, blessed, crimson, animations, states, head,
                    glowPrefix == null ? inheritedGlow : glowPrefix, translucent);
        }

        /**
         * The only {@code *.json} in {@code dir} (not {@value #MANIFEST}, not {@code *.animation.json}) whose content
         * is geometry; several is an error. A candidate that is not valid JSON is an error too, since it is most
         * likely the broken model.
         * dir 中唯一内容为几何的 {@code *.json}（不含 skin.json 与 {@code *.animation.json}）；多个则报错。无效 JSON 的候选同样
         * 报错，因为它很可能就是损坏的模型。
         */
        @Nullable
        private Path discoverModel(@Nullable Path dir, String where, String prefix) throws InvalidSkinException {
            List<Path> candidates = new ArrayList<>();
            List<Path> jsonFiles = files(dir, path -> hasExtension(path, ".json") && !isAnimationFile(path)
                    && !path.getFileName().toString().equalsIgnoreCase(MANIFEST));
            scannedCandidates += jsonFiles.size();
            if (scannedCandidates > MAX_MODEL_CANDIDATES) {
                throw new InvalidSkinException("Too many .json files to search for a model (" + scannedCandidates
                        + "; the limit is " + MAX_MODEL_CANDIDATES + "): set \"" + prefix + "model\" in " + MANIFEST);
            }
            for (Path file : jsonFiles) {
                charge(file, MAX_JSON_BYTES);
                if (isGeometry.test(readJson(file, shown(file)))) {
                    candidates.add(file);
                }
            }
            if (candidates.size() > 1) {
                List<String> names = candidates.stream().map(this::shown).toList();
                throw new InvalidSkinException("Several " + where + "models found (" + String.join(", ", names)
                        + "): set \"" + prefix + "model\" in " + MANIFEST);
            }
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        private Path texture(JsonObject section, @Nullable Path dir, String prefix, String where, Path model,
                             boolean skull) throws InvalidSkinException {
            String key = string(section, "texture", prefix);
            Path texture;
            if (key != null) {
                texture = inside(key, prefix + "texture");
            } else {
                List<Path> pngs = files(dir, path -> hasExtension(path, ".png")
                        && !(skull && (stem(path).equals(BLESSED_STEM) || stem(path).equals(CRIMSON_STEM))));
                if (pngs.isEmpty()) {
                    throw new InvalidSkinException("No texture for " + shown(model)
                            + ": put one .png next to it or set \"" + prefix + "texture\" in " + MANIFEST);
                }
                if (pngs.size() > 1) {
                    List<String> names = pngs.stream().map(this::shown).toList();
                    throw new InvalidSkinException("Several " + where + "textures found (" + String.join(", ", names)
                            + "): set \"" + prefix + "texture\" in " + MANIFEST);
                }
                texture = pngs.get(0);
            }
            readPngHeader(texture);
            return texture;
        }

        @Nullable
        private Path kindTexture(JsonObject section, @Nullable Path dir, String prefix, String key, String stem)
                throws InvalidSkinException {
            String value = string(section, key, prefix);
            Path texture = null;
            if (value != null) {
                texture = inside(value, prefix + key);
            } else {
                for (Path file : files(dir, path -> hasExtension(path, ".png") && stem(path).equals(stem))) {
                    texture = file;
                }
            }
            if (texture != null) {
                readPngHeader(texture);
            }
            return texture;
        }

        /** Size and header check without keeping the bytes. / 只检查大小与头部，不保留数据。 */
        private void readPngHeader(Path texture) throws InvalidSkinException {
            String shown = shown(texture);
            charge(texture, MAX_PNG_BYTES);
            byte[] head = new byte[24];
            int read = 0;
            try (InputStream in = Files.newInputStream(texture)) {
                while (read < head.length) {
                    int n = in.read(head, read, head.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
            } catch (IOException exception) {
                throw new InvalidSkinException("Unable to read " + shown, exception);
            }
            checkPngHeader(read == head.length ? head : new byte[0], shown);
        }

        private Map<MiniWitherAnimationState, List<String>> states(JsonObject section, String prefix)
                throws InvalidSkinException {
            Map<MiniWitherAnimationState, List<String>> states = new EnumMap<>(MiniWitherAnimationState.class);
            JsonElement value = section.get("states");
            if (value == null || value.isJsonNull()) {
                return states;
            }
            if (!value.isJsonObject()) {
                throw new InvalidSkinException(MANIFEST + ": \"" + prefix + "states\" must be an object");
            }
            JsonObject object = value.getAsJsonObject();
            for (String key : object.keySet()) {
                MiniWitherAnimationState state = null;
                for (MiniWitherAnimationState candidate : MiniWitherAnimationState.values()) {
                    if (candidate.key().equalsIgnoreCase(key)) {
                        state = candidate;
                    }
                }
                if (state == null) {
                    warnings.add(MANIFEST + ": unknown state \"" + prefix + "states." + key + "\" ignored");
                    continue;
                }
                List<String> names = strings(object, key, prefix + "states." + key);
                states.put(state, names == null ? List.of() : List.copyOf(names));
            }
            return states;
        }

        /**
         * Per slot: the manifest's {@code sounds.<slot>} list, else {@code sounds/<slot>.ogg} plus the
         * {@code <slot><digits>.ogg} / {@code <slot>_<digits>.ogg} variants; at most {@link #MAX_SOUND_FILES} in all.
         * 每个槽位：清单中的 {@code sounds.<slot>} 列表，否则为 {@code sounds/<slot>.ogg} 及其
         * {@code <slot><数字>.ogg} / {@code <slot>_<数字>.ogg} 变体；总数最多 {@link #MAX_SOUND_FILES} 个。
         */
        Map<MiniWitherSoundSlot, List<Path>> sounds(JsonObject manifest) throws InvalidSkinException {
            JsonObject explicit = new JsonObject();
            JsonElement value = manifest.get("sounds");
            if (value != null && !value.isJsonNull()) {
                if (!value.isJsonObject()) {
                    throw new InvalidSkinException(MANIFEST + ": \"sounds\" must be an object");
                }
                explicit = value.getAsJsonObject();
                for (String key : explicit.keySet()) {
                    if (MiniWitherSoundSlot.byKey(key) == null) {
                        warnings.add(MANIFEST + ": unknown sound \"sounds." + key + "\" ignored");
                    }
                }
            }
            List<Path> folderFiles = files(subFolder(SOUNDS_FOLDER), path -> hasExtension(path, ".ogg"));
            Set<Path> claimed = new HashSet<>();
            Map<MiniWitherSoundSlot, List<Path>> sounds = new EnumMap<>(MiniWitherSoundSlot.class);
            int total = 0;
            for (MiniWitherSoundSlot slot : MiniWitherSoundSlot.values()) {
                List<Path> files = new ArrayList<>();
                List<String> listed = strings(explicit, slot.key(), "sounds." + slot.key());
                if (listed != null) {
                    for (String entry : listed) {
                        Path file = inside(entry, "sounds." + slot.key());
                        if (!hasExtension(file, ".ogg")) {
                            throw new InvalidSkinException(MANIFEST + ": \"sounds." + slot.key() + "\" ("
                                    + entry + ") must be an .ogg file");
                        }
                        files.add(file);
                    }
                } else {
                    files.addAll(variants(folderFiles, slot.key()));
                }
                claimed.addAll(variants(folderFiles, slot.key()));
                for (Path file : files) {
                    checkOgg(file);
                }
                total += files.size();
                if (!files.isEmpty()) {
                    sounds.put(slot, List.copyOf(files));
                }
            }
            if (total > MAX_SOUND_FILES) {
                throw new InvalidSkinException("Too many sound files (" + total + "; the limit is " + MAX_SOUND_FILES
                        + ")");
            }
            for (Path file : folderFiles) {
                if (!claimed.contains(file)) {
                    warnings.add(shown(file) + " matches no sound name and is ignored");
                }
            }
            return sounds;
        }

        private void checkOgg(Path file) throws InvalidSkinException {
            String shown = shown(file);
            charge(file, MAX_OGG_BYTES);
            byte[] magic = new byte[4];
            int read;
            try (InputStream in = Files.newInputStream(file)) {
                read = in.readNBytes(magic, 0, magic.length);
            } catch (IOException exception) {
                throw new InvalidSkinException("Unable to read " + shown, exception);
            }
            if (read < 4 || magic[0] != 'O' || magic[1] != 'g' || magic[2] != 'g' || magic[3] != 'S') {
                throw new InvalidSkinException(shown + " is not an Ogg Vorbis file");
            }
        }
    }

    private static boolean isAnimationFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".animation.json");
    }

    /**
     * {@code <key>.ogg}, {@code <key><digits>.ogg} and {@code <key>_<digits>.ogg} (any case), plain first, then by
     * number.
     * {@code <key>.ogg}、{@code <key><数字>.ogg} 与 {@code <key>_<数字>.ogg}（不区分大小写），无编号的在前，其余按数字排序。
     */
    static List<Path> variants(List<Path> files, String key) {
        Pattern pattern = Pattern.compile(Pattern.quote(key) + "(?:_?(\\d{1,9}))?\\.ogg",
                Pattern.CASE_INSENSITIVE);
        record Variant(Path file, long number) {
        }
        List<Variant> matches = new ArrayList<>();
        for (Path file : files) {
            Matcher matcher = pattern.matcher(file.getFileName().toString());
            if (matcher.matches()) {
                matches.add(new Variant(file, matcher.group(1) == null ? -1L : Long.parseLong(matcher.group(1))));
            }
        }
        matches.sort(Comparator.comparingLong(Variant::number)
                .thenComparing(variant -> variant.file().getFileName().toString()));
        return matches.stream().map(Variant::file).toList();
    }
}
