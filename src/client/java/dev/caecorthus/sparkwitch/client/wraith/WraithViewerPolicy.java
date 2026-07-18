package dev.caecorthus.sparkwitch.client.wraith;

/** Pure viewer-local visibility policy for an active Wraith target. / 激活冤魂目标的纯客户端观察者可见性规则。 */
public final class WraithViewerPolicy {
    public enum Presentation {
        ANONYMOUS_PEER,
        HIDDEN,
        REAL
    }

    private WraithViewerPolicy() {
    }

    public static Presentation presentation(
            boolean targetWraithActive,
            boolean samePlayer,
            boolean viewerWraithActive,
            boolean viewerPlayingAndAlive
    ) {
        if (!targetWraithActive || samePlayer) {
            return Presentation.REAL;
        }
        if (viewerWraithActive) {
            return Presentation.ANONYMOUS_PEER;
        }
        return viewerPlayingAndAlive ? Presentation.HIDDEN : Presentation.REAL;
    }
}
