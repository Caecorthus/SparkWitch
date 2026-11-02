package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure rules that bind a loaded model's animation and bone names to the Mini Wither: which animations each
 * {@link MiniWitherAnimationState} plays and which bone aims. Names a skin asks for but lacks are warnings, never
 * errors.
 * 把已加载模型的动画名与骨骼名绑定到小凋灵的纯规则：每个 {@link MiniWitherAnimationState} 播放哪些动画、哪根骨骼负责瞄准。
 * 皮肤请求但不存在的名字只算警告，绝不算错误。
 */
public final class MiniWitherSkinStates {
    /** Default aim bones, first match wins. / 默认瞄准骨骼，取第一个存在的。 */
    public static final List<String> DEFAULT_HEAD_BONES = List.of("Head", "head", "AllHead", "bone_head");

    private MiniWitherSkinStates() {
    }

    /**
     * The state an animation plays for by default: its last name segment after {@code .}, lower-cased
     * ({@code animation.cirno.walk} is {@link MiniWitherAnimationState#MOVE}); null for any other name.
     * 动画的默认状态：取名字最后一个 {@code .} 之后的部分并转小写；其它名字返回 null。
     */
    @Nullable
    public static MiniWitherAnimationState stateOf(String animationName) {
        String last = animationName.substring(animationName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (last) {
            case "idle" -> MiniWitherAnimationState.IDLE;
            case "move", "walk", "fly" -> MiniWitherAnimationState.MOVE;
            case "summon", "spawn", "appear" -> MiniWitherAnimationState.SUMMON;
            case "shoot", "attack" -> MiniWitherAnimationState.SHOOT;
            case "crimson" -> MiniWitherAnimationState.CRIMSON;
            default -> null;
        };
    }

    /**
     * Every state's animations, in order: a state named in {@code explicit} ({@code skin.json}) keeps only its names
     * the model has (exact, then ignoring case; missing ones become warnings); every other state takes the
     * {@code available} names {@link #stateOf} maps to it, in file order. The result has every state (possibly empty)
     * and never falls CRIMSON back to SHOOT: the animator does that.
     * 每种状态的动画（按顺序）：{@code explicit}（skin.json）中指定的状态只保留模型中存在的名字（先精确再忽略大小写；缺失的
     * 记为警告）；其余状态取 {@code available} 中经 {@link #stateOf} 映射到它的名字，按文件顺序。结果包含所有状态（可能为空），
     * 且不会把 CRIMSON 回退到 SHOOT：由动画器负责。
     *
     * @param where message prefix, e.g. {@code "skull "} / 消息前缀
     */
    public static Map<MiniWitherAnimationState, List<String>> resolve(
            Map<MiniWitherAnimationState, List<String>> explicit, Collection<String> available, String where,
            List<String> warnings) {
        Map<MiniWitherAnimationState, List<String>> states = new EnumMap<>(MiniWitherAnimationState.class);
        for (MiniWitherAnimationState state : MiniWitherAnimationState.values()) {
            List<String> names = new ArrayList<>();
            List<String> asked = explicit.get(state);
            if (asked != null) {
                for (String name : asked) {
                    String found = find(available, name);
                    if (found == null) {
                        warnings.add(where + "states." + state.key() + ": no animation named \"" + name + "\"");
                    } else if (!names.contains(found)) {
                        names.add(found);
                    }
                }
            } else {
                for (String name : available) {
                    if (stateOf(name) == state) {
                        names.add(name);
                    }
                }
            }
            states.put(state, List.copyOf(names));
        }
        return Collections.unmodifiableMap(states);
    }

    /**
     * The aim bone: the {@code head} key when set (missing: a warning and -1), otherwise the first of
     * {@link #DEFAULT_HEAD_BONES} the geometry has, otherwise -1.
     * 瞄准骨骼：设置了 {@code head} 键时取它（不存在则警告并返回 -1），否则取 {@link #DEFAULT_HEAD_BONES} 中几何拥有的第一个，
     * 否则为 -1。
     */
    public static int headBone(ToIntFunction<String> boneIndex, @Nullable String head, List<String> warnings) {
        if (head != null) {
            int bone = boneIndex.applyAsInt(head);
            if (bone < 0) {
                warnings.add("head: no bone named \"" + head + "\"");
            }
            return Math.max(bone, -1);
        }
        for (String name : DEFAULT_HEAD_BONES) {
            int bone = boneIndex.applyAsInt(name);
            if (bone >= 0) {
                return bone;
            }
        }
        return -1;
    }

    @Nullable
    private static String find(Collection<String> available, String name) {
        if (available.contains(name)) {
            return name;
        }
        for (String candidate : available) {
            if (candidate.equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }
}
