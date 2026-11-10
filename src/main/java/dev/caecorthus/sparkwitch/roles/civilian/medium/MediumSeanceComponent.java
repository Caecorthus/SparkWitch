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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level 通灵 voting window, the Otherworld-window shape: it is started by a paid 通灵 and runs the full
 * {@link MediumRules#SEANCE_WINDOW_TICKS} even if the Medium dies mid-window; only the timer or the round edge stops
 * it. One window at a time (the service refuses a second submit). Synced to every client once per second, plus on
 * start and end, so every dead spectator sees the question and the countdown; the client ticks the remainder down
 * locally between syncs.
 * 世界级通灵投票窗口，里世界窗口的形态：由一次付费通灵开启，即使通灵师中途死亡也会跑满
 * {@link MediumRules#SEANCE_WINDOW_TICKS}；只有计时或回合节点能结束它。同一时刻至多一个窗口（服务端拒绝第二次提交）。
 * 每秒同步给所有客户端，开启与结束时也同步，使所有死亡观战者看到问题与倒计时；客户端在两次同步之间本地倒数。
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
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final World world;
    private int ticksLeft;
    private String question = "";
    @Nullable
    private UUID casterUuid;
    private int syncTicks;
    // Server-only vote tally; never synced and never persisted (a window is 20 s long).
    // 仅服务端计票；不同步、不持久化（一个窗口只有 20 秒）。
    private final Map<UUID, Boolean> votes = new LinkedHashMap<>();
    @Nullable
    private UUID lastVoter;
    /** The caster's 招魂 bindings frozen at window start, so a mid-window death keeps the paid weights.
     * 窗口开启时冻结的招魂绑定名单，使通灵师中途死亡也不会丢失已付费的票权。 */
    private final Set<UUID> boundTargets = new LinkedHashSet<>();

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

    /** The Medium who paid for this window; null outside one. / 为本窗口付费的通灵师；窗口外为空。 */
    public @Nullable UUID getCasterUuid() {
        return casterUuid;
    }

    /** Server only: opens the window and pushes it to every client at once. / 仅服务端：开启窗口并立刻推给所有客户端。 */
    public void start(UUID caster, String question, Collection<UUID> boundTargets) {
        this.ticksLeft = MediumRules.SEANCE_WINDOW_TICKS;
        this.question = question == null ? "" : question;
        this.casterUuid = caster;
        this.syncTicks = 0;
        this.votes.clear();
        this.lastVoter = null;
        this.boundTargets.clear();
        if (boundTargets != null) {
            this.boundTargets.addAll(boundTargets);
        }
        sync();
    }

    /** Clears on window end, round start and round end. / 窗口结束、回合开始与结束时清空。 */
    public void clear() {
        if (ticksLeft == 0 && question.isEmpty() && casterUuid == null && votes.isEmpty()
                && lastVoter == null && boundTargets.isEmpty()) {
            return;
        }
        ticksLeft = 0;
        question = "";
        casterUuid = null;
        syncTicks = 0;
        votes.clear();
        lastVoter = null;
        boundTargets.clear();
        sync();
    }

    /**
     * Server only: one dead player's vote; the map keeps only the latest choice and remembers who voted last.
     * 仅服务端：一名死者的一票；映射只保留最新选择，并记住最后投票者。
     */
    public void recordVote(UUID voter, boolean yes) {
        if (voter == null || !isActive()) {
            return;
        }
        votes.put(voter, yes);
        lastVoter = voter;
    }

    /** Server-only snapshot of the votes at window end. / 窗口结束时的计票快照，仅服务端。 */
    public Map<UUID, Boolean> getVotes() {
        return Map.copyOf(votes);
    }

    /** The player whose vote was cast last; null when nobody voted. / 最后投票的玩家；无人投票时为 null。 */
    public @Nullable UUID getLastVoter() {
        return lastVoter;
    }

    /** The 招魂 bindings frozen at window start. / 窗口开启时冻结的招魂绑定。 */
    public Set<UUID> getBoundTargets() {
        return Set.copyOf(boundTargets);
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
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getTicksLeft());
        buf.writeString(question, MediumRules.QUESTION_MAX_CHARS * 4);
        buf.writeBoolean(casterUuid != null);
        if (casterUuid != null) {
            buf.writeUuid(casterUuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        ticksLeft = Math.max(0, buf.readVarInt());
        question = buf.readString(MediumRules.QUESTION_MAX_CHARS * 4);
        casterUuid = buf.readBoolean() ? buf.readUuid() : null;
    }
}
