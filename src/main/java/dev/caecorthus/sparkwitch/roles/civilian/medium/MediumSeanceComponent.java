package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level vote window of the Medium, the Otherworld-window shape: it is started by a paid 通灵
 * ({@link MediumWindowKind#QUESTION}) or a paid 幽灵的加护 ({@link MediumWindowKind#BLESSING}) and runs the full
 * {@link MediumRules#SEANCE_WINDOW_TICKS} even if the Medium dies mid-window; only the timer or the round edge stops
 * it. One window at a time, of either kind (both services refuse while one is live). Synced once per second, plus on
 * start and end; the client ticks the remainder down locally between syncs. The sync is per recipient: the content
 * (the question, or the blessings' disabled mask) goes only to the voters who may answer and to the caster
 * ({@link #isRevealed()} on their clients); another Medium gets the countdown, the window serial and the kind (its HUD
 * rows need "running"); every other client receives no packet at all ({@link #shouldSyncWith}). The caster's identity
 * is never synced. A player who dies mid-window receives the content with the next periodic sync.
 * 通灵师的世界级投票窗口，里世界窗口的形态：由一次付费通灵（{@link MediumWindowKind#QUESTION}）或付费幽灵的加护
 * （{@link MediumWindowKind#BLESSING}）开启，即使通灵师中途死亡也会跑满 {@link MediumRules#SEANCE_WINDOW_TICKS}；
 * 只有计时或回合节点能结束它。同一时刻至多一个窗口，不分种类（窗口进行中两个服务都会拒绝）。每秒同步一次，开启与结束时也同步；
 * 客户端在两次同步之间本地倒数。同步按接收者区分：内容（提问，或加护的禁用掩码）只发给可以作答的投票者与施放者（其客户端上
 * {@link #isRevealed()} 为 true）；其他通灵师拿到倒计时、窗口序号与种类（其 HUD 行需要显示「进行中」）；其余客户端完全
 * 收不到数据包（{@link #shouldSyncWith}）。施放者身份从不同步。窗口中途死亡的玩家会在下一次周期同步时收到内容。
 */
public final class MediumSeanceComponent implements AutoSyncedComponent, ServerTickingComponent,
        ClientTickingComponent {
    public static final ComponentKey<MediumSeanceComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("medium_seance"),
            MediumSeanceComponent.class
    );
    private static final String TICKS_LEFT = "TicksLeft";
    private static final String QUESTION = "Question";
    private static final String CASTER = "Caster";
    private static final String WINDOW_ID = "WindowId";
    private static final String KIND = "Kind";
    private static final String DISABLED_MASK = "DisabledMask";
    /** Every bit a disabled mask may carry. / 禁用掩码可携带的全部位。 */
    private static final int BLESSING_MASK_BITS = (1 << MediumBlessing.count()) - 1;
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final World world;
    private int ticksLeft;
    private String question = "";
    private MediumWindowKind kind = MediumWindowKind.QUESTION;
    /** Bits of the {@link MediumBlessing}s greyed out in this window, frozen at its start; 0 for a question.
     * 本窗口中置灰的 {@link MediumBlessing} 位，在窗口开启时冻结；提问窗口为 0。 */
    private int disabledMask;
    /** Client only: the server revealed this window's content to the local player (a voter or the caster).
     * 仅客户端：服务端已向本地玩家（投票者或施放者）公开本窗口的内容。 */
    private boolean revealed;
    @Nullable
    private UUID casterUuid;
    private int syncTicks;
    /** Serial of the current window; the client uses it to open the vote prompt once per window.
     * 当前窗口的序号；客户端据此每个窗口只弹出一次投票界面。 */
    private int windowId;
    // Server-only vote tally (voter -> choice index of the window's kind); never synced and never persisted (a window
    // is 20 s long).
    // 仅服务端计票（投票者 -> 本窗口种类下的选项序号）；不同步、不持久化（一个窗口只有 20 秒）。
    private final Map<UUID, Integer> votes = new LinkedHashMap<>();
    /** The caster's 招魂 bindings frozen at window start, so a mid-window death keeps the paid weights.
     * 窗口开启时冻结的招魂绑定名单，使通灵师中途死亡也不会丢失已付费的票权。 */
    private final Set<UUID> boundTargets = new LinkedHashSet<>();
    /** Server only: recipients whose client currently holds something other than the idle default.
     * 仅服务端：其客户端当前持有非空闲默认值的接收者。 */
    private final Set<UUID> toldRecipients = new HashSet<>();

    public MediumSeanceComponent(World world) {
        this.world = world;
    }

    public boolean isActive() {
        return ticksLeft > 0;
    }

    public int getTicksLeft() {
        return Math.max(0, ticksLeft);
    }

    /**
     * The question the dead are voting on; empty outside a window, in a blessing window, and on a client the content
     * was not revealed to. A client must read {@link #isRevealed()}, not this, to tell whether it was told.
     * 死者正在投票的问题；窗口外、加护窗口中以及未获公开内容的客户端上为空。客户端判断自己是否被告知时必须读取
     * {@link #isRevealed()}，而不是本方法。
     */
    public String getQuestion() {
        return question;
    }

    /** What the live window votes on; {@link MediumWindowKind#QUESTION} while idle. / 进行中窗口的投票种类；空闲时为 QUESTION。 */
    public MediumWindowKind getKind() {
        return kind;
    }

    /**
     * Bits ({@link MediumBlessing#mask()}) of the blessings that cannot be chosen in this window; synced only to
     * revealed recipients, 0 otherwise.
     * 本窗口中不可选的加护位（{@link MediumBlessing#mask()}）；只同步给已获公开内容的接收者，否则为 0。
     */
    public int getDisabledMask() {
        return disabledMask;
    }

    public boolean isDisabled(@Nullable MediumBlessing blessing) {
        return blessing != null && (disabledMask & blessing.mask()) != 0;
    }

    /**
     * Client-readable: the live window's content (question text, or the blessing choices and their disabled mask) was
     * sent to this client, i.e. the local player is a voter or the caster. Always false on the server, which decides
     * per recipient instead.
     * 客户端可读：进行中窗口的内容（提问文本，或加护选项及其禁用掩码）已发给本客户端，即本地玩家是投票者或施放者。
     * 服务端上始终为 false，服务端按接收者分别判定。
     */
    public boolean isRevealed() {
        return revealed && isActive();
    }

    /** Serial of the current (or last) window; a client that is told nothing reads 0. Real serials start at 1.
     * 当前（或上一个）窗口的序号；未被告知的客户端读到 0。真实序号从 1 开始。 */
    public int getWindowId() {
        return windowId;
    }

    /** Server only: the Medium who paid for this window; null outside one and always null on a client.
     * 仅服务端：为本窗口付费的通灵师；窗口外为空，客户端上始终为空。 */
    public @Nullable UUID getCasterUuid() {
        return casterUuid;
    }

    /** Server only: opens a 通灵 question window and pushes it to its recipients at once.
     * 仅服务端：开启通灵提问窗口并立刻推给其接收者。 */
    public void startQuestion(UUID caster, String question, Collection<UUID> boundTargets) {
        open(MediumWindowKind.QUESTION, caster, question == null ? "" : question, 0, boundTargets);
    }

    /**
     * Server only: opens a 幽灵的加护 window. {@code disabledMask} is decided here, once, and frozen for the window
     * (owner rule: 回满 san is greyed out for a caster without real sanity).
     * 仅服务端：开启幽灵的加护窗口。{@code disabledMask} 在此一次性决定并在窗口内冻结（所有者规则：施放者没有真实理智时
     * 「回满 san」置灰）。
     */
    public void startBlessing(UUID caster, int disabledMask, Collection<UUID> boundTargets) {
        open(MediumWindowKind.BLESSING, caster, "", disabledMask & BLESSING_MASK_BITS, boundTargets);
    }

    private void open(MediumWindowKind kind, UUID caster, String question, int disabledMask,
                      Collection<UUID> boundTargets) {
        this.ticksLeft = MediumRules.SEANCE_WINDOW_TICKS;
        this.kind = kind;
        this.question = question;
        this.disabledMask = disabledMask;
        this.casterUuid = caster;
        this.syncTicks = 0;
        this.windowId++;
        this.votes.clear();
        this.boundTargets.clear();
        if (boundTargets != null) {
            this.boundTargets.addAll(boundTargets);
        }
        sync();
    }

    /** Clears on window end, round start and round end. / 窗口结束、回合开始与结束时清空。 */
    public void clear() {
        if (ticksLeft == 0 && question.isEmpty() && casterUuid == null && votes.isEmpty()
                && boundTargets.isEmpty() && kind == MediumWindowKind.QUESTION && disabledMask == 0) {
            return;
        }
        ticksLeft = 0;
        question = "";
        kind = MediumWindowKind.QUESTION;
        disabledMask = 0;
        casterUuid = null;
        syncTicks = 0;
        votes.clear();
        boundTargets.clear();
        sync();
    }

    /**
     * Whether {@code choice} is a selectable option of the live window: {@link MediumRules#VOTE_NO} /
     * {@link MediumRules#VOTE_YES} for a question; a known, not disabled {@link MediumBlessing} index for a blessing.
     * 该 {@code choice} 是否为进行中窗口的可选项：提问窗口为 {@link MediumRules#VOTE_NO} / {@link MediumRules#VOTE_YES}；
     * 加护窗口为已知且未禁用的 {@link MediumBlessing} 序号。
     */
    public boolean acceptsChoice(int choice) {
        if (!isActive()) {
            return false;
        }
        if (kind == MediumWindowKind.BLESSING) {
            MediumBlessing blessing = MediumBlessing.byIndex(choice);
            return blessing != null && !isDisabled(blessing);
        }
        return choice == MediumRules.VOTE_NO || choice == MediumRules.VOTE_YES;
    }

    /**
     * Server only: one dead player's vote; the map keeps only the latest choice, in casting order (a changed vote
     * moves its voter to the end, which is what the tie rule reads). A choice the window does not offer
     * ({@link #acceptsChoice}) is dropped and leaves an earlier vote untouched.
     * 仅服务端：一名死者的一票；映射只保留最新选择，并按投票先后排列（改票会把该投票者移到最后，平票规则读取的就是它）。
     * 窗口不提供的选项（{@link #acceptsChoice}）会被丢弃，且不影响此前已投的票。
     */
    public void recordVote(UUID voter, int choice) {
        if (voter == null || !acceptsChoice(choice)) {
            return;
        }
        // Re-inserting keeps the map in casting order, so its last entry is always the last vote cast.
        // 先移除再插入，使映射保持投票先后顺序，其最后一项始终是最后投出的一票。
        votes.remove(voter);
        votes.put(voter, choice);
    }

    /** Server-only snapshot of the votes (choice indices) at window end, in casting order.
     * 窗口结束时按投票先后排列的计票快照（选项序号），仅服务端。 */
    public Map<UUID, Integer> getVotes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(votes));
    }

    /** The 招魂 bindings frozen at window start, in binding order. / 窗口开启时冻结的招魂绑定，按绑定先后排序。 */
    public Set<UUID> getBoundTargets() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(boundTargets));
    }

    /**
     * Server only: keeps bindings paid for during the live window, so a caster who dies or changes role before the
     * tally does not lose the vote weight they bought.
     * 仅服务端：保留进行中窗口期间已付费的绑定，使在计票前死亡或转职的施放者不会丢掉已买的票权。
     */
    public void retainBoundTargets(Collection<UUID> targets) {
        if (targets == null || !isActive()) {
            return;
        }
        boundTargets.addAll(targets);
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!GameWorldComponent.KEY.get(serverWorld).isRunning()) {
            clear();
            return;
        }
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        if (ticksLeft <= 0) {
            ticksLeft = 0;
            // The tally owns the end: it reads this component, then clears it.
            // 计票负责收尾：它先读本组件，再将其清空。
            MediumSkillService.onSeanceEnded(serverWorld);
            return;
        }
        if (++syncTicks >= SYNC_INTERVAL_TICKS) {
            syncTicks = 0;
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    private void sync() {
        if (world instanceof ServerWorld) {
            KEY.sync(world);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt(WINDOW_ID, windowId);
        if (ticksLeft > 0) {
            tag.putInt(TICKS_LEFT, ticksLeft);
            tag.putString(QUESTION, question);
            tag.putInt(KIND, kind.index());
            tag.putInt(DISABLED_MASK, disabledMask);
            if (casterUuid != null) {
                tag.putUuid(CASTER, casterUuid);
            }
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ticksLeft = tag.contains(TICKS_LEFT, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(TICKS_LEFT)) : 0;
        question = ticksLeft > 0 ? tag.getString(QUESTION) : "";
        // Absent in a save written before the blessing existed: a question window with nothing disabled.
        // 加护出现之前写入的存档中没有这两项：按没有禁用项的提问窗口读取。
        kind = ticksLeft > 0 && tag.contains(KIND, NbtElement.NUMBER_TYPE)
                ? MediumWindowKind.byIndex(tag.getInt(KIND))
                : MediumWindowKind.QUESTION;
        disabledMask = ticksLeft > 0 && kind == MediumWindowKind.BLESSING
                ? tag.getInt(DISABLED_MASK) & BLESSING_MASK_BITS
                : 0;
        casterUuid = ticksLeft > 0 && tag.containsUuid(CASTER) ? tag.getUuid(CASTER) : null;
        windowId = tag.contains(WINDOW_ID, NbtElement.NUMBER_TYPE) ? tag.getInt(WINDOW_ID) : 0;
    }

    /**
     * Server only: the window's content (question, or blessing choices) is revealed to the voters
     * ({@link MediumRules#mayVote(net.minecraft.entity.player.PlayerEntity)}: dead round members who are not Wraiths)
     * and to the caster, nobody else.
     * 仅服务端：窗口内容（提问或加护选项）只向投票者（不属于冤魂的本局死者）与施放者公开，其他人一律不发。
     */
    private boolean revealsContentTo(ServerPlayerEntity recipient) {
        if (recipient.getUuid().equals(casterUuid)) {
            return true;
        }
        return MediumRules.mayVote(recipient);
    }

    /**
     * Server only: who is told about the window at all. Voters and the caster during a live window, and every Medium
     * at any time (its HUD row shows "running").
     * 仅服务端：窗口究竟告知哪些人。进行中窗口的投票者与施放者，以及任何时候的每一名通灵师（其 HUD 行显示「进行中」）。
     */
    private boolean informs(ServerPlayerEntity recipient) {
        return (isActive() && revealsContentTo(recipient)) || MediumRules.isMedium(recipient);
    }

    /**
     * Nobody else receives a packet at all, so no other client can tell that, or when, either skill was cast. A
     * recipient that was told something earlier still gets one more packet once it stops being informed (window end,
     * revive, role change), which resets its copy.
     * 其余任何人都收不到数据包，因此其他客户端无从得知是否、何时有人施放通灵或加护。此前被告知过的接收者在不再被告知时
     * （窗口结束、复活、转职）还会再收到一个数据包，用于重置其副本。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return informs(recipient) || toldRecipients.contains(recipient.getUuid());
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        // Voters and the caster get everything; another Medium gets the countdown, the serial and the kind; a
        // recipient that is no longer informed gets an idle window with serial 0 and is then forgotten.
        // Layout: varint ticksLeft, varint windowId, varint kind, boolean revealed, then only when revealed:
        // varint disabledMask, string question (empty in a blessing window).
        // 投票者与施放者拿到全部内容；其他通灵师拿到倒计时、序号与种类；不再被告知的接收者拿到序号为 0 的空闲窗口，随后被遗忘。
        // 布局：varint 剩余刻数、varint 窗口序号、varint 种类、boolean 是否公开；仅在公开时再跟：varint 禁用掩码、
        // 字符串提问（加护窗口为空）。
        boolean reveal = isActive() && revealsContentTo(recipient);
        boolean informed = reveal || MediumRules.isMedium(recipient);
        if (informed) {
            toldRecipients.add(recipient.getUuid());
        } else {
            toldRecipients.remove(recipient.getUuid());
        }
        buf.writeVarInt(informed ? getTicksLeft() : 0);
        buf.writeVarInt(informed ? windowId : 0);
        buf.writeVarInt(informed && isActive() ? kind.index() : MediumWindowKind.QUESTION.index());
        buf.writeBoolean(reveal);
        if (reveal) {
            buf.writeVarInt(disabledMask);
            buf.writeString(question, MediumRules.QUESTION_MAX_CHARS * 4);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        ticksLeft = Math.max(0, buf.readVarInt());
        windowId = buf.readVarInt();
        kind = MediumWindowKind.byIndex(buf.readVarInt());
        revealed = buf.readBoolean();
        disabledMask = revealed ? buf.readVarInt() & BLESSING_MASK_BITS : 0;
        question = revealed ? buf.readString(MediumRules.QUESTION_MAX_CHARS * 4) : "";
        casterUuid = null;
    }
}
