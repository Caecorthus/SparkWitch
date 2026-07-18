package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

/** Pure distance, highlight, and timer presentation rules for Vendetta. / 仇杀客距离、描边与计时显示的纯规则。 */
public final class VendettaPresentationRules {
    public static final int KILLER_HIGHLIGHT_COLOR = 0xFF0000;
    public static final int VENDETTA_HIGHLIGHT_COLOR = 0xFF8C00;
    public static final double FULL_DESATURATION_DISTANCE = 1.0D;
    public static final double BASE_DESATURATION_DISTANCE = 15.0D;
    public static final double PROXIMITY_HIGHLIGHT_DISTANCE = 4.0D;
    public static final float BASE_DESATURATION = 0.50F;
    public static final float FULL_DESATURATION = 1.0F;

    private VendettaPresentationRules() {
    }

    public static float desaturation(double distance) {
        if (!Double.isFinite(distance) || distance >= BASE_DESATURATION_DISTANCE) {
            return BASE_DESATURATION;
        }
        if (distance <= FULL_DESATURATION_DISTANCE) {
            return FULL_DESATURATION;
        }
        double progress = (BASE_DESATURATION_DISTANCE - distance)
                / (BASE_DESATURATION_DISTANCE - FULL_DESATURATION_DISTANCE);
        return (float) (BASE_DESATURATION
                + progress * (FULL_DESATURATION - BASE_DESATURATION));
    }

    public static boolean shouldHighlightKiller(double distance, int revealActiveTicks) {
        return revealActiveTicks > 0 || distance <= PROXIMITY_HIGHLIGHT_DISTANCE;
    }

    public static boolean canSeeKnifeEquipment(
            boolean ownerView,
            boolean boundKillerView,
            boolean spectator
    ) {
        return ownerView || boundKillerView && !spectator;
    }

    public static int secondsRemaining(int ticks) {
        return Math.max(0, (int) Math.ceil(ticks / 20.0D));
    }
}
