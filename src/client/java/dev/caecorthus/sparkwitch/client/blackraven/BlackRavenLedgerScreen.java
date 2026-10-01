package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Perceived-tab book pages built only from completed owner snapshots. / 仅用所有者已解锁快照生成“已感知身份”页的原版书页。 */
public final class BlackRavenLedgerScreen {
    private static final int ENTRIES_PER_PAGE = 4;

    private BlackRavenLedgerScreen() {
    }

    /**
     * Opens the two-tab ledger on the Perceived tab; the Absent tab is read-only from here (no mask session).
     * 在“已感知身份”页打开双页感知册；从这里打开时“未登场的善良职业”页只读（无面具会话）。
     */
    public static void open(MinecraftClient client) {
        BlackRavenLedgerBookScreen.open(client, BlackRavenLedgerBookScreen.Tab.PERCEIVED, 0);
    }

    /** Tab A pages, built only from completed owner snapshots. / Tab A 书页，仅由已完成的拥有者快照生成。 */
    static BookScreen.Contents perceivedContents(PlayerEntity player) {
        List<BlackRavenIdentitySnapshot> snapshots =
                BlackRavenPerceptionPlayerComponent.KEY.get(player).completedSnapshots();
        return new BookScreen.Contents(pages(snapshots));
    }

    static List<Text> pages(List<BlackRavenIdentitySnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return List.of(Text.translatable("screen.sparkwitch.black_raven_ledger.empty"));
        }

        List<Text> pages = new ArrayList<>();
        for (int start = 0; start < snapshots.size(); start += ENTRIES_PER_PAGE) {
            MutableText page = Text.empty();
            int end = Math.min(snapshots.size(), start + ENTRIES_PER_PAGE);
            for (int index = start; index < end; index++) {
                if (index > start) {
                    page.append(Text.literal("\n\n"));
                }
                BlackRavenIdentitySnapshot snapshot = snapshots.get(index);
                MutableText role = WitchRoleDisplayTexts.roleName(snapshot.roleTranslationKey())
                        .styled(style -> style.withColor(snapshot.roleColor()));
                page.append(Text.literal(snapshot.playerName() + " - ").formatted(Formatting.BLACK));
                page.append(role);
            }
            pages.add(page);
        }
        return List.copyOf(pages);
    }
}
