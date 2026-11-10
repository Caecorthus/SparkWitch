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
 * World-level 通灵 voting window, the Otherworld-window shape: it is started by a paid 通灵 and runs the full
 * {@link MediumRules#SEANCE_WINDOW_TICKS} even if the Medium dies mid-window; only the timer or the round edge stops
 * it. One window at a time (the service refuses a second submit). Synced once per second, plus on start and end; the
 * client ticks the remainder down locally between syncs. The sync is per recipient: the question goes only to the
 * voters who may answer it and to the caster; another Medium gets the countdown and the window serial (its HUD row
 * needs "running"); every other client receives no packet at all ({@link #shouldSyncWith}). The caster's identity is
 * never synced. A player who dies mid-window receives the question with the next periodic sync.
 * 世界级通灵投票窗口，里世界窗口的形态：由一次付费通灵开启，即使通灵师中途死亡也会跑满
 * {@link MediumRules#SEANCE_WINDOW_TICKS}；只有计时或回合节点能结束它。同一时刻至多一个窗口（服务端拒绝第二次提交）。
 * 每秒同步一次，开启与结束时也同步；客户端在两次同步之间本地倒数。同步按接收者区分：提问只发给可以作答的投票者与
 * 施放者；其他通灵师拿到倒计时与窗口序号（其 HUD 行需要显示「进行中」）；其余客户端完全收不到数据包
 * （{@link #shouldSyncWith}）。
 * 施放者身份从不同步。窗口中途死亡的玩家会在下一次周期同步时收到提问。
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
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final World world;
    private int ticksLeft;
    private String question = "";
    @Nullable
    private UUID casterUuid;
    private int syncTicks;
    /** Serial of the current window; the client uses it to open the vote prompt once per window.
     * 当前窗口的序号；客户端据此每个窗口只弹出一次投票界面。 */
    private int windowId;
    // Server-only vote tally; never synced and never persisted (a window is 20 s long).
    // 仅服务端计票；不同步、不持久化（一个窗口只有 20 秒）。
    private final Map<UUID, Boolean> votes = new LinkedHashMap<>();
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

    /** The question the dead are voting on; empty outside a window. / 死者正在投票的问题；窗口外为空。 */
    public String getQuestion() {
        return question;
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

    /** Server only: opens the window and pushes it to every client at once. / 仅服务端：开启窗口并立刻推给所有客户端。 */
    public void start(UUID caster, String question, Collection<UUID> boundTargets) {
        this.ticksLeft = MediumRules.SEANCE_WINDOW_TICKS;
        this.question = question == null ? "" : question;
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
                && boundTargets.isEmpty()) {
            return;
        }
        ticksLeft = 0;
        question = "";
        casterUuid = null;
        syncTicks = 0;
        votes.clear();
        boundTargets.clear();
        sync();
    }

    /**
     * Server only: one dead player's vote; the map keeps only the latest choice, in casting order (a changed vote
     * moves its voter to the end, which is what the tie rule reads).
     * 仅服务端：一名死者的一票；映射只保留最新选择，并按投票先后排列（改票会把该投票者移到最后，平票规则读取的就是它）。
     */
    public void recordVote(UUID voter, boolean yes) {
        if (voter == null || !isActive()) {
            return;
        }
        // Re-inserting keeps the map in casting order, so its last entry is always the last vote cast.
        // 先移除再插入，使映射保持投票先后顺序，其最后一项始终是最后投出的一票。
        votes.remove(voter);
        votes.put(voter, yes);
    }

    /** Server-only snapshot of the votes at window end, in casting order. / 窗口结束时按投票先后排列的计票快照，仅服务端。 */
    public Map<UUID, Boolean> getVotes() {
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
            if (casterUuid != null) {
                tag.putUuid(CASTER, casterUuid);
            }
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ticksLeft = tag.contains(TICKS_LEFT, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(TICKS_LEFT)) : 0;
        question = ticksLeft > 0 ? tag.getString(QUESTION) : "";
        casterUuid = ticksLeft > 0 && tag.containsUuid(CASTER) ? tag.getUuid(CASTER) : null;
        windowId = tag.contains(WINDOW_ID, NbtElement.NUMBER_TYPE) ? tag.getInt(WINDOW_ID) : 0;
    }

    /**
     * Server only: the question is revealed to the voters ({@link MediumRules#mayVote(net.minecraft.entity.player
     * .PlayerEntity)}: dead round members who are not promoted Wraiths) and to the caster, nobody else.
     * 仅服务端：提问只向投票者（未晋升为冤魂身份的本局死者）与施放者公开，其他人一律不发。
     */
    private boolean revealsQuestionTo(ServerPlayerEntity recipient) {
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
        return (isActive() && revealsQuestionTo(recipient)) || MediumRules.isMedium(recipient);
    }

    /**
     * Nobody else receives a packet at all, so no other client can tell that, or when, a 通灵 was cast. A recipient
     * that was told something earlier still gets one more packet once it stops being informed (window end, revive,
     * role change), which resets its copy.
     * 其余任何人都收不到数据包，因此其他客户端无从得知是否、何时有人施放通灵。此前被告知过的接收者在不再被告知时
     * （窗口结束、复活、转职）还会再收到一个数据包，用于重置其副本。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return informs(recipient) || toldRecipients.contains(recipient.getUuid());
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        // Voters and the caster get everything; another Medium gets the countdown and the serial; a recipient that is
        // no longer informed gets an idle window with serial 0 and is then forgotten.
        // 投票者与施放者拿到全部内容；其他通灵师拿到倒计时与序号；不再被告知的接收者拿到序号为 0 的空闲窗口，随后被遗忘。
        boolean reveal = isActive() && revealsQuestionTo(recipient);
        boolean informed = reveal || MediumRules.isMedium(recipient);
        if (informed) {
            toldRecipients.add(recipient.getUuid());
        } else {
            toldRecipients.remove(recipient.getUuid());
        }
        buf.writeVarInt(informed ? getTicksLeft() : 0);
        buf.writeVarInt(informed ? windowId : 0);
        buf.writeBoolean(reveal);
        if (reveal) {
            buf.writeString(question, MediumRules.QUESTION_MAX_CHARS * 4);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        ticksLeft = Math.max(0, buf.readVarInt());
        windowId = buf.readVarInt();
        question = buf.readBoolean() ? buf.readString(MediumRules.QUESTION_MAX_CHARS * 4) : "";
        casterUuid = null;
    }
}
