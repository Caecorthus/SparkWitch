package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A skin folder resolved by {@link MiniWitherSkinFolder#resolve}: every default and safety rule already applied, no
 * file parsed beyond what discovery needs. Every path is a real path inside the skin folder that existed and passed the
 * size limits when resolved.
 * 由 {@link MiniWitherSkinFolder#resolve} 解析出的皮肤文件夹：所有默认值与安全规则均已应用，除发现文件所需外不解析任何
 * 文件。每个路径都是皮肤文件夹内的真实路径，解析时已存在且通过了大小限制。
 *
 * @param folder   the skin folder's real path; every other path is inside it / 皮肤文件夹的真实路径；其余路径都在其中
 * @param body     null keeps the vanilla Wither (a sounds-only skin) / null 时保留原版凋灵（仅声音的皮肤）
 * @param skull    null keeps the vanilla skull / null 时保留原版头颅
 * @param sounds   only slots with at least one file / 只包含至少有一个文件的槽位
 * @param warnings non-fatal problems in plain English, for the log / 非致命问题（英文），写入日志
 */
public record MiniWitherSkinSpec(String id, String displayName, Path folder, @Nullable Model body,
                                 @Nullable Model skull, Map<MiniWitherSoundSlot, List<Path>> sounds,
                                 List<String> warnings) {
    public MiniWitherSkinSpec {
        sounds = Map.copyOf(sounds);
        warnings = List.copyOf(warnings);
    }

    /** {@code folder}-relative, forward slashes, for messages. / 相对 folder、使用正斜杠的路径，用于消息。 */
    public String shown(Path file) {
        Path relative = file.startsWith(folder) ? folder.relativize(file) : file.getFileName();
        return relative.toString().replace('\\', '/');
    }

    public int soundCount() {
        int count = 0;
        for (List<Path> files : sounds.values()) {
            count += files.size();
        }
        return count;
    }

    /**
     * One model of the skin, still on disk.
     * 皮肤中的一个模型，仍在磁盘上。
     *
     * @param geometry       {@code description.identifier} to take, null for the first / 选用的几何标识，null 取第一个
     * @param blessedTexture skull only / 仅头颅
     * @param crimsonTexture skull only / 仅头颅
     * @param states         only the states {@code skin.json} names; every other state is mapped from the animation
     *                       names at load ({@link MiniWitherSkinStates#resolve}) / 只含 skin.json 指定的状态；其余状态在
     *                       加载时按动画名映射
     * @param head           the {@code head} key; null searches the default names (body only) / head 键；null 时按默认
     *                       名字查找（仅本体）
     */
    public record Model(Path model, @Nullable String geometry, Path texture, @Nullable Path blessedTexture,
                        @Nullable Path crimsonTexture, List<Path> animations,
                        Map<MiniWitherAnimationState, List<String>> states, @Nullable String head,
                        String glowPrefix, boolean translucent) {
        public Model {
            animations = List.copyOf(animations);
            states = Map.copyOf(states);
        }
    }
}
