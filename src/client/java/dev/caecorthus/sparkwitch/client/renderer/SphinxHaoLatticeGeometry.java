package dev.caecorthus.sparkwitch.client.renderer;

import org.joml.Vector3d;
import org.joml.Vector3fc;

/**
 * Pure geometry of the Sphinx Hao spring fist ({@link SphinxHaoFistEntityRenderer}): where the fist sits on the shot
 * ray, the scissor lattice (lazy tongs) between the muzzle and the fist's wrist, the textured boxes its slats and rivets
 * are made of, and the muzzle anchor the whole assembly starts from.
 * <p>
 * Lattice: a chain of X cells with a CONSTANT slat length {@link #SLAT_LENGTH} and a cell count fixed for the whole
 * shot ({@link #cellCount}), so as the span grows the cells lengthen and the zigzag flattens ("opens"), and folds back
 * as it shrinks. Cell {@code i} spans {@code x_i..x_{i+1}}; slat A runs from {@code (x_i, -h)} to {@code (x_{i+1}, +h)}
 * on layer A (+side), slat B from {@code (x_i, +h)} to {@code (x_{i+1}, -h)} on layer B (-side); neighbouring cells
 * share their end pivots. 2D lattice coordinates are {@code x} along the ray from the anchor and {@code y} up.
 * <p>
 * Boxes are emitted in the frame {@link ProjectileModels#faceAlong} leaves at the anchor: the ray is local -Z, the
 * lattice "up" (perpendicular to the ray, in its vertical plane) is +Y and the lattice side is +X. The same frame turns
 * the fist, so the lattice plane always matches the fist's up.
 * 狮身豪弹簧拳头（{@link SphinxHaoFistEntityRenderer}）的纯几何：拳头在射线上的位置、枪口与拳头腕部之间的剪刀架
 * （伸缩夹）、构成剪刀片与铆钉的贴图盒体，以及整个装置的枪口锚点。
 * <p>
 * 剪刀架：一串 X 形单元，剪刀片长度恒为 {@link #SLAT_LENGTH}，单元数在整次射击中固定（{@link #cellCount}），因此跨度增大时
 * 单元沿轴拉长、锯齿变平（“张开”），跨度缩小时折回。第 {@code i} 个单元覆盖 {@code x_i..x_{i+1}}；A 片位于 A 层（+侧向），
 * 从 {@code (x_i, -h)} 到 {@code (x_{i+1}, +h)}；B 片位于 B 层（-侧向），从 {@code (x_i, +h)} 到 {@code (x_{i+1}, -h)}；
 * 相邻单元共用端点铰接。二维坐标中 {@code x} 为从锚点沿射线的距离，{@code y} 向上。
 * <p>
 * 盒体在 {@link ProjectileModels#faceAlong} 于锚点处留下的坐标系中输出：射线为局部 -Z，剪刀架“上方”（垂直于射线、位于其
 * 竖直平面内）为 +Y，侧向为 +X。拳头也用同一坐标系转向，因此剪刀架平面始终与拳头的上方一致。
 */
final class SphinxHaoLatticeGeometry {
    // ---- Fist model contract (sphinx_hao_fist.json) / 拳头模型约定 ----

    /**
     * Knuckle face (z = 5) is 3/16 ahead of the model pivot (8, 8, 8); the wrist rear (z = 11) 3/16 behind it. Both are
     * multiplied by the renderer's fist scale.
     * 指节前面（z = 5）位于模型枢轴 (8, 8, 8) 前方 3/16，腕部后面（z = 11）位于其后方 3/16。两者都乘以渲染器的拳头缩放。
     */
    static final double FIST_KNUCKLE_AHEAD = 3.0D / 16.0D;
    static final double FIST_WRIST_BEHIND = 3.0D / 16.0D;

    // ---- Lattice / 剪刀架 ----

    /** Pivot-to-pivot slat length, constant at any extension. / 剪刀片两端铰接间距，任何伸展量下都不变。 */
    static final double SLAT_LENGTH = 0.45D;
    /**
     * Fully extended, a cell is at most this fraction of a slat long, so the slats always keep an angle.
     * 完全伸出时单元长度至多为剪刀片长度的这个比例，剪刀片始终保持夹角。
     */
    static final double OPEN_LIMIT = 0.85D;
    /** 25-block range / (0.45 x 0.85) = 66 cells; the cap only guards odd values. / 25 格射程约 66 个单元，上限只防异常值。 */
    static final int MAX_CELLS = 128;
    /**
     * The cell count comes from the eye-to-target distance (fixed per shot, the entity never moves) minus this nominal
     * eye-to-muzzle reach, so it never changes frame to frame with the moving muzzle.
     * 单元数取自眼睛到终点的距离（每次射击固定，实体不会移动）减去这一名义的眼睛到枪口距离，因此不会随移动的枪口逐帧变化。
     */
    static final double NOMINAL_MUZZLE_REACH = 1.0D;
    /** In-plane slat width 1/16. / 剪刀片面内宽度 1/16。 */
    static final float SLAT_HALF_WIDTH = 0.5F / 16.0F;
    /** Sideways slat thickness 0.5/16 (flat plastic strips). / 剪刀片侧向厚度 0.5/16（扁平塑料条）。 */
    static final float SLAT_HALF_THICKNESS = 0.25F / 16.0F;
    /**
     * Layer centres sit at +/- this sideways: a 0.125/16 gap between the layers, so crossing slats never z-fight.
     * 两层中心位于侧向 +/- 该值：层间留 0.125/16 空隙，交叉的剪刀片永不深度冲突。
     */
    static final float LAYER_OFFSET = SLAT_HALF_THICKNESS + 0.0625F / 16.0F;
    /**
     * Rivet cube 1.5/16: wider than a slat and than both layers together, so no rivet face is coplanar with a slat face.
     * 铆钉立方体 1.5/16：比剪刀片宽、也比两层合起来厚，因此铆钉面不会与剪刀片面共面。
     */
    static final float RIVET_HALF_SIZE = 0.75F / 16.0F;

    // ---- Muzzle anchor / 枪口锚点 ----

    /** Vanilla draws the first-person hand with a fixed 70 degree FOV. / 原版第一人称手部固定使用 70 度视场角绘制。 */
    static final double HAND_FOV_DEGREES = 70.0D;
    /**
     * First person: the muzzle collar centre, model (8, 11, -3) of sphinx_hao_hand_gun.json, in camera space (right,
     * up, forward) at the 70 degree hand FOV: vanilla's equip offset (0.56, -0.52, -0.72) + the firstperson_righthand
     * display (translation (1.5, 2.7, -1)/16, rotation (2, 6, 0) degrees, scale 0.5) applied to (0, 3, -11)/16. Only
     * the screen position (right/forward, up/forward) matters; the forward depth puts the anchor at the drawn gun at
     * FOV 70. Recompute here whenever the display block changes.
     * 第一人称：sphinx_hao_hand_gun.json 中枪口环中心（模型 (8, 11, -3)）在 70 度手部视场角下的相机空间坐标（右、上、前）：
     * 原版装备偏移 (0.56, -0.52, -0.72) 加上 firstperson_righthand 显示变换（平移 (1.5, 2.7, -1)/16、旋转 (2, 6, 0) 度、
     * 缩放 0.5）作用于 (0, 3, -11)/16。真正起作用的是屏幕位置（右/前、上/前）；前向深度使 FOV 70 时锚点恰在所绘手枪上。
     * display 块变化时须在此重新计算。
     */
    static final double FP_MUZZLE_RIGHT = 0.618D;
    static final double FP_MUZZLE_UP = -0.246D;
    static final double FP_MUZZLE_FORWARD = 1.121D;
    /**
     * Third person, Wathe's straight-arm gun hold (arm pitch = look pitch + 0.1 rad down, arm yaw = look yaw turned
     * 0.3 rad inward): the shoulder pivot sits 5/16 to the side and 22/16 above the feet, i.e. 0.33 below the 1.62 eye.
     * The muzzle in the hand's item frame is vanilla's item offset (1/16, 2/16, -10/16) + the thirdperson_righthand
     * display (translation (0, -0.6, 2)/16, rotation (5.7, -17.2, 0) degrees, scale 0.5) applied to (0, 3, -11)/16:
     * 0.82 along the arm, 0.21 up from it and 0.16 to the arm's own right (the display yaw cancels the arm's inward
     * yaw, so the barrel follows the look). Model sizes x 0.9375 (player model scale), then x the owner's scale.
     * 第三人称，Wathe 平举持枪（手臂俯仰 = 视线俯仰再下压 0.1 弧度，手臂偏航 = 视线偏航向内转 0.3 弧度）：肩部枢轴位于侧向
     * 5/16、脚上 22/16，即比 1.62 的眼睛低 0.33。枪口在手部物品坐标系中为原版物品偏移 (1/16, 2/16, -10/16) 加上
     * thirdperson_righthand 显示变换（平移 (0, -0.6, 2)/16、旋转 (5.7, -17.2, 0) 度、缩放 0.5）作用于 (0, 3, -11)/16：
     * 沿手臂 0.82、垂直手臂向上 0.21、朝手臂自身右方 0.16（display 偏航抵消了手臂的向内偏航，枪管因此沿视线方向）。
     * 模型尺寸乘以 0.9375（玩家模型缩放），再乘以玩家自身缩放。
     */
    static final double TP_SHOULDER_SIDE = 0.293D;
    static final double TP_SHOULDER_DROP = 0.33D;
    static final double TP_ARM_INWARD_YAW = 0.3D;
    static final double TP_ARM_DROP_PITCH = 0.1D;
    static final double TP_MUZZLE_REACH = 0.766D;
    static final double TP_MUZZLE_LIFT = 0.200D;
    /** Toward the arm's own right (the other arm mirrors). / 朝手臂自身右方（另一只手臂镜像）。 */
    static final double TP_MUZZLE_SIDE = 0.154D;
    /**
     * A target closer than the muzzle (point-blank wall or player) pulls the anchor back toward the eye, keeping this
     * much room for the fist; in first person the pull keeps the anchor's screen position.
     * 终点比枪口还近（贴脸的墙或玩家）时，把锚点朝眼睛拉回，为拳头保留这么长的距离；第一人称下拉回不改变锚点的屏幕位置。
     */
    static final double ANCHOR_TARGET_CLEARANCE = 0.3D;

    private static final double MIN_SLAT_LENGTH = 1.0E-6D;
    private static final double MIN_OFFSET = 1.0E-6D;

    private SphinxHaoLatticeGeometry() {
    }

    /** Corner sink of {@link #emitBox}. / {@link #emitBox} 的顶点接收器。 */
    @FunctionalInterface
    interface BoxSink {
        /**
         * One face corner in the faceAlong frame; corners {@code 0..3} of a face run counter-clockwise seen from outside
         * (OpenGL front faces), {@code (nx, ny, nz)} is the face's outward unit normal.
         * faceAlong 坐标系中的一个面顶点；每个面的 {@code 0..3} 号顶点从外侧看为逆时针（OpenGL 正面），{@code (nx, ny, nz)}
         * 为该面向外的单位法线。
         */
        void corner(float x, float y, float z, float nx, float ny, float nz, int index);
    }

    // ---- Fist placement / 拳头位置 ----

    /** Knuckle front to wrist rear. / 指节前面到腕部后面的长度。 */
    static double fistLength(double scale) {
        return (FIST_KNUCKLE_AHEAD + FIST_WRIST_BEHIND) * scale;
    }

    /**
     * Distance along the ray from the anchor to the fist pivot when the knuckles are {@code knuckleDistance} out.
     * 指节伸出 {@code knuckleDistance} 时，从锚点沿射线到拳头枢轴的距离。
     */
    static double fistPivotDistance(double knuckleDistance, double scale) {
        return finiteOrZero(knuckleDistance) - FIST_KNUCKLE_AHEAD * scale;
    }

    /**
     * Lattice span: anchor to the wrist rear, never negative; 0 for NaN or infinite input.
     * 剪刀架跨度：锚点到腕部后面，不为负；NaN 或无穷输入为 0。
     */
    static double span(double knuckleDistance, double scale) {
        return finiteOrZero(knuckleDistance - fistLength(scale));
    }

    /**
     * Full-extension span for the cell count, from the per-shot eye-to-target distance.
     * 用于单元数的完全伸出跨度，取自每次射击固定的眼睛到终点距离。
     */
    static double fullSpanEstimate(double eyeToTarget, double scale) {
        return span(eyeToTarget - NOMINAL_MUZZLE_REACH, scale);
    }

    /**
     * Cells for a shot: {@code max(1, ceil(fullSpan / (L x 0.85)))}, capped at {@link #MAX_CELLS}; 1 for 0 or NaN.
     * 一次射击的单元数：{@code max(1, ceil(fullSpan / (L x 0.85)))}，上限 {@link #MAX_CELLS}；0 或 NaN 时为 1。
     */
    static int cellCount(double fullSpan) {
        if (!(fullSpan > 0.0D)) {
            return 1;
        }
        double cells = Math.ceil(fullSpan / (SLAT_LENGTH * OPEN_LIMIT));
        return cells >= MAX_CELLS ? MAX_CELLS : Math.max(1, (int) cells);
    }

    /**
     * Half height of a cell with pitch {@code c}: {@code sqrt(max(0, L^2 - c^2)) / 2}; L/2 for 0 or NaN.
     * 单元长度为 {@code c} 时的半高：{@code sqrt(max(0, L^2 - c^2)) / 2}；0 或 NaN 时为 L/2。
     */
    static double halfHeight(double pitch) {
        double c = pitch > 0.0D ? pitch : 0.0D;
        double rest = SLAT_LENGTH * SLAT_LENGTH - c * c;
        return rest > 0.0D ? Math.sqrt(rest) * 0.5D : 0.0D;
    }

    /**
     * One frame of the lattice: {@code cells} cells over the current span.
     * 剪刀架的一帧：当前跨度上的 {@code cells} 个单元。
     */
    record Lattice(int cells, double pitch, double halfHeight) {
        static Lattice of(double span, int cells) {
            int count = Math.max(1, cells);
            double pitch = finiteOrZero(span) / count;
            return new Lattice(count, pitch, SphinxHaoLatticeGeometry.halfHeight(pitch));
        }

        double span() {
            return cells * pitch;
        }

        /** End pivot column {@code 0..cells}. / 端点铰接列 {@code 0..cells}。 */
        double pivotX(int pivot) {
            return pivot * pitch;
        }

        /** Where the two slats of {@code cell} cross (at y = 0). / {@code cell} 两片剪刀片的交叉点（y = 0）。 */
        double crossingX(int cell) {
            return (cell + 0.5D) * pitch;
        }

        /** Slat A rises from the bottom pivot, slat B falls from the top one. / A 片从下铰接升起，B 片从上铰接落下。 */
        double slatStartY(boolean layerA) {
            return layerA ? -halfHeight : halfHeight;
        }

        double slatEndY(boolean layerA) {
            return -slatStartY(layerA);
        }

        /** Both slats of {@code cell} and the rivet where they cross. / {@code cell} 的两片剪刀片及其交叉处的铆钉。 */
        void emitCell(int cell, BoxSink slats, BoxSink rivets) {
            double x0 = pivotX(cell);
            double x1 = pivotX(cell + 1);
            emitSlat(x0, slatStartY(true), x1, slatEndY(true), LAYER_OFFSET, slats);
            emitSlat(x0, slatStartY(false), x1, slatEndY(false), -LAYER_OFFSET, slats);
            emitRivet(crossingX(cell), 0.0D, rivets);
        }

        /** Top and bottom rivets of pivot column {@code pivot}. / 第 {@code pivot} 列的上下铆钉。 */
        void emitPivotRivets(int pivot, BoxSink rivets) {
            emitRivet(pivotX(pivot), halfHeight, rivets);
            if (halfHeight > 0.0D) {
                emitRivet(pivotX(pivot), -halfHeight, rivets);
            }
        }
    }

    // ---- Boxes / 盒体 ----

    /**
     * A slat from {@code (x0, y0)} to {@code (x1, y1)} on the layer centred {@code side} sideways; nothing for a zero
     * length. / 位于侧向 {@code side} 层上、从 {@code (x0, y0)} 到 {@code (x1, y1)} 的剪刀片；长度为零时不输出。
     */
    static void emitSlat(double x0, double y0, double x1, double y1, double side, BoxSink sink) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > MIN_SLAT_LENGTH)) {
            return;
        }
        // Width axis = side x length axis = the length axis turned +90 degrees in the plane, so (length, width, side)
        // is right-handed. / 宽度轴 = 侧向 x 长度轴 = 长度轴在平面内转 +90 度，使（长度、宽度、侧向）为右手系。
        double wx = -dy / length * SLAT_HALF_WIDTH;
        double wy = dx / length * SLAT_HALF_WIDTH;
        emitBox((x0 + x1) * 0.5D, (y0 + y1) * 0.5D, side,
                dx * 0.5D, dy * 0.5D, 0.0D,
                wx, wy, 0.0D,
                0.0D, 0.0D, SLAT_HALF_THICKNESS,
                sink);
    }

    /** An axis-aligned rivet cube spanning both layers. / 跨越两层的轴对齐铆钉立方体。 */
    static void emitRivet(double x, double y, BoxSink sink) {
        emitBox(x, y, 0.0D,
                RIVET_HALF_SIZE, 0.0D, 0.0D,
                0.0D, RIVET_HALF_SIZE, 0.0D,
                0.0D, 0.0D, RIVET_HALF_SIZE,
                sink);
    }

    /**
     * Six faces of the box centred {@code c} with half axes {@code a, b, n} given in lattice coordinates (x along the
     * ray, y up, z side); the axes must be orthogonal and right-handed ({@code a x b} along {@code +n}). Each face with
     * outward axis P and the other two axes Q, R where {@code Q x R = P} has corners {@code c + P - Q - R},
     * {@code + Q - R}, {@code + Q + R}, {@code - Q + R}: counter-clockwise from outside. The output is mapped to the
     * faceAlong frame (side, up, -along), a proper rotation, so the winding survives.
     * 以 {@code c} 为中心、半轴 {@code a, b, n}（剪刀架坐标：x 沿射线、y 向上、z 侧向）的盒体六个面；三轴须正交且为右手系
     * （{@code a x b} 沿 {@code +n}）。外法轴为 P、另两轴 Q、R 满足 {@code Q x R = P} 的面，其顶点依次为
     * {@code c + P - Q - R}、{@code + Q - R}、{@code + Q + R}、{@code - Q + R}：从外侧看为逆时针。输出映射到 faceAlong
     * 坐标系（侧向、上、-沿射线），这是一个真旋转，绕序保持不变。
     */
    static void emitBox(
            double cx, double cy, double cz,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double nx, double ny, double nz,
            BoxSink sink
    ) {
        face(cx, cy, cz, ax, ay, az, bx, by, bz, nx, ny, nz, sink);
        face(cx, cy, cz, -ax, -ay, -az, nx, ny, nz, bx, by, bz, sink);
        face(cx, cy, cz, bx, by, bz, nx, ny, nz, ax, ay, az, sink);
        face(cx, cy, cz, -bx, -by, -bz, ax, ay, az, nx, ny, nz, sink);
        face(cx, cy, cz, nx, ny, nz, ax, ay, az, bx, by, bz, sink);
        face(cx, cy, cz, -nx, -ny, -nz, bx, by, bz, ax, ay, az, sink);
    }

    private static void face(
            double cx, double cy, double cz,
            double px, double py, double pz,
            double qx, double qy, double qz,
            double rx, double ry, double rz,
            BoxSink sink
    ) {
        double length = Math.sqrt(px * px + py * py + pz * pz);
        float normalAlong = (float) (px / length);
        float normalUp = (float) (py / length);
        float normalSide = (float) (pz / length);
        double fx = cx + px;
        double fy = cy + py;
        double fz = cz + pz;
        corner(fx - qx - rx, fy - qy - ry, fz - qz - rz, normalAlong, normalUp, normalSide, 0, sink);
        corner(fx + qx - rx, fy + qy - ry, fz + qz - rz, normalAlong, normalUp, normalSide, 1, sink);
        corner(fx + qx + rx, fy + qy + ry, fz + qz + rz, normalAlong, normalUp, normalSide, 2, sink);
        corner(fx - qx + rx, fy - qy + ry, fz - qz + rz, normalAlong, normalUp, normalSide, 3, sink);
    }

    /** Lattice (along, up, side) to faceAlong (side, up, -along). / 剪刀架（沿、上、侧）到 faceAlong（侧、上、-沿）。 */
    private static void corner(
            double along, double up, double side,
            float normalAlong, float normalUp, float normalSide,
            int index,
            BoxSink sink
    ) {
        sink.corner((float) side, (float) up, (float) -along, normalSide, normalUp, -normalAlong, index);
    }

    // ---- Muzzle anchor / 枪口锚点 ----

    /**
     * First-person muzzle offset from the camera. {@code forward}, {@code up} and {@code left} are the camera's
     * horizontal, vertical and diagonal planes; the sideways and vertical parts scale by
     * {@code tan(worldFov/2) / tan(handFov/2)}, so the anchor stays on the drawn muzzle whatever world FOV (options,
     * speed, zoom) is in use. {@code side} is +1 for the right hand, -1 for the left.
     * 第一人称枪口相对相机的偏移。{@code forward}、{@code up}、{@code left} 为相机的水平、竖直与斜向平面向量；侧向与竖直分量按
     * {@code tan(世界FOV/2) / tan(手部FOV/2)} 缩放，因此无论世界视场角（选项、速度、缩放）如何，锚点都停在所绘的枪口上。
     * {@code side} 右手为 +1，左手为 -1。
     */
    static Vector3d firstPersonMuzzleOffset(
            Vector3fc forward,
            Vector3fc up,
            Vector3fc left,
            double worldFovDegrees,
            int side
    ) {
        double ratio = fovRatio(worldFovDegrees);
        double leftAmount = -side * FP_MUZZLE_RIGHT * ratio;
        double upAmount = FP_MUZZLE_UP * ratio;
        return new Vector3d(
                forward.x() * FP_MUZZLE_FORWARD + left.x() * leftAmount + up.x() * upAmount,
                forward.y() * FP_MUZZLE_FORWARD + left.y() * leftAmount + up.y() * upAmount,
                forward.z() * FP_MUZZLE_FORWARD + left.z() * leftAmount + up.z() * upAmount
        );
    }

    /** {@code tan(worldFov/2) / tan(handFov/2)}; 1 for odd FOVs. / 视场角异常时为 1。 */
    static double fovRatio(double worldFovDegrees) {
        if (!(worldFovDegrees > 1.0D && worldFovDegrees < 179.0D)) {
            return 1.0D;
        }
        return Math.tan(Math.toRadians(worldFovDegrees) * 0.5D) / Math.tan(Math.toRadians(HAND_FOV_DEGREES) * 0.5D);
    }

    /**
     * Third-person muzzle offset from the eye for Wathe's straight-arm hold (see the TP constants). Yaws and pitch are
     * Minecraft degrees (yaw 0 faces +Z, positive pitch looks down); {@code side} is +1 for the right arm.
     * 第三人称 Wathe 平举持枪时枪口相对眼睛的偏移（见 TP 常量）。偏航与俯仰为 Minecraft 角度（偏航 0 朝 +Z，俯仰为正时向下看）；
     * {@code side} 右臂为 +1。
     */
    static Vector3d thirdPersonMuzzleOffset(
            float bodyYawDegrees,
            float headYawDegrees,
            float pitchDegrees,
            int side,
            float scale
    ) {
        double bodyYaw = Math.toRadians(bodyYawDegrees);
        // Body right for yaw 0 (facing +Z) is -X. / 偏航 0（朝 +Z）时身体右方为 -X。
        double rightX = -Math.cos(bodyYaw);
        double rightZ = -Math.sin(bodyYaw);
        // The arm turns inward (toward the other side), i.e. to the left for the right arm: a smaller yaw.
        // 手臂向内转（朝另一侧），即右臂向左：偏航变小。
        double armYaw = Math.toRadians(headYawDegrees) - side * TP_ARM_INWARD_YAW;
        double armPitch = Math.toRadians(pitchDegrees) + TP_ARM_DROP_PITCH;
        double sinYaw = Math.sin(armYaw);
        double cosYaw = Math.cos(armYaw);
        double sinPitch = Math.sin(armPitch);
        double cosPitch = Math.cos(armPitch);
        // Arm direction (Minecraft's rotation vector) and its up: perpendicular, in the arm's vertical plane.
        // 手臂方向（Minecraft 的朝向向量）及其上方：与之垂直，位于手臂的竖直平面内。
        double dirX = -sinYaw * cosPitch;
        double dirY = -sinPitch;
        double dirZ = cosYaw * cosPitch;
        double upX = -sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = cosYaw * sinPitch;
        // The arm's own right is horizontal (the hold never rolls). / 手臂自身的右方是水平的（持枪姿势从不横滚）。
        double armRightX = -cosYaw;
        double armRightZ = -sinYaw;
        double shoulder = side * TP_SHOULDER_SIDE;
        double muzzleSide = side * TP_MUZZLE_SIDE;
        return new Vector3d(
                shoulder * rightX + dirX * TP_MUZZLE_REACH + upX * TP_MUZZLE_LIFT + muzzleSide * armRightX,
                -TP_SHOULDER_DROP + dirY * TP_MUZZLE_REACH + upY * TP_MUZZLE_LIFT,
                shoulder * rightZ + dirZ * TP_MUZZLE_REACH + upZ * TP_MUZZLE_LIFT + muzzleSide * armRightZ
        ).mul(scale > 0.0F ? scale : 1.0F);
    }

    /**
     * Factor in [0, 1] applied to the eye-to-muzzle offset: 1 unless the target is closer to the eye than the muzzle
     * plus {@link #ANCHOR_TARGET_CLEARANCE}. / 作用于眼睛到枪口偏移的系数 [0, 1]：除非终点比枪口加
     * {@link #ANCHOR_TARGET_CLEARANCE} 更靠近眼睛，否则为 1。
     */
    static double anchorPull(double offsetLength, double eyeToTarget) {
        if (!(offsetLength > MIN_OFFSET)) {
            return 1.0D;
        }
        double pull = (eyeToTarget - ANCHOR_TARGET_CLEARANCE) / offsetLength;
        return pull >= 1.0D ? 1.0D : pull > 0.0D ? pull : 0.0D;
    }

    private static double finiteOrZero(double value) {
        return value > 0.0D && value < Double.POSITIVE_INFINITY ? value : 0.0D;
    }
}
