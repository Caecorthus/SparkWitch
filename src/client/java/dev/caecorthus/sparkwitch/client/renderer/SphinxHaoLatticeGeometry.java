package dev.caecorthus.sparkwitch.client.renderer;

import org.joml.Vector3d;
import org.joml.Vector3fc;

/**
 * Pure geometry of the Sphinx Hao spring fist ({@link SphinxHaoFistEntityRenderer}): where the fist sits on the shot
 * ray, the scissor lattice (lazy tongs) between the muzzle and the fist's wrist, the textured boxes its slats and rivets
 * are made of, and the muzzle anchor the whole assembly starts from.
 * <p>
 * Lattice (2026-11-17, from dev-client screenshots): every full X cell stays OPEN at {@link #OPEN_ANGLE_DEGREES} from
 * the ray ({@link #CELL_LENGTH} long, pivots at {@code +/-}{@link #HALF_HEIGHT}); the number of full cells follows the
 * current span ({@code floor(span / cell)}), anchored at the fist's wrist, and ONE partial cell at the muzzle end
 * shows only the part already out of the gun: its slats run between the same pivot heights over the remaining length,
 * so its angle eases from folded (upright) to open as it emerges, and back while retracting. Cells never refold, so
 * there is no dense band at any age. Cell {@code k} spans {@code pivotX(k)..pivotX(k+1)}; slat A runs from
 * {@code (x, -h)} to {@code (x', +h)} on layer A (+side), slat B from {@code (x, +h)} to {@code (x', -h)} on layer B
 * (-side); neighbouring cells share their end pivots. 2D lattice coordinates are {@code x} along the ray from the
 * anchor and {@code y} up.
 * <p>
 * Boxes are emitted in the frame {@link ProjectileModels#faceAlong} leaves at the anchor: the ray is local -Z, the
 * lattice "up" (perpendicular to the ray, in its vertical plane) is +Y and the lattice side is +X. The same frame turns
 * the fist, so the lattice plane always matches the fist's up.
 * 狮身豪弹簧拳头（{@link SphinxHaoFistEntityRenderer}）的纯几何：拳头在射线上的位置、枪口与拳头腕部之间的剪刀架
 * （伸缩夹）、构成剪刀片与铆钉的贴图盒体，以及整个装置的枪口锚点。
 * <p>
 * 剪刀架（2026-11-17，依据开发客户端截图）：每个完整 X 单元始终以与射线成 {@link #OPEN_ANGLE_DEGREES} 的角度张开（长
 * {@link #CELL_LENGTH}，铰接位于 {@code +/-}{@link #HALF_HEIGHT}）；完整单元数随当前跨度变化（{@code floor(跨度 / 单元长)}），
 * 以拳头腕部为基准；枪口一端另有一个不完整单元，只显示已伸出枪外的部分：其剪刀片在相同的铰接高度之间跨越剩余长度，因此伸出时
 * 角度从折叠（竖直）逐渐变为张开，收回时反向。单元从不重新折叠，因此任何时刻都不会出现密集色带。第 {@code k} 个单元覆盖
 * {@code pivotX(k)..pivotX(k+1)}；A 片位于 A 层（+侧向），从 {@code (x, -h)} 到 {@code (x', +h)}；B 片位于 B 层（-侧向），
 * 从 {@code (x, +h)} 到 {@code (x', -h)}；相邻单元共用端点铰接。二维坐标中 {@code x} 为从锚点沿射线的距离，{@code y} 向上。
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

    /**
     * Pivot-to-pivot slat length 0.30, about the held gun's lattice at its 0.5 display scale (owner screenshots).
     * 剪刀片两端铰接间距 0.30，约等于手持枪械剪刀架在 0.5 display 缩放下的大小（所有者截图）。
     */
    static final double SLAT_LENGTH = 0.30D;
    /** Fixed open angle of every full cell, from the ray. / 每个完整单元与射线之间固定的张开角。 */
    static final double OPEN_ANGLE_DEGREES = 30.0D;
    /** Cell length along the ray: L cos 30 = 0.26. / 单元沿射线的长度：L cos 30 = 0.26。 */
    static final double CELL_LENGTH = SLAT_LENGTH * Math.cos(Math.toRadians(OPEN_ANGLE_DEGREES));
    /**
     * Pivot height above / below the ray: a slat rises L sin 30 = 0.15 across one cell, so pivots sit at +/-0.075.
     * 铰接在射线上下的高度：剪刀片跨越一个单元上升 L sin 30 = 0.15，因此铰接位于 +/-0.075。
     */
    static final double HALF_HEIGHT = SLAT_LENGTH * Math.sin(Math.toRadians(OPEN_ANGLE_DEGREES)) * 0.5D;
    /** 25-block range / 0.26 = 96 cells; the cap only guards odd values. / 25 格射程约 96 个单元，上限只防异常值。 */
    static final int MAX_CELLS = 128;
    /** Slat cross-section 1/16 x 1/16 (the held slat at its display scale). / 剪刀片截面 1/16 x 1/16（与手持剪刀片一致）。 */
    static final float SLAT_HALF_WIDTH = 0.5F / 16.0F;
    static final float SLAT_HALF_THICKNESS = 0.5F / 16.0F;
    /**
     * Layer centres sit at +/- this sideways: a 0.125/16 gap between the layers, so crossing slats never z-fight.
     * 两层中心位于侧向 +/- 该值：层间留 0.125/16 空隙，交叉的剪刀片永不深度冲突。
     */
    static final float LAYER_OFFSET = SLAT_HALF_THICKNESS + 0.0625F / 16.0F;
    /**
     * Rivet: 1.5/16 square in the lattice plane (the slat cross-section x 1.5), a pin through both layers sideways that
     * stands 0.125/16 proud of each, so no rivet face is coplanar with a slat face.
     * 铆钉：剪刀架平面内 1.5/16 见方（剪刀片截面 x 1.5），侧向是一根贯穿两层的销，两侧各凸出 0.125/16，因此铆钉面不会与剪刀片面共面。
     */
    static final float RIVET_HALF_SIZE = 0.75F / 16.0F;
    static final float RIVET_HALF_LENGTH = LAYER_OFFSET + SLAT_HALF_THICKNESS + 0.125F / 16.0F;

    // ---- Muzzle anchor / 枪口锚点 ----

    /** Vanilla draws the first-person hand with a fixed 70 degree FOV. / 原版第一人称手部固定使用 70 度视场角绘制。 */
    static final double HAND_FOV_DEGREES = 70.0D;
    /**
     * First person: the muzzle collar's front-face centre, model (8, 11, -3) of sphinx_hao_hand_gun.json (the same point
     * and display in the body-only sphinx_hao_hand_gun_fired.json held while the fist is out), in camera space (right,
     * up, forward) at the 70 degree hand FOV: vanilla's equip offset (0.56, -0.52, -0.72) + the firstperson_righthand
     * display (translation (1.5, 2.7, -1)/16, rotation (2, 6, 0) degrees, scale 0.5) applied to (0, 3, -11)/16. Only
     * the screen position (right/forward, up/forward) matters; the forward depth puts the anchor at the drawn gun at
     * FOV 70. Recompute here whenever the display block changes.
     * 第一人称：sphinx_hao_hand_gun.json 中枪口环前表面中心（模型 (8, 11, -3)；拳头在外时手持的只有枪身的
     * sphinx_hao_hand_gun_fired.json 中该点与 display 完全相同）在 70 度手部视场角下的相机空间坐标（右、上、前）：
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
     * One frame of the lattice over the current span: {@code fullCells} open cells next to the wrist and a
     * {@code partial} cell (0 <= partial < cell, longer only past the cap) at the muzzle.
     * 当前跨度上的剪刀架一帧：靠近腕部的 {@code fullCells} 个张开单元，以及枪口处的不完整单元 {@code partial}
     * （0 <= partial < 单元长，仅在超过上限时更长）。
     */
    record Lattice(double span, int fullCells, double partial) {
        static Lattice of(double span) {
            double length = finiteOrZero(span);
            int cells = (int) Math.min(MAX_CELLS, Math.floor(length / CELL_LENGTH));
            return new Lattice(length, cells, length - cells * CELL_LENGTH);
        }

        /** Full-cell pivot column {@code 0..fullCells}; column 0 is the partial cell's far end. / 完整单元的铰接列。 */
        double pivotX(int pivot) {
            return partial + pivot * CELL_LENGTH;
        }

        /** Where the two slats of full cell {@code cell} cross (at y = 0). / 完整单元两片剪刀片的交叉点（y = 0）。 */
        double crossingX(int cell) {
            return partial + (cell + 0.5D) * CELL_LENGTH;
        }

        boolean hasPartial() {
            return partial > MIN_SLAT_LENGTH;
        }

        /**
         * The partial cell's slat angle from the ray: 90 degrees (folded) at the muzzle, {@link #OPEN_ANGLE_DEGREES}
         * once a whole cell is out. / 不完整单元剪刀片与射线的夹角：在枪口处为 90 度（折叠），伸出完整一格后为张开角。
         */
        double partialAngleDegrees() {
            return Math.toDegrees(Math.atan2(2.0D * HALF_HEIGHT, partial));
        }

        /** Slat A rises from the bottom pivot, slat B falls from the top one. / A 片从下铰接升起，B 片从上铰接落下。 */
        static double slatStartY(boolean layerA) {
            return layerA ? -HALF_HEIGHT : HALF_HEIGHT;
        }

        static double slatEndY(boolean layerA) {
            return -slatStartY(layerA);
        }

        /**
         * The partial cell at the muzzle: both slats over {@code 0..partial} between the shared pivot heights, its
         * crossing rivet and the two muzzle pivots; nothing while it is empty.
         * 枪口处的不完整单元：在共用铰接高度之间跨越 {@code 0..partial} 的两片剪刀片、交叉铆钉与枪口处两个铰接；为空时不输出。
         */
        void emitPartial(BoxSink slats, BoxSink rivets) {
            if (!hasPartial()) {
                return;
            }
            emitSlat(0.0D, slatStartY(true), partial, slatEndY(true), LAYER_OFFSET, slats);
            emitSlat(0.0D, slatStartY(false), partial, slatEndY(false), -LAYER_OFFSET, slats);
            emitRivet(partial * 0.5D, 0.0D, rivets);
            emitRivet(0.0D, HALF_HEIGHT, rivets);
            emitRivet(0.0D, -HALF_HEIGHT, rivets);
        }

        /** Both slats of full cell {@code cell} and the rivet where they cross. / 完整单元的两片剪刀片及交叉铆钉。 */
        void emitCell(int cell, BoxSink slats, BoxSink rivets) {
            double x0 = pivotX(cell);
            double x1 = pivotX(cell + 1);
            emitSlat(x0, slatStartY(true), x1, slatEndY(true), LAYER_OFFSET, slats);
            emitSlat(x0, slatStartY(false), x1, slatEndY(false), -LAYER_OFFSET, slats);
            emitRivet(crossingX(cell), 0.0D, rivets);
        }

        /** Top and bottom rivets of full-cell pivot column {@code pivot}. / 完整单元第 {@code pivot} 列的上下铆钉。 */
        void emitPivotRivets(int pivot, BoxSink rivets) {
            emitRivet(pivotX(pivot), HALF_HEIGHT, rivets);
            emitRivet(pivotX(pivot), -HALF_HEIGHT, rivets);
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

    /** An axis-aligned rivet pin through both layers. / 贯穿两层的轴对齐铆钉销。 */
    static void emitRivet(double x, double y, BoxSink sink) {
        emitBox(x, y, 0.0D,
                RIVET_HALF_SIZE, 0.0D, 0.0D,
                0.0D, RIVET_HALF_SIZE, 0.0D,
                0.0D, 0.0D, RIVET_HALF_LENGTH,
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
