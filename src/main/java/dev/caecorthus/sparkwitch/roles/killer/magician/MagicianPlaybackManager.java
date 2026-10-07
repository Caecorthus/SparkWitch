package dev.caecorthus.sparkwitch.roles.killer.magician;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 回放实体、代理玩家和动作时间线的统一生命周期管理器。 */
public final class MagicianPlaybackManager {
    /**
     * 录制完成后的服务端快照。录制帧和动作属于 transient 数据，不能只依赖
     * CCA 组件本身，否则组件读写/同步时被清空后，客户端仍显示可播放但服务端
     * 会因为列表为空直接放弃播放。
     */
    private static final Map<UUID, RecordingData> READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static boolean initialized;
    private MagicianPlaybackManager() {}
    public static void init() { if (initialized) return; initialized=true; ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.entrySet().removeIf(e -> { if (e.getValue().tick()) { stop(server.getPlayerManager().getPlayer(e.getKey()), false, null); return true; } return false; })); }
    public static void cache(MagicianPlayerComponent c) {
        if (c.player() instanceof ServerPlayerEntity p) {
            RecordingData recording = RecordingData.from(c);
            if (recording != null) READY.put(p.getUuid(), recording);
        }
    }
    public static void startPlayback(ServerPlayerEntity owner) {
        MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(owner);
        RecordingData recording = RecordingData.from(c);
        if (recording == null) recording = READY.get(owner.getUuid());
        // 组件 transient 列表已被清空时，从录制完成快照恢复；两者都没有才算真正不可播放。
        if (recording == null || c.cooldownTicks() > 0) return;
        Active old=ACTIVE.remove(owner.getUuid()); if (old != null) old.entity.discard();
        MagicianReplayFrame first=recording.frames().getFirst();
        MagicianPlaybackEntity entity=new MagicianPlaybackEntity(SparkWitchEntities.magicianPlayback(), owner.getServerWorld());
        entity.setIdentity(owner.getUuid(), c.selectedTarget(), c.selectedName()); first.applyTo(entity);
        MagicianPlaybackFakePlayer proxy=new MagicianPlaybackFakePlayer(owner.getServerWorld(), new GameProfile(owner.getUuid(), "[MagicianReplay]"), owner.getUuid(), c.selectedTarget(), c.selectedName()); first.applyToProxy(proxy);
        if (!owner.getServerWorld().spawnEntity(entity)) return;
        ACTIVE.put(owner.getUuid(), new Active(owner,c,entity,proxy,recording.frames(),recording.actions())); c.beginPlaying(recording.frames().size());
        READY.remove(owner.getUuid());
        NbtCompound data = new NbtCompound();
        putPlayerReference(data, "disguise_player", "disguise_name", c.selectedTarget(), c.selectedName());
        GameRecordManager.recordGlobalEvent(owner.getServerWorld(), MagicianReplayEvents.PLAYBACK_STARTED, owner, data);
    }
    public static int remaining(net.minecraft.entity.player.PlayerEntity p) { Active a=ACTIVE.get(p.getUuid()); return a==null?0:Math.max(0,a.frames.size()-a.index); }
    public static boolean hasCachedRecording(PlayerEntity player) { return READY.containsKey(player.getUuid()); }
    public static void clearCachedRecording(PlayerEntity player) { READY.remove(player.getUuid()); }
    public static boolean isPlaybackEntity(Object e) { return e instanceof MagicianPlaybackEntity; }
    public static MagicianPlaybackEntity find(Object e) { return e instanceof MagicianPlaybackEntity p?p:null; }
    public static void stopPlaybackSilently(net.minecraft.entity.player.PlayerEntity p) { if (p instanceof ServerPlayerEntity sp) stop(sp,true,null); }
    public static void stopPlaybackEarly(ServerPlayerEntity owner) { stop(owner, false, null, MagicianReplayEvents.PLAYBACK_STOPPED_EARLY); }
    public static void stopByWeapon(MagicianPlaybackEntity entity, ServerPlayerEntity attacker, String weapon) {
        UUID ownerId=entity.owner(); if(ownerId==null || attacker.getServer()==null)return; ServerPlayerEntity owner=attacker.getServer().getPlayerManager().getPlayer(ownerId); if(owner==null)return;
        if(!attacker.getUuid().equals(ownerId)) PlayerShopComponent.KEY.get(owner).addToBalance(MagicianConstants.FORCED_END_REWARD_COINS);
        stop(owner,false,entity,MagicianReplayEvents.PLAYBACK_FORCED_END,attacker,weapon);
    }
    /**
     * 手雷爆炸不经过 Wathe 的真实玩家枪械/匕首目标包，因此由手雷 mixin 在爆炸前
     * 直接按范围收束皮套。皮套被视为真正的手雷伤害目标，并生成正确死亡原因的尸体。
     */
    public static void stopByExplosion(ServerWorld world, Box explosionBox, @Nullable ServerPlayerEntity attacker, String weapon) {
        for (Active active : List.copyOf(ACTIVE.values())) {
            if (active.owner.getServerWorld() != world || !active.entity.getBoundingBox().intersects(explosionBox)) continue;
            stop(active.owner, false, active.entity, MagicianReplayEvents.PLAYBACK_FORCED_END,
                    attacker, weapon, GameConstants.DeathReasons.GRENADE);
        }
    }
    private static void stop(ServerPlayerEntity owner, boolean silent, MagicianPlaybackEntity hit) {
        stop(owner, silent, hit, hit == null ? MagicianReplayEvents.PLAYBACK_FINISHED : MagicianReplayEvents.PLAYBACK_FORCED_END, null, null);
    }
    private static void stop(
            ServerPlayerEntity owner,
            boolean silent,
            MagicianPlaybackEntity hit,
            Identifier eventId
    ) {
        stop(owner, silent, hit, eventId, null, null);
    }
    private static void stop(
            ServerPlayerEntity owner,
            boolean silent,
            MagicianPlaybackEntity hit,
            Identifier eventId,
            ServerPlayerEntity attacker,
            String weapon
    ) {
        stop(owner, silent, hit, eventId, attacker, weapon, GameConstants.DeathReasons.GUN);
    }
    private static void stop(
            ServerPlayerEntity owner,
            boolean silent,
            MagicianPlaybackEntity hit,
            Identifier eventId,
            ServerPlayerEntity attacker,
            String weapon,
            Identifier deathReason
    ) {
        if(owner==null)return; Active a=ACTIVE.remove(owner.getUuid()); MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(owner);
        if(a==null && c.stage()!=MagicianStage.PLAYING)return;
        if(a!=null){ if(hit!=null) spawnCorpse(owner,hit,deathReason); a.entity.discard(); }
        c.finishPlaying(!silent);
        if(!silent) {
            NbtCompound data = null;
            if (hit != null) {
                data = new NbtCompound();
                putPlayerReference(data, "disguise_player", "disguise_name", hit.disguise(), hit.disguiseName());
                if (attacker != null) {
                    putPlayerReference(data, "attacker_player", "attacker_name", attacker.getUuid(), attacker.getName().getString());
                }
                if (weapon != null && !weapon.isBlank()) {
                    data.putString("weapon_name", weapon);
                }
            }
            GameRecordManager.recordGlobalEvent(owner.getServerWorld(), eventId, owner, data);
        }
    }
    private static void putPlayerReference(
            NbtCompound data,
            String uuidKey,
            String nameKey,
            UUID uuid,
            String name
    ) {
        if (uuid != null) {
            data.putUuid(uuidKey, uuid);
        }
        if (name != null && !name.isBlank()) {
            data.putString(nameKey, name);
        }
    }
    private static void spawnCorpse(ServerPlayerEntity owner, MagicianPlaybackEntity entity, Identifier deathReason) {
        PlayerBodyEntity body=WatheEntities.PLAYER_BODY.create(owner.getWorld());
        if(body==null)return;
        body.setPlayerUuid(entity.disguise());
        body.setDeathReason(deathReason);
        body.setDeathGameTime(owner.getWorld().getTime());
        float corpseYaw = entity.getHeadYaw();
        body.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), corpseYaw, 0.0F);
        // PlayerBodyEntityRenderer 根据 yaw/headYaw/bodyYaw 做“正面朝地”的倒地旋转；
        // 只设置 refreshPositionAndAngles 会留下旧的插值角度，导致头部斜向或瞬间扭转。
        body.setYaw(corpseYaw);
        body.setHeadYaw(corpseYaw);
        body.bodyYaw = entity.bodyYaw;
        body.prevYaw = corpseYaw;
        body.prevHeadYaw = corpseYaw;
        body.prevBodyYaw = entity.bodyYaw;
        owner.getWorld().spawnEntity(body);
    }
    private record RecordingData(List<MagicianReplayFrame> frames, List<MagicianRecordedAction> actions) {
        private static RecordingData from(MagicianPlayerComponent component) {
            List<MagicianReplayFrame> frames = component.frames();
            if (frames.isEmpty()) return null;
            return new RecordingData(List.copyOf(frames), List.copyOf(component.actions()));
        }
    }
    private static final class Active {
        final ServerPlayerEntity owner; final MagicianPlayerComponent component; final MagicianPlaybackEntity entity; final MagicianPlaybackFakePlayer proxy; final java.util.List<MagicianReplayFrame> frames; final java.util.List<MagicianRecordedAction> actions; int index;
        Active(ServerPlayerEntity o,MagicianPlayerComponent c,MagicianPlaybackEntity e,MagicianPlaybackFakePlayer p,java.util.List<MagicianReplayFrame> f,java.util.List<MagicianRecordedAction> a){owner=o;component=c;entity=e;proxy=p;frames=f;actions=a;}
        boolean tick(){
            if(!GameFunctions.isPlayerAliveAndSurvival(owner)||entity.isRemoved()||index>=frames.size())return true;
            MagicianReplayFrame frame=frames.get(index);
            // 可见皮套和服务端代理必须使用同一帧的位置、视角和姿态。
            // 代理是武器/方块/投掷逻辑的真实执行者，漏同步会让所有目标计算失效。
            frame.applyTo(entity);
            frame.applyToProxy(proxy);
            // 匕首的起手音效由 KnifeItem.use 在真实客户端侧播放；回放代理没有客户端，
            // 因此在从未使用切换到使用的第一帧补发一次世界声音。
            if (frame.usingItem() && frame.activeHand() != null
                    && frame.getStackInHand(frame.activeHand()).isOf(WatheItems.KNIFE)
                    && (index == 0 || !frames.get(index - 1).usingItem())) {
                proxy.getWorld().playSound(null, proxy.getX(), proxy.getY(), proxy.getZ(),
                        WatheSounds.ITEM_KNIFE_PREPARE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            for(MagicianRecordedAction action:actions)if(action.tick()==index)MagicianPlaybackActionExecutor.execute(owner,proxy,entity,action);
            index++;
            return index>=frames.size();
        }
    }
}
