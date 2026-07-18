package dev.caecorthus.sparkwitch.client.witchmaiden;

/** Player-avatar pagination owned by Witch Maiden. / 巫女自有的玩家头像分页布局。 */
public final class FocusedFootstepsPageLayout {
    public static final int PLAYERS_PER_PAGE = 10;
    public static final int SLOT_SPACING = 36;
    public static final int BUTTON_SIZE = 16;
    private static final int CONTROL_ROW_OFFSET = 28;

    private FocusedFootstepsPageLayout() {
    }

    public static int rowY(int screenHeight) {
        return (screenHeight - 32) / 2 + 80;
    }

    public static int controlRowY(int screenHeight) {
        return rowY(screenHeight) - CONTROL_ROW_OFFSET;
    }

    public static int playerStartX(int screenWidth, int visiblePlayers) {
        return centeredStartX(screenWidth, visiblePlayers);
    }

    public static int playerX(int screenWidth, int visibleIndex, int visiblePlayers) {
        return playerStartX(screenWidth, visiblePlayers) + Math.max(0, visibleIndex) * SLOT_SPACING;
    }

    public static int controlStartX(int screenWidth, int visibleControls) {
        return centeredStartX(screenWidth, visibleControls);
    }

    private static int centeredStartX(int screenWidth, int visibleCount) {
        if (visibleCount <= 0) {
            return Math.max(0, (screenWidth - BUTTON_SIZE) / 2);
        }
        int width = BUTTON_SIZE + (visibleCount - 1) * SLOT_SPACING;
        return Math.max(0, (screenWidth - width) / 2);
    }

    public static PageWindow window(int candidateCount, int requestedPage) {
        int normalizedCount = Math.max(0, candidateCount);
        int pages = Math.max(1, (normalizedCount + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = Math.min(normalizedCount, page * PLAYERS_PER_PAGE);
        int end = Math.min(normalizedCount, start + PLAYERS_PER_PAGE);
        return new PageWindow(page, pages, start, end, page > 0, page < pages - 1);
    }

    public record PageWindow(
            int page,
            int pageCount,
            int startIndex,
            int endIndex,
            boolean showPrevious,
            boolean showNext
    ) {
        public int visibleCount() {
            return endIndex - startIndex;
        }
    }
}
