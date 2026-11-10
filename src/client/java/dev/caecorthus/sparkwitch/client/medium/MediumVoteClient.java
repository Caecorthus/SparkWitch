package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.SubmitMediumVoteC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

/**
 * Client state of the dead's 通灵 vote: opens {@link MediumVoteScreen} once per window, re-opens it on the shared
 * ability key, and remembers the local player's own latest answer for the highlight and the HUD line. It decides
 * nothing: the server syncs the question only to the players it lets answer, re-checks every vote and keeps each
 * voter's latest one. The remembered answer is presentation only and is lost on reconnect (the server still has it).
 * 死者通灵投票的客户端状态：每个窗口自动弹出一次 {@link MediumVoteScreen}，按共享技能键可重新打开，并记住本地玩家自己
 * 最新的回答，用于高亮与 HUD 提示行。它不做任何裁决：服务端只把提问同步给允许作答的玩家，重新校验每一票，并保留每名
 * 投票者最新的一票。记住的回答仅用于展示，重连后丢失（服务端仍保留该票）。
 */
public final class MediumVoteClient {
    /** Real window serials start at 1 and only grow (0 means "told nothing"), so this never matches one.
     * 真实窗口序号从 1 开始递增（0 表示「未被告知」），此值不会与之相同。 */
    private static final int NO_WINDOW = Integer.MIN_VALUE;

    private static int promptedWindowId = NO_WINDOW;
    private static int choiceWindowId = NO_WINDOW;
    @Nullable
    private static Boolean localChoice;

    private MediumVoteClient() {
    }

    /**
     * Every client tick: pops the vote screen up once for each new window the local player may answer. Another screen
     * that is already open (chat, a menu) is never interrupted; the HUD prompt and the ability key cover that case.
     * 每个客户端刻调用：本地玩家可作答的每个新窗口自动弹出一次投票界面。已打开的其他界面（聊天、菜单）绝不会被打断，
     * 此时由 HUD 提示与技能键兜底。
     */
    static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            reset();
            return;
        }
        if (!canVote(client)) {
            return;
        }
        int windowId = MediumSeanceComponent.KEY.get(client.world).getWindowId();
        if (windowId == promptedWindowId) {
            return;
        }
        promptedWindowId = windowId;
        clearChoice();
        if (client.currentScreen == null) {
            open(client);
        }
    }

    /**
     * The local player could answer right now: confirmed SparkWitch server, a live window whose question the server
     * revealed to this client, and the server's own side-neutral voter gate (a dead member of the running round who
     * is not a promoted Wraith; a promoted Wraith keeps the ability key for its own skill).
     * 本地玩家此刻可以作答：已确认的 SparkWitch 服务器、窗口进行中且服务端已向本客户端公开提问，并满足与服务端相同的
     * 两端通用投票门槛（进行中对局的本局死者，且不是已晋升的冤魂；已晋升的冤魂的技能键留给自己的技能）。
     */
    static boolean canVote(MinecraftClient client) {
        if (client.world == null || client.player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(client.world);
        return seance.isActive()
                && !seance.getQuestion().isEmpty()
                && MediumRules.mayVote(client.player);
    }

    /**
     * True when a press of the shared ability key should open the vote screen instead of reaching any role branch.
     * 为 true 时，共享技能键的按下应打开投票界面，而不是进入任何职业分支。
     */
    public static boolean ownsAbilityKey(MinecraftClient client) {
        return client.currentScreen == null && canVote(client);
    }

    public static void onAbilityKey(MinecraftClient client) {
        if (ownsAbilityKey(client)) {
            open(client);
        }
    }

    /** The local player's answer in {@code windowId}; null when none was sent. / 本地玩家在该窗口的回答；未作答为 null。 */
    static @Nullable Boolean choice(int windowId) {
        return choiceWindowId == windowId ? localChoice : null;
    }

    /**
     * Sends one answer; a later one for the same window replaces it server-side. Nothing is remembered when the
     * packet cannot be sent, so the HUD never claims an answer the server did not get.
     * 发送一次回答；同一窗口后发的回答在服务端覆盖先前的回答。数据包无法发送时不记录，HUD 不会显示服务端没有收到的回答。
     */
    static void submit(int windowId, boolean yes) {
        if (!ClientPlayNetworking.canSend(SubmitMediumVoteC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new SubmitMediumVoteC2SPacket(windowId, yes));
        choiceWindowId = windowId;
        localChoice = yes;
    }

    private static void open(MinecraftClient client) {
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(client.world);
        client.setScreen(new MediumVoteScreen(seance.getWindowId(), seance.getQuestion()));
    }

    private static void clearChoice() {
        choiceWindowId = NO_WINDOW;
        localChoice = null;
    }

    private static void reset() {
        promptedWindowId = NO_WINDOW;
        clearChoice();
    }
}
