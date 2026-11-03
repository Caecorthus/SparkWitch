package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

/**
 * The preview stage's yaw: a slow auto-spin that a mouse drag (or the arrow keys) takes over. While dragging, the yaw
 * follows the mouse; after release it holds for {@link #RESUME_DELAY_MS}, then spins on from the dragged angle. Pure:
 * the caller passes the time in milliseconds.
 * 预览舞台的偏航角：缓慢自动旋转，可由鼠标拖动（或方向键）接管。拖动时偏航角跟随鼠标；松开后保持
 * {@link #RESUME_DELAY_MS}，然后从拖动后的角度继续旋转。纯逻辑：时间（毫秒）由调用方传入。
 */
public final class MiniWitherPreviewSpin {
    /** One full auto turn, as {@code MiniWitherSkinPreview}'s own spin. / 自动转一整圈的时长。 */
    public static final long TURN_MS = 12_000L;
    public static final long RESUME_DELAY_MS = 2_000L;
    public static final float DEGREES_PER_PIXEL = 1.2F;
    public static final float KEY_STEP_DEGREES = 15.0F;

    private float baseYaw;
    private long spinFrom;
    private boolean dragging;
    private float heldYaw;
    private float dragStartYaw;
    private double dragStartX;

    public MiniWitherPreviewSpin(float initialYaw, long now) {
        this.baseYaw = wrap(initialYaw);
        this.spinFrom = now;
    }

    /** The yaw to draw at {@code now}, in [0, 360). / {@code now} 时刻应绘制的偏航角，范围 [0, 360)。 */
    public float yaw(long now) {
        if (dragging) {
            return heldYaw;
        }
        if (now <= spinFrom) {
            return baseYaw;
        }
        return wrap(baseYaw + (float) ((now - spinFrom) % TURN_MS) * 360.0F / TURN_MS);
    }

    public boolean dragging() {
        return dragging;
    }

    public void press(long now, double mouseX) {
        heldYaw = yaw(now);
        dragStartYaw = heldYaw;
        dragStartX = mouseX;
        dragging = true;
    }

    public void drag(double mouseX) {
        if (dragging) {
            heldYaw = wrap(dragStartYaw + (float) (mouseX - dragStartX) * DEGREES_PER_PIXEL);
        }
    }

    public void release(long now) {
        if (dragging) {
            dragging = false;
            hold(heldYaw, now);
        }
    }

    /** Turns by {@code degrees} at once and holds there, as a release would. / 立即转动并保持，与松开时相同。 */
    public void nudge(long now, float degrees) {
        if (!dragging) {
            hold(yaw(now) + degrees, now);
        }
    }

    private void hold(float yaw, long now) {
        baseYaw = wrap(yaw);
        spinFrom = now + RESUME_DELAY_MS;
    }

    static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }
}
