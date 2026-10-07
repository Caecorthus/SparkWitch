package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 魔术师本局运行态；只同步 HUD 所需的小字段，轨迹保留在服务端内存。 */
public final class MagicianPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
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
    public void reset() { MagicianPlaybackManager.stopPlaybackSilently(player); MagicianPlaybackManager.clearCachedRecording(player); stage=MagicianStage.IDLE; stageTicks=0; recordedTicks=0; cooldownTicks=0; frames.clear(); actions.clear(); selectedTarget=safeOwnUuid(); selectedName=safeOwnName(); sync(); }
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
        if (cooldownTicks > 0) { cooldownTicks--; if (cooldownTicks % 5 == 0) sync(); }
        if (stage==MagicianStage.RECORDING) { recordedTicks++; frames.add(MagicianReplayFrame.capture(player)); if(--stageTicks<=0) finishRecording(); else if(stageTicks%5==0) sync(); }
        else if(stage==MagicianStage.PLAYING) { stageTicks=MagicianPlaybackManager.remaining(player); if(stageTicks%5==0) sync(); }
    }
    void beginPlaying(int ticks) { stage=MagicianStage.PLAYING; stageTicks=ticks; sync(); }
    void finishPlaying() { finishPlaying(true); }
    void finishPlaying(boolean applyCooldown) { stage=MagicianStage.IDLE; stageTicks=0; if (applyCooldown) cooldownTicks=MagicianConstants.PLAYBACK_COOLDOWN_TICKS; frames.clear(); actions.clear(); sync(); }
    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) { if(selectedTarget!=null)tag.putUuid("SelectedTarget",selectedTarget); tag.putString("SelectedName",selectedName); tag.putString("Stage",stage.name()); tag.putInt("StageTicks",stageTicks); tag.putInt("CooldownTicks",cooldownTicks); }
    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) { selectedTarget=tag.containsUuid("SelectedTarget")?tag.getUuid("SelectedTarget"):player.getUuid(); selectedName=tag.getString("SelectedName"); try{stage=MagicianStage.valueOf(tag.getString("Stage"));}catch(Exception e){stage=MagicianStage.IDLE;} stageTicks=Math.max(0,tag.getInt("StageTicks")); cooldownTicks=Math.max(0,tag.getInt("CooldownTicks")); if(!player.getWorld().isClient()){frames.clear();actions.clear();stage=MagicianStage.IDLE;stageTicks=0;} }
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) { buf.writeUuid(selectedTarget()); buf.writeString(selectedName()); buf.writeVarInt(stage.ordinal()); buf.writeVarInt(stageTicks); buf.writeVarInt(cooldownTicks); }
    public void applySyncPacket(RegistryByteBuf buf) { selectedTarget=buf.readUuid(); selectedName=buf.readString(); int ordinal=Math.max(0,Math.min(MagicianStage.values().length-1,buf.readVarInt())); stage=MagicianStage.values()[ordinal]; stageTicks=Math.max(0,buf.readVarInt()); cooldownTicks=Math.max(0,buf.readVarInt()); }
    public void sync(){ KEY.sync(player); }
    /** FakePlayer 在 PlayerEntity 构造早期可能尚未注入 GameProfile，不能调用 getName。 */
    private UUID safeOwnUuid() { try { return player.getUuid(); } catch (Throwable ignored) { return new UUID(0L, 0L); } }
    private String safeOwnName() {
        try {
            var profile = player.getGameProfile();
            if (profile != null && profile.getName() != null && !profile.getName().isBlank()) return profile.getName();
        } catch (Throwable ignored) { }
        return "未知玩家";
    }
}
