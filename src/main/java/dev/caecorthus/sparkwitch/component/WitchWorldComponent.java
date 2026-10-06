package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintKarmaRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintKarmaState;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithSettings;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithSettingsNbtCodec;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchCeremonialSwordBgmSources;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchWorldRuntime;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.ForcedRecruit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Stores world-level skill toggles and round-scoped shared runtime state.
 * 保存世界级技能配置和本局共享运行态，例如禁用列表与圣徒业障。
 */
public final class WitchWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("world"),
            WitchWorldComponent.class
    );

    private final World world;
    private final LinkedHashSet<Identifier> disabledSkills = new LinkedHashSet<>();
    private final ForcedWraithPromotionLocks forcedWraithPromotions = new ForcedWraithPromotionLocks();
    private final ForcedRecruitQueue forcedRecruits = new ForcedRecruitQueue();
    private final GrandWitchCeremonialSwordBgmSources grandWitchCeremonialSwordBgmSources =
            new GrandWitchCeremonialSwordBgmSources();
    private final SaintKarmaState saintKarmaState = new SaintKarmaState();
    private WraithSettings wraithSettings = WraithSettings.DEFAULT;
    private int instinctObscureTicks;
    private int obscureActionbarTicks;
    private int fearTicks;
    private int syncedGrandWitchCeremonialSwordBgmSources;

    public WitchWorldComponent(World world) {
        this.world = world;
    }

    public boolean isSkillEnabled(Identifier skillId) {
        return !disabledSkills.contains(skillId);
    }

    public void setSkillEnabled(Identifier skillId, boolean enabled) {
        boolean changed = enabled ? disabledSkills.remove(skillId) : disabledSkills.add(skillId);
        if (changed) {
            sync();
        }
    }

    public Set<Identifier> getDisabledSkillIds() {
        return Set.copyOf(disabledSkills);
    }

    public boolean setForcedWraithPromotion(UUID playerUuid, Identifier roleId) {
        return forcedWraithPromotions.set(playerUuid, roleId);
    }

    public Identifier getForcedWraithPromotion(UUID playerUuid) {
        return forcedWraithPromotions.get(playerUuid);
    }

    public boolean clearForcedWraithPromotion(UUID playerUuid) {
        return forcedWraithPromotions.clear(playerUuid);
    }

    public Map<UUID, Identifier> getForcedWraithPromotions() {
        return forcedWraithPromotions.snapshot();
    }

    /**
     * The admin-forced Grand Witch recruitment number {@code order} (1 = the round's first successful recruitment), or
     * null. Read from the OVERWORLD instance only; server-only state, never synced.
     * 管理员强制指定的第 {@code order} 次大魔女招募（1 = 本局第一次成功招募），没有时为 null。只从主世界实例读取；
     * 仅服务端状态，从不同步。
     */
    public @Nullable ForcedRecruit getForcedRecruit(int order) {
        return forcedRecruits.get(order);
    }

    /** Removes and returns the entry for {@code order}. / 移除并返回 {@code order} 的条目。 */
    public @Nullable ForcedRecruit removeForcedRecruit(int order) {
        return forcedRecruits.remove(order);
    }

    /** Immutable snapshot in ascending order. / 按序号升序的不可变快照。 */
    public SortedMap<Integer, ForcedRecruit> getForcedRecruits() {
        return forcedRecruits.snapshot();
    }

    /**
     * Decides a {@code /sparkwitch:forceAccompliceRole} request without storing it. {@code requestedOrder} null picks
     * the next free pending order; {@code recruitedCount} is the running round's count, 0 when no round runs.
     * 判定一次 {@code /sparkwitch:forceAccompliceRole} 请求但不写入。{@code requestedOrder} 为 null 时取下一个空闲的
     * 待生效序号；{@code recruitedCount} 为正在进行对局的已招募次数，无对局时为 0。
     */
    public ForcedRecruitPlan planForcedRecruit(
            ForcedRecruit recruit,
            boolean special,
            @Nullable Integer requestedOrder,
            int recruitedCount
    ) {
        return forcedRecruits.plan(recruit, special, requestedOrder, recruitedCount);
    }

    public void applyForcedRecruit(ForcedRecruitPlan.Accepted plan) {
        forcedRecruits.apply(plan);
    }

    /**
     * Drops every forced recruitment. Round end calls it on the overworld store even when the round ran in another
     * world; {@link #clearRoundState()} covers a round on the overworld itself.
     * 清除所有强制招募。局末即使对局在其他世界进行，也会对主世界存储调用；对局在主世界时由 {@link #clearRoundState()} 清除。
     */
    public void clearForcedRecruits() {
        forcedRecruits.clearAll();
    }

    public WraithSettings getWraithSettings() {
        return wraithSettings;
    }

    public void setWraithChance(int chance) {
        wraithSettings = new WraithSettings(chance, wraithSettings.minimum(), wraithSettings.dividend());
    }

    public void setWraithMinimum(int minimum) {
        wraithSettings = new WraithSettings(wraithSettings.chance(), minimum, wraithSettings.dividend());
    }

    public void setWraithDividend(int dividend) {
        wraithSettings = new WraithSettings(wraithSettings.chance(), wraithSettings.minimum(), dividend);
    }

    public boolean isInstinctObscured() {
        return instinctObscureTicks > 0;
    }

    public int getInstinctObscureTicks() {
        return instinctObscureTicks;
    }

    public int getFearTicks() {
        return fearTicks;
    }

    /**
     * Exposes only the Grand Witch countdown state needed by its owning runtime Module.
     * 只向大魔女运行时 Module 暴露其倒计时所需的状态快照。
     */
    public GrandWitchRuntimeState grandWitchRuntimeState() {
        return new GrandWitchRuntimeState(instinctObscureTicks, obscureActionbarTicks, fearTicks);
    }

    public void applyGrandWitchRuntimeState(GrandWitchRuntimeState state) {
        instinctObscureTicks = state.instinctObscureTicks();
        obscureActionbarTicks = state.obscureActionbarTicks();
        fearTicks = state.fearTicks();
    }

    public record GrandWitchRuntimeState(
            int instinctObscureTicks,
            int obscureActionbarTicks,
            int fearTicks
    ) {
    }

    public boolean hasGrandWitchCeremonialSwordBgm() {
        return grandWitchCeremonialSwordBgmSourceCount() > 0;
    }

    public int grandWitchCeremonialSwordBgmSourceCount() {
        return usesLocalGrandWitchCeremonialSwordBgmSources()
                ? grandWitchCeremonialSwordBgmSources.size()
                : syncedGrandWitchCeremonialSwordBgmSources;
    }

    public void startInstinctObscure(int durationTicks) {
        instinctObscureTicks = Math.max(0, durationTicks);
        obscureActionbarTicks = 0;
        sync();
    }

    public void startFear(int durationTicks) {
        fearTicks = Math.max(0, durationTicks);
        sync();
    }

    /**
     * Tracks Grand Witch skill BGM by player UUID so overlapping casts keep the ambience alive.
     * 按玩家 UUID 记录大魔女技能 BGM 来源，多个技能窗口重叠时不会误停全场环境音。
     */
    public void startGrandWitchCeremonialSwordBgm(UUID playerUuid) {
        if (!grandWitchCeremonialSwordBgmSources.start(playerUuid)) {
            return;
        }
        syncedGrandWitchCeremonialSwordBgmSources = grandWitchCeremonialSwordBgmSources.size();
        sync();
    }

    public void stopGrandWitchCeremonialSwordBgm(UUID playerUuid) {
        if (!grandWitchCeremonialSwordBgmSources.stop(playerUuid)) {
            return;
        }
        syncedGrandWitchCeremonialSwordBgmSources = grandWitchCeremonialSwordBgmSources.size();
        sync();
    }

    public boolean markSaintKarma(UUID playerUuid) {
        return saintKarmaState.mark(playerUuid);
    }

    public boolean hasSaintKarma(UUID playerUuid) {
        return saintKarmaState.isMarked(playerUuid);
    }

    public int getSaintKarmaTicks(UUID playerUuid) {
        return saintKarmaState.remainingTicks(playerUuid);
    }

    public int triggerSaintKarma(UUID playerUuid, int durationTicks) {
        return saintKarmaState.trigger(playerUuid, durationTicks);
    }

    public boolean clearSaintKarma(UUID playerUuid) {
        return saintKarmaState.unmark(playerUuid);
    }

    public boolean exemptSaintKarmaAdminCleared(UUID playerUuid, Identifier itemId) {
        return saintKarmaState.exemptAdminCleared(playerUuid, itemId);
    }

    public boolean isSaintKarmaAdminCleared(UUID playerUuid, Identifier itemId) {
        return saintKarmaState.isAdminCleared(playerUuid, itemId);
    }

    public void tickSaintKarmaState() {
        saintKarmaState.tick();
    }

    public void clearRoundState() {
        instinctObscureTicks = 0;
        obscureActionbarTicks = 0;
        fearTicks = 0;
        grandWitchCeremonialSwordBgmSources.clear();
        syncedGrandWitchCeremonialSwordBgmSources = 0;
        saintKarmaState.clear();
        forcedWraithPromotions.clearAll();
        forcedRecruits.clearAll();
        sync();
    }

    public void sync() {
        if (world != null) {
            KEY.sync(world);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        GrandWitchWorldRuntime.tick(serverWorld, this);
        SaintKarmaRuntime.tick(serverWorld, this);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        writeIdentifierSet(buf, disabledSkills);
        buf.writeVarInt(instinctObscureTicks);
        buf.writeVarInt(fearTicks);
        buf.writeVarInt(grandWitchCeremonialSwordBgmSources.size());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        readIdentifierSet(buf, disabledSkills);
        instinctObscureTicks = Math.max(0, buf.readVarInt());
        fearTicks = Math.max(0, buf.readVarInt());
        syncedGrandWitchCeremonialSwordBgmSources = Math.max(0, buf.readVarInt());
        obscureActionbarTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.put("DisabledSkills", toNbt(disabledSkills));
        tag.put("ForcedWraithPromotions", forcedWraithPromotions.toNbt());
        tag.put(ForcedRecruitQueue.NBT_KEY, forcedRecruits.toNbt());
        WraithSettingsNbtCodec.writeWorld(tag, wraithSettings);
        if (instinctObscureTicks > 0) {
            tag.putInt("InstinctObscureTicks", instinctObscureTicks);
        }
        if (fearTicks > 0) {
            tag.putInt("FearTicks", fearTicks);
        }
        NbtList saintKarma = new NbtList();
        for (SaintKarmaState.Entry state : saintKarmaState.entries()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Player", state.playerUuid().toString());
            if (state.remainingTicks() > 0) {
                entry.putInt("RemainingTicks", state.remainingTicks());
            }
            saintKarma.add(entry);
        }
        tag.put("SaintKarma", saintKarma);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        disabledSkills.clear();
        forcedWraithPromotions.clearAll();
        grandWitchCeremonialSwordBgmSources.clear();
        syncedGrandWitchCeremonialSwordBgmSources = 0;
        saintKarmaState.clear();
        fromNbt(tag.getList("DisabledSkills", NbtElement.STRING_TYPE), disabledSkills);
        forcedWraithPromotions.readFromNbt(tag, "ForcedWraithPromotions");
        forcedRecruits.readFromNbt(tag);
        wraithSettings = WraithSettingsNbtCodec.readWorld(tag);
        instinctObscureTicks = tag.contains("InstinctObscureTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("InstinctObscureTicks"))
                : 0;
        fearTicks = tag.contains("FearTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("FearTicks"))
                : 0;
        NbtList saintKarma = tag.getList("SaintKarma", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < saintKarma.size(); index++) {
            NbtCompound entry = saintKarma.getCompound(index);
            try {
                UUID playerUuid = UUID.fromString(entry.getString("Player"));
                saintKarmaState.restore(playerUuid, entry.getInt("RemainingTicks"));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy/manual data while preserving all valid round entries.
                // 忽略损坏的旧版或手工数据，同时保留本局其他有效业障条目。
            }
        }
        obscureActionbarTicks = 0;
    }

    private static NbtList toNbt(Collection<Identifier> ids) {
        NbtList list = new NbtList();
        for (Identifier id : ids) {
            list.add(NbtString.of(id.toString()));
        }
        return list;
    }

    private static void fromNbt(NbtList list, Set<Identifier> ids) {
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
    }

    private static void writeIdentifierSet(RegistryByteBuf buf, Collection<Identifier> ids) {
        buf.writeVarInt(ids.size());
        for (Identifier id : ids) {
            buf.writeString(id.toString());
        }
    }

    private static void readIdentifierSet(RegistryByteBuf buf, Set<Identifier> ids) {
        ids.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            Identifier id = Identifier.tryParse(buf.readString());
            if (id != null) {
                ids.add(id);
            }
        }
    }

    private boolean usesLocalGrandWitchCeremonialSwordBgmSources() {
        return world == null || world instanceof ServerWorld;
    }
}
