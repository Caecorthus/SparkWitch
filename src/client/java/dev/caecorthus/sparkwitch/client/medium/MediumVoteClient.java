package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumWindowKind;
import dev.caecorthus.sparkwitch.roles.civilian.medium.SubmitMediumVoteC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

/**
 * Client state of the dead's vote, for both window kinds: opens {@link MediumVoteScreen} (通灵 question) or
 * {@link MediumBlessingVoteScreen} (幽灵的加护) once per window, re-opens it on the shared ability key, and remembers
 * the local player's own latest choice for the highlight and the HUD line. It decides nothing: the server reveals a
 * window only to the players it lets answer, re-checks every vote (a disabled blessing is dropped) and keeps each
 * voter's latest one. The remembered choice is presentation only and is lost on reconnect (the server still has it).
 * 死者投票的客户端状态，覆盖两种窗口：每个窗口自动弹出一次 {@link MediumVoteScreen}（通灵提问）或
 * {@link MediumBlessingVoteScreen}（幽灵的加护），按共享技能键可重新打开，并记住本地玩家自己最新的选择，用于高亮与 HUD
 * 提示行。它不做任何裁决：服务端只向允许作答的玩家公开窗口，重新校验每一票（已禁用的加护会被丢弃），并保留每名投票者最新的
 * 一票。记住的选择仅用于展示，重连后丢失（服务端仍保留该票）。
 */
public final class MediumVoteClient {
    /** Real window serials start at 1 and only grow (0 means "told nothing"), so this never matches one.
     * 真实窗口序号从 1 开始递增（0 表示「未被告知」），此值不会与之相同。 */
    private static final int NO_WINDOW = Integer.MIN_VALUE;

    private static int promptedWindowId = NO_WINDOW;
    private static int choiceWindowId = NO_WINDOW;
    @Nullable
    private static Integer localChoice;

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
     * The local player could answer right now: confirmed SparkWitch server, a live window whose content the server
     * revealed to this client (question text or the blessing choices), and the server's own side-neutral voter gate
     * (a dead member of the running round who is not a Wraith; a Wraith keeps the ability key for its own play).
     * A living Medium also has the content revealed (its own window) but fails the voter gate.
     * 本地玩家此刻可以作答：已确认的 SparkWitch 服务器、窗口进行中且服务端已向本客户端公开内容（提问文本或加护选项），并满足
     * 与服务端相同的两端通用投票门槛（进行中对局的本局死者，且不是冤魂；冤魂的技能键留给自己使用）。存活的通灵师同样获得内容
     * （自己的窗口），但不满足投票门槛。
     */
    static boolean canVote(MinecraftClient client) {
        if (client.world == null || client.player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        return MediumSeanceComponent.KEY.get(client.world).isRevealed() && MediumRules.mayVote(client.player);
    }

    /** The live window's kind; meaningful only while {@link #canVote} holds. / 进行中窗口的种类；仅在可投票时有意义。 */
    static MediumWindowKind kind(MinecraftClient client) {
        return client.world == null ? MediumWindowKind.QUESTION
                : MediumSeanceComponent.KEY.get(client.world).getKind();
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

    /**
     * The local player's choice index in {@code windowId} (a {@code MediumRules.VOTE_*} answer or a blessing index);
     * null when none was sent.
     * 本地玩家在该窗口的选择序号（{@code MediumRules.VOTE_*} 回答或加护序号）；未作答为 null。
     */
    static @Nullable Integer choice(int windowId) {
        return choiceWindowId == windowId ? localChoice : null;
    }

    /** The question answer in {@code windowId}: true yes, false no, null none. / 该窗口的提问回答：是、否或未作答。 */
    static @Nullable Boolean answer(int windowId) {
        Integer choice = choice(windowId);
        return choice == null ? null : choice == MediumRules.VOTE_YES;
    }

    /**
     * Sends one choice; a later one for the same window replaces it server-side. Nothing is remembered when the
     * packet cannot be sent, so the HUD never claims a choice the server did not get.
     * 发送一次选择；同一窗口后发的选择在服务端覆盖先前的选择。数据包无法发送时不记录，HUD 不会显示服务端没有收到的选择。
     */
    static void submit(int windowId, int choice) {
        if (!ClientPlayNetworking.canSend(SubmitMediumVoteC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new SubmitMediumVoteC2SPacket(windowId, choice));
        choiceWindowId = windowId;
        localChoice = choice;
    }

    /** A 通灵 answer. / 通灵回答。 */
    static void submitAnswer(int windowId, boolean yes) {
        submit(windowId, yes ? MediumRules.VOTE_YES : MediumRules.VOTE_NO);
    }

    private static void open(MinecraftClient client) {
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(client.world);
        if (seance.getKind() == MediumWindowKind.BLESSING) {
            client.setScreen(new MediumBlessingVoteScreen(seance.getWindowId()));
        } else {
            client.setScreen(new MediumVoteScreen(seance.getWindowId(), seance.getQuestion()));
        }
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
