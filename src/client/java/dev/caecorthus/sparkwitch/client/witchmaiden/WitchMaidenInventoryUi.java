package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Role-owned avatar row attached by a thin mixin to Wathe's existing inventory screen.
 * 由薄 mixin 挂到 Wathe 现有背包界面的巫女自有头像栏。
 */
public final class WitchMaidenInventoryUi {
    private final LimitedInventoryScreen screen;
    private final ClientPlayerEntity player;
    private final Consumer<ClickableWidget> addChild;
    private final Map<UUID, WitchMaidenTargetWidget> targetWidgets = new LinkedHashMap<>();
    private List<WitchMaidenTargetWidget> liveWidgets = List.of();
    private final WitchMaidenPageSwitchWidget previous;
    private final WitchMaidenPageSwitchWidget next;

    private WitchMaidenInventoryUi(
            LimitedInventoryScreen screen,
            ClientPlayerEntity player,
            Consumer<ClickableWidget> addChild
    ) {
        this.screen = screen;
        this.player = player;
        this.addChild = addChild;
        int y = FocusedFootstepsPageLayout.rowY(screen.height);
        previous = new WitchMaidenPageSwitchWidget(
                0,
                y,
                Items.PURPLE_DYE.getDefaultStack(),
                Text.translatable("ui.sparkwitch.witch_maiden.pagination.previous"),
                button -> changePage(-1)
        );
        next = new WitchMaidenPageSwitchWidget(
                0,
                y,
                Items.LIME_DYE.getDefaultStack(),
                Text.translatable("ui.sparkwitch.witch_maiden.pagination.next"),
                button -> changePage(1)
        );
        addChild.accept(previous);
        addChild.accept(next);
        refreshAvailability();
    }

    /** Returns null unless the exact live Witch Maiden owner gate is satisfied. */
    public static @Nullable WitchMaidenInventoryUi attach(
            LimitedInventoryScreen screen,
            Consumer<ClickableWidget> addChild
    ) {
        ClientPlayerEntity player = screen.player;
        if (player == null || !ownerAvailable(player)) {
            return null;
        }
        return new WitchMaidenInventoryUi(screen, player, addChild);
    }

    public void renderEmptyState(DrawContext context) {
        refreshAvailability();
        if (!ownerAvailable(player) || !liveWidgets.isEmpty()) {
            return;
        }
        Text empty = Text.translatable("ui.sparkwitch.witch_maiden.no_targets");
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int x = screen.width / 2 - textRenderer.getWidth(empty) / 2;
        int y = FocusedFootstepsPageLayout.rowY(screen.height) + 4;
        context.drawTextWithShadow(textRenderer, empty, x, y, 0xB04A8B);
    }

    private void changePage(int delta) {
        FocusedFootstepsClientState state = WitchMaidenClientModule.state();
        state.setPage(state.page() + delta);
        refreshAvailability();
    }

    private void refreshAvailability() {
        if (!ownerAvailable(player)) {
            liveWidgets = List.of();
            hideAll();
            return;
        }

        List<UUID> liveCandidates = candidates(player);
        for (WitchMaidenTargetWidget widget : targetWidgets.values()) {
            widget.setPageVisible(false);
        }
        List<WitchMaidenTargetWidget> refreshed = new ArrayList<>(liveCandidates.size());
        int y = FocusedFootstepsPageLayout.rowY(screen.height);
        for (UUID targetUuid : liveCandidates) {
            PlayerListEntry entry = player.networkHandler.getPlayerListEntry(targetUuid);
            WitchMaidenTargetWidget widget = targetWidgets.get(targetUuid);
            if (widget == null) {
                widget = new WitchMaidenTargetWidget(0, y, player, targetUuid, entry);
                targetWidgets.put(targetUuid, widget);
                addChild.accept(widget);
            } else {
                widget.refreshPlayerListEntry(entry);
            }
            refreshed.add(widget);
        }
        liveWidgets = List.copyOf(refreshed);
        refreshPage();
    }

    private void refreshPage() {
        FocusedFootstepsClientState state = WitchMaidenClientModule.state();
        int page = state.clampPage(liveWidgets.size());
        FocusedFootstepsPageLayout.PageWindow window =
                FocusedFootstepsPageLayout.window(liveWidgets.size(), page);
        int y = FocusedFootstepsPageLayout.rowY(screen.height);
        int playerX = FocusedFootstepsPageLayout.playerStartX(screen.width, window.visibleCount());

        for (int index = 0; index < liveWidgets.size(); index++) {
            WitchMaidenTargetWidget widget = liveWidgets.get(index);
            boolean visible = index >= window.startIndex() && index < window.endIndex();
            widget.setPageVisible(visible);
            if (visible) {
                widget.setX(playerX + (index - window.startIndex()) * FocusedFootstepsPageLayout.SLOT_SPACING);
                widget.setY(y);
            }
        }

        int controlCount = (window.showPrevious() ? 1 : 0) + (window.showNext() ? 1 : 0);
        int controlX = FocusedFootstepsPageLayout.controlStartX(screen.width, controlCount);
        int controlY = FocusedFootstepsPageLayout.controlRowY(screen.height);
        previous.visible = window.showPrevious();
        previous.active = previous.visible;
        previous.setX(controlX);
        previous.setY(controlY);
        next.visible = window.showNext();
        next.active = next.visible;
        next.setX(controlX + (window.showPrevious() ? FocusedFootstepsPageLayout.SLOT_SPACING : 0));
        next.setY(controlY);
    }

    private void hideAll() {
        for (WitchMaidenTargetWidget widget : targetWidgets.values()) {
            widget.setPageVisible(false);
        }
        previous.visible = false;
        previous.active = false;
        next.visible = false;
        next.active = false;
    }

    private static List<UUID> candidates(ClientPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return FocusedFootstepsInventoryRules.candidates(
                player.getUuid(),
                player.networkHandler.getPlayerUuids(),
                game::hasAnyRole,
                game::isPlayerDead
        );
    }

    private static boolean ownerAvailable(ClientPlayerEntity player) {
        if (player.networkHandler == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return FocusedFootstepsInventoryRules.ownerEligible(
                SparkWitchServerConnection.isConfirmedServer(),
                game.isRunning(),
                WitchMaidenRules.isWitchMaiden(game.getRole(player)),
                GameFunctions.isPlayerPlayingAndAlive(player),
                FocusedFootstepsRules.SKILL_ID.equals(
                        WitchPlayerComponent.KEY.get(player).getActiveSkillId())
        );
    }
}
