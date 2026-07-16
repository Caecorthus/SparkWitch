package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Opens vanilla read-only book pages built only from completed owner snapshots. / 仅用所有者已解锁快照生成原版只读书页。 */
public final class BlackRavenLedgerScreen {
    private static final int ENTRIES_PER_PAGE = 4;

    private BlackRavenLedgerScreen() {
    }

    public static void open(MinecraftClient client) {
        if (client.player == null || !BlackRavenClientState.isEligible(client.player)) {
            return;
        }
        List<BlackRavenIdentitySnapshot> snapshots =
                BlackRavenPerceptionPlayerComponent.KEY.get(client.player).completedSnapshots();
        client.setScreen(new BookScreen(new BookScreen.Contents(pages(snapshots))));
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
                page.append(Text.literal(snapshot.playerName() + " - ").formatted(Formatting.WHITE));
                page.append(role);
            }
            pages.add(page);
        }
        return List.copyOf(pages);
    }
}
