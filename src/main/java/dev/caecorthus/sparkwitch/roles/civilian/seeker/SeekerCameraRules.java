package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Pure multi-camera selection rules, shared by the server (which camera an open request without a target views) and
 * the owner's client (which camera the A/D keys cycle to, and the "2/3" position among the cameras they can reach). The predicates carry every world
 * fact, so the order and wrap-around are testable without a game. The server still validates every chosen id.
 * 纯多摄像头选择规则，由服务端（未指定目标的打开请求观看哪台摄像头）与拥有者客户端（A/D 键切换到哪台，以及“2/3”位置）
 * 共用（位置只在按键可到达的摄像头中计算）。所有世界事实都由谓词提供，因此顺序与循环可在无游戏环境下测试。服务端仍会校验每个选中的 id。
 */
public final class SeekerCameraRules {
    public static final int NO_CAMERA = -1;

    private SeekerCameraRules() {
    }

    /**
     * Default open target: the last-viewed camera while it is still one of the owner's cameras and usable, otherwise
     * the lowest-label usable camera; {@link #NO_CAMERA} when none is usable.
     * 默认打开目标：最近观看的摄像头（仍属于拥有者且可用时），否则为编号最小的可用摄像头；都不可用时为 {@link #NO_CAMERA}。
     */
    public static int defaultCamera(List<SeekerState.Camera> cameras, int lastViewedId, IntPredicate usable) {
        List<SeekerState.Camera> ordered = byLabel(cameras);
        if (lastViewedId >= 0) {
            for (SeekerState.Camera camera : ordered) {
                if (camera.entityId() == lastViewedId && usable.test(camera.entityId())) {
                    return lastViewedId;
                }
            }
        }
        for (SeekerState.Camera camera : ordered) {
            if (usable.test(camera.entityId())) {
                return camera.entityId();
            }
        }
        return NO_CAMERA;
    }

    /**
     * The next ({@code direction > 0}) or previous ({@code direction < 0}) selectable camera by label, wrapping around
     * and skipping the current one; {@link #NO_CAMERA} for direction 0 or when no other camera is selectable. An
     * unknown {@code currentId} starts before the first label (next) or after the last one (previous).
     * 按编号取下一台（{@code direction > 0}）或上一台（{@code direction < 0}）可选摄像头，循环并跳过当前摄像头；
     * 方向为 0 或没有其他可选摄像头时为 {@link #NO_CAMERA}。未知的 {@code currentId} 视为位于第一个编号之前（下一台）
     * 或最后一个编号之后（上一台）。
     */
    public static int cycle(List<SeekerState.Camera> cameras, int currentId, int direction, IntPredicate selectable) {
        if (direction == 0 || cameras.isEmpty()) {
            return NO_CAMERA;
        }
        List<SeekerState.Camera> ordered = byLabel(cameras);
        int size = ordered.size();
        int start = indexOf(ordered, currentId);
        int step = direction > 0 ? 1 : -1;
        if (start < 0) {
            start = step > 0 ? -1 : size;
        }
        for (int offset = 1; offset <= size; offset++) {
            int index = Math.floorMod(start + step * offset, size);
            int candidate = ordered.get(index).entityId();
            if (candidate != currentId && selectable.test(candidate)) {
                return candidate;
            }
        }
        return NO_CAMERA;
    }

    /**
     * The cameras the A/D keys can reach from {@code currentId}, by label: the current camera plus every other
     * selectable one (the same skip rule as {@link #cycle}). The "2/3" hint counts this list, so cameras the keys would
     * skip are never counted.
     * 从 {@code currentId} 出发 A/D 键可到达的摄像头（按编号）：当前摄像头加上其余所有可选摄像头（与 {@link #cycle}
     * 相同的跳过规则）。“2/3”提示按此列表计数，因此按键会跳过的摄像头不计入。
     */
    public static List<SeekerState.Camera> cycleRing(List<SeekerState.Camera> cameras, int currentId,
                                                     IntPredicate selectable) {
        List<SeekerState.Camera> ring = new ArrayList<>();
        for (SeekerState.Camera camera : byLabel(cameras)) {
            if (camera.entityId() == currentId || selectable.test(camera.entityId())) {
                ring.add(camera);
            }
        }
        return ring;
    }

    /** 1-based position by label ("2" of "2/3"); 0 when the id is not one of the cameras. / 按编号的 1 起位置。 */
    public static int position(List<SeekerState.Camera> cameras, int entityId) {
        int index = indexOf(byLabel(cameras), entityId);
        return index < 0 ? 0 : index + 1;
    }

    /** The label of that camera, or {@link #NO_CAMERA}. / 该摄像头的编号，或 {@link #NO_CAMERA}。 */
    public static int label(List<SeekerState.Camera> cameras, int entityId) {
        for (SeekerState.Camera camera : cameras) {
            if (camera.entityId() == entityId) {
                return camera.label();
            }
        }
        return NO_CAMERA;
    }

    private static int indexOf(List<SeekerState.Camera> ordered, int entityId) {
        if (entityId < 0) {
            return -1;
        }
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).entityId() == entityId) {
                return index;
            }
        }
        return -1;
    }

    private static List<SeekerState.Camera> byLabel(List<SeekerState.Camera> cameras) {
        List<SeekerState.Camera> ordered = new ArrayList<>(cameras);
        ordered.sort(Comparator.comparingInt(SeekerState.Camera::label));
        return ordered;
    }
}
