package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 魔术师本局运行态；只同步 HUD 所需的小字段，轨迹保留在服务端内存。
 * Synced to the owner only (stage, cooldown, chosen disguise and the round roster); everyone else would otherwise spot
 * the Magician by its running cooldown. The roster (owner decision 2026-10-07 D7) is every round participant captured
 * at round start; any roster member may be picked, alive or dead, so a pick never reveals a death.
 * 只同步给本人（阶段、冷却、所选伪装与本局名单）；否则其他人可凭进行中的冷却认出魔术师。名单（所有者 2026-10-07 决定 D7）
 * 是回合开始时记录的全部参与者；名单内任何人都可选，无论生死，因此选择永远不会暴露死亡。
 * The running cooldown and stage timers sync only when the HUD's shown second changes (their 20-tick boundaries, which
 * the old 5-tick cadence also hit); the client counts down in between without crossing the shown second, so every HUD
 * line, READY included, changes exactly when the server's sync lands.
 * 进行中的冷却与阶段计时只在 HUD 显示的秒数变化时同步（即 20 刻边界，旧的 5 刻节奏同样会命中）；客户端在两次同步之间自行倒数，
 * 但不会越过当前显示的秒数，因此每行 HUD（包括 READY）都恰在服务端同步到达时变化。
 */
public final class MagicianPlayerComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<MagicianPlayerComponent> KEY = ComponentRegistry.getOrCreate(SparkWitch.id("magician"), MagicianPlayerComponent.class);
    final PlayerEntity player;
    private UUID selectedTarget;
    private String selectedName = "";
    private MagicianStage stage = MagicianStage.IDLE;
    private int stageTicks;
    private int recordedTicks;
    private int cooldownTicks;
    private final List<MagicianReplayFrame> frames = new ArrayList<>();
    private final List<MagicianRecordedAction> actions = new ArrayList<>();
    private final List<RosterEntry> roster = new ArrayList<>();
    /** Server: nesting depth of {@link #syncOnceAfter}. Client: local countdown floors. / 服务端：合并同步深度；客户端：本地倒数下限。 */
    private int syncHold;
    private int clientCooldownFloor;
    private int clientStageFloor;
    /** One round participant the Magician may copy. / 魔术师可复制的一名本局参与者。 */
    public record RosterEntry(UUID uuid, String name) {}
    public MagicianPlayerComponent(PlayerEntity player) { this.player = player; this.selectedTarget = safeOwnUuid(); this.selectedName = safeOwnName(); }
    public UUID selectedTarget() { return selectedTarget == null ? safeOwnUuid() : selectedTarget; }
    PlayerEntity player() { return player; }
    public String selectedName() { return selectedName == null || selectedName.isBlank() ? safeOwnName() : selectedName; }
    public MagicianStage stage() { return stage; }
    public int stageTicks() { return stageTicks; }
    public int cooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int ticks) { cooldownTicks = Math.max(0, ticks); sync(); }
    public List<MagicianReplayFrame> frames() { return List.copyOf(frames); }
    public List<MagicianRecordedAction> actions() { return List.copyOf(actions); }
    public void setSelectedTarget(UUID uuid, String name) { selectedTarget=uuid; selectedName=name; sync(); }
    public List<RosterEntry> roster() { return List.copyOf(roster); }
    public void setRoster(List<RosterEntry> entries) { roster.clear(); roster.addAll(entries); sync(); }
    /** The roster entry for {@code uuid}, or null when that player was not in this round. / 名单中的条目；不在本局时为 null。 */
    public @Nullable RosterEntry rosterEntry(UUID uuid) { for (RosterEntry entry : roster) if (entry.uuid().equals(uuid)) return entry; return null; }
    /** Forced-cooldown write: only ever lengthens. / 强制冷却写入：只会延长。 */
    public void raiseCooldownTicks(int ticks) { if (ticks > cooldownTicks) { cooldownTicks = ticks; sync(); } }
    public void reset() { MagicianPlaybackManager.stopPlaybackSilently(player); MagicianPlaybackManager.clearCachedRecording(player); MagicianAbility.forget(safeOwnUuid()); stage=MagicianStage.IDLE; stageTicks=0; recordedTicks=0; cooldownTicks=0; frames.clear(); actions.clear(); roster.clear(); selectedTarget=safeOwnUuid(); selectedName=safeOwnName(); sync(); }
    public void assignInitialCooldown() { cooldownTicks = MagicianConstants.INITIAL_COOLDOWN_TICKS; sync(); }
    public void startRecording() { if (cooldownTicks > 0) return; MagicianPlaybackManager.clearCachedRecording(player); frames.clear(); actions.clear(); recordedTicks=0; stage=MagicianStage.RECORDING; stageTicks=MagicianConstants.RECORD_DURATION_TICKS; frames.add(MagicianReplayFrame.capture(player)); if(player instanceof ServerPlayerEntity sp) GameRecordManager.recordGlobalEvent(sp.getServerWorld(), MagicianReplayEvents.RECORDING_STARTED, sp, null); sync(); }
    public void finishRecording() { finishRecording(false); }
    private void finishRecording(boolean early) { if(stage!=MagicianStage.RECORDING)return; stage=MagicianStage.READY_PLAYBACK; stageTicks=0; MagicianPlaybackManager.cache(this); if(player instanceof ServerPlayerEntity sp) GameRecordManager.recordGlobalEvent(sp.getServerWorld(), early ? MagicianReplayEvents.RECORDING_STOPPED_EARLY : MagicianReplayEvents.RECORDING_FINISHED, sp, null); sync(); }
    public void startPlayback() { if (!(player instanceof ServerPlayerEntity serverPlayer)) return; MagicianPlaybackManager.startPlayback(serverPlayer); }
    public void stopRecordingEarly() { finishRecording(true); }
    public void record(MagicianRecordedAction.Type type) { if(stage==MagicianStage.RECORDING) actions.add(switch(type) { case ATTACK -> MagicianRecordedAction.attack(recordedTicks); case RELEASE_USE_ITEM -> MagicianRecordedAction.release(recordedTicks); case GUN_SHOOT -> MagicianRecordedAction.gun(recordedTicks); case KNIFE_STAB -> MagicianRecordedAction.knife(recordedTicks); default -> new MagicianRecordedAction(recordedTicks,type,null,0,0,0,0,null,null,false); }); }
    public void recordUse(Hand hand) { if(stage==MagicianStage.RECORDING) actions.add(MagicianRecordedAction.use(recordedTicks, hand)); }
    public void recordBlockUse(Hand hand, BlockHitResult hit) { if(stage==MagicianStage.RECORDING) actions.add(MagicianRecordedAction.useBlock(recordedTicks, hand, hit)); }
    public void recordSwing(Hand hand) { if(stage==MagicianStage.RECORDING) actions.add(MagicianRecordedAction.swing(recordedTicks, hand)); }
    public void recordSlot(int slot) { if(stage==MagicianStage.RECORDING) actions.add(MagicianRecordedAction.slot(recordedTicks, slot)); }
    @Override public void serverTick() {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        // One sync per tick at most, and only when a shown second changed (finishRecording syncs everything itself).
        // 每刻至多同步一次，且只在显示的秒数变化时同步（finishRecording 自身会同步全部状态）。
        boolean resync = false;
        if (cooldownTicks > 0) { cooldownTicks--; resync = Cadence.shownSecondChanged(cooldownTicks + 1, cooldownTicks); }
        if (stage==MagicianStage.RECORDING) { recordedTicks++; frames.add(MagicianReplayFrame.capture(player)); if(--stageTicks<=0) { finishRecording(); return; } resync |= Cadence.shownSecondChanged(stageTicks + 1, stageTicks); }
        else if(stage==MagicianStage.PLAYING) { int before=stageTicks; stageTicks=MagicianPlaybackManager.remaining(player); resync |= Cadence.shownSecondChanged(before, stageTicks); }
        if (resync) sync();
    }
    @Override public void clientTick() {
        if (cooldownTicks > clientCooldownFloor) cooldownTicks--;
        if ((stage==MagicianStage.RECORDING || stage==MagicianStage.PLAYING) && stageTicks > clientStageFloor) stageTicks--;
    }
    void beginPlaying(int ticks) { stage=MagicianStage.PLAYING; stageTicks=ticks; sync(); }
    void finishPlaying() { finishPlaying(true); }
    // A forced cooldown raised during playback is kept: the playback cooldown never shortens it.
    // 播放期间被强制抬高的冷却会保留：播放冷却绝不缩短它。
    void finishPlaying(boolean applyCooldown) { stage=MagicianStage.IDLE; stageTicks=0; if (applyCooldown) cooldownTicks=Math.max(cooldownTicks, MagicianConstants.PLAYBACK_COOLDOWN_TICKS); frames.clear(); actions.clear(); sync(); }
    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) { if(selectedTarget!=null)tag.putUuid("SelectedTarget",selectedTarget); tag.putString("SelectedName",selectedName); tag.putString("Stage",stage.name()); tag.putInt("StageTicks",stageTicks); tag.putInt("CooldownTicks",cooldownTicks); NbtList list=new NbtList(); for(RosterEntry entry:roster){NbtCompound e=new NbtCompound(); e.putUuid("Uuid",entry.uuid()); e.putString("Name",entry.name()); list.add(e);} tag.put("Roster",list); }
    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) { selectedTarget=tag.containsUuid("SelectedTarget")?tag.getUuid("SelectedTarget"):player.getUuid(); selectedName=tag.getString("SelectedName"); try{stage=MagicianStage.valueOf(tag.getString("Stage"));}catch(Exception e){stage=MagicianStage.IDLE;} stageTicks=Math.max(0,tag.getInt("StageTicks")); cooldownTicks=Math.max(0,tag.getInt("CooldownTicks")); roster.clear(); for(NbtElement element:tag.getList("Roster",NbtElement.COMPOUND_TYPE)){NbtCompound e=(NbtCompound)element; if(e.containsUuid("Uuid")) roster.add(new RosterEntry(e.getUuid("Uuid"),e.getString("Name")));} if(!player.getWorld().isClient()){frames.clear();actions.clear();stage=MagicianStage.IDLE;stageTicks=0;} }
    /** Owner-only sync. / 只同步给本人。 */
    @Override public boolean shouldSyncWith(ServerPlayerEntity recipient) { return recipient == player; }
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) { buf.writeUuid(selectedTarget()); buf.writeString(selectedName()); buf.writeVarInt(stage.ordinal()); buf.writeVarInt(stageTicks); buf.writeVarInt(cooldownTicks); buf.writeVarInt(roster.size()); for(RosterEntry entry:roster){buf.writeUuid(entry.uuid()); buf.writeString(entry.name());} }
    public void applySyncPacket(RegistryByteBuf buf) { selectedTarget=buf.readUuid(); selectedName=buf.readString(); int ordinal=Math.max(0,Math.min(MagicianStage.values().length-1,buf.readVarInt())); stage=MagicianStage.values()[ordinal]; stageTicks=Math.max(0,buf.readVarInt()); cooldownTicks=Math.max(0,buf.readVarInt()); clientStageFloor=Cadence.clientFloor(stageTicks); clientCooldownFloor=Cadence.clientFloor(cooldownTicks); roster.clear(); int size=buf.readVarInt(); for(int i=0;i<size;i++) roster.add(new RosterEntry(buf.readUuid(), buf.readString())); }
    public void sync(){ if (syncHold == 0) KEY.sync(player); }
    /**
     * Runs {@code writes} (each of which would sync) and syncs once at the end with the final state, e.g. the reset,
     * initial cooldown and roster capture of a role assignment.
     * 执行 {@code writes}（其中每次写入本会各自同步），最后只用最终状态同步一次，例如身份分配时的重置、初始冷却与名单记录。
     */
    public void syncOnceAfter(Runnable writes) { syncHold++; try { writes.run(); } finally { syncHold--; } sync(); }
    /** FakePlayer 在 PlayerEntity 构造早期可能尚未注入 GameProfile，不能调用 getName。 */
    private UUID safeOwnUuid() { try { return player.getUuid(); } catch (Throwable ignored) { return new UUID(0L, 0L); } }
    private String safeOwnName() {
        try {
            var profile = player.getGameProfile();
            if (profile != null && profile.getName() != null && !profile.getName().isBlank()) return profile.getName();
        } catch (Throwable ignored) { }
        return "";
    }
    /**
     * Pure sync cadence of the HUD timers, matching {@code MagicianStatusHudRenderer}'s shown seconds
     * ({@code ceil(ticks / 20)}). Nested so tests reach it without loading the component key.
     * HUD 计时的纯同步节奏，与 {@code MagicianStatusHudRenderer} 显示的秒数（{@code ceil(ticks / 20)}）一致。
     * 作为嵌套类，测试无需加载组件键即可访问。
     */
    static final class Cadence {
        private Cadence() {}
        static int shownSeconds(int ticks) { return Math.max(0, (ticks + 19) / 20); }
        static boolean shownSecondChanged(int before, int after) { return shownSeconds(before) != shownSeconds(after); }
        /**
         * Lowest local value that still shows the same second (at least 1 while running, so READY waits for the server).
         * 仍显示同一秒的最低本地值（计时中至少为 1，因此 READY 会等待服务端）。
         */
        static int clientFloor(int ticks) { return ticks <= 0 ? 0 : (shownSeconds(ticks) - 1) * 20 + 1; }
    }
}
