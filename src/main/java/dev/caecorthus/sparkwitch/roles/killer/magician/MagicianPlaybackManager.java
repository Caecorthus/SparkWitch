package dev.caecorthus.sparkwitch.roles.killer.magician;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleAccess;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
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
    public static void init() {
        if (initialized) return;
        initialized = true;
        // The puppet is discarded even when its owner went offline, so a finished timeline never leaves a puppet behind.
        // A replayed action that ended this very puppet (endPuppet) already removed and finished it.
        // 即使主人已离线也会移除皮套，播放结束后不会遗留皮套。回放动作若已结束该皮套（endPuppet），则它已被移除并收尾。
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.entrySet().removeIf(e -> {
            Active active = e.getValue();
            if (!active.tick() || ACTIVE.get(e.getKey()) != active) return false;
            active.entity.discard();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(e.getKey());
            if (owner != null) finish(owner, false, MagicianReplayEvents.PLAYBACK_FINISHED, null);
            return true;
        }));
        // Fabric may fire DISCONNECT on the Netty thread; the cleanup runs on the server thread.
        // Fabric 可能在 Netty 线程触发 DISCONNECT；清理在服务端线程执行。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            server.execute(() -> onDisconnect(id));
        });
    }
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
    public static void stopPlaybackSilently(net.minecraft.entity.player.PlayerEntity p) { if (p instanceof ServerPlayerEntity sp) stop(sp, true, null, MagicianReplayEvents.PLAYBACK_FINISHED, null, null, null); }
    public static void stopPlaybackEarly(ServerPlayerEntity owner) { stop(owner, false, null, MagicianReplayEvents.PLAYBACK_STOPPED_EARLY, null, null, null); }

    /**
     * Whether this puppet is a live, running playback (server only). A discarded puppet or one whose timeline already
     * ended is not live.
     * 该皮套是否为仍在播放的活皮套（仅服务端）。已移除或时间线已结束的皮套不算。
     */
    public static boolean isLive(@Nullable MagicianPlaybackEntity puppet) {
        if (puppet == null || puppet.isRemoved() || puppet.getWorld().isClient()) return false;
        UUID owner = puppet.owner();
        Active active = owner == null ? null : ACTIVE.get(owner);
        return active != null && active.entity == puppet;
    }

    /** Whether {@code player} is the Magician who owns this puppet. / {@code player} 是否为该皮套的主人。 */
    public static boolean isOwnedBy(@Nullable MagicianPlaybackEntity puppet, @Nullable PlayerEntity player) {
        return puppet != null && player != null && player.getUuid().equals(puppet.owner());
    }

    /**
     * Every live puppet in {@code world}, snapshotted so ending one never changes the list others see.
     * {@code world} 中所有活皮套的快照，结束其中一个不会影响其余判定。
     */
    public static List<MagicianPlaybackEntity> livePuppets(ServerWorld world) {
        List<MagicianPlaybackEntity> puppets = new ArrayList<>();
        for (Active active : ACTIVE.values()) {
            if (active.entity.getWorld() == world && !active.entity.isRemoved()) puppets.add(active.entity);
        }
        return puppets;
    }

    /**
     * The single forced-end path: every validated weapon hit on a puppet ends here (see {@link MagicianPuppetHits}).
     * Leaves a decoy body (cover role Civilian, owner decision 2026-10-07 D1) with {@code deathReason}, pays the
     * Magician the reward when anyone other than the Magician ended it (D4, blasts included), records the replay
     * event, and starts the playback cooldown. Returns false when the puppet is not live. Callers validate the weapon,
     * reach, aim and line of sight first; this method trusts them.
     * 唯一的强制结束路径：所有经校验的武器命中皮套都汇入此处（见 {@link MagicianPuppetHits}）。按 {@code deathReason}
     * 留下诱饵尸体（掩护身份为平民，所有者 2026-10-07 决定 D1）；只要结束者不是魔术师本人就向魔术师支付奖励
     * （D4，含爆炸）；记录回放事件并进入播放冷却。皮套不是活皮套时返回 false。调用方须先校验武器、距离、瞄准与视线，
     * 本方法直接信任调用方。
     */
    public static boolean endPuppet(MagicianPlaybackEntity puppet, @Nullable ServerPlayerEntity attacker,
                                    Identifier deathReason, @Nullable String weaponName) {
        if (!isLive(puppet)) return false;
        Active active = ACTIVE.get(puppet.owner());
        if (active == null) return false;
        ServerPlayerEntity owner = active.owner;
        if (attacker != null && !attacker.getUuid().equals(owner.getUuid())) {
            PlayerShopComponent.KEY.get(owner).addToBalance(MagicianConstants.FORCED_END_REWARD_COINS);
        }
        stop(owner, false, puppet, MagicianReplayEvents.PLAYBACK_FORCED_END, attacker, weaponName, deathReason);
        return true;
    }

    /**
     * Ends every live puppet, discarding puppets whose owner is offline; used at round end.
     * 结束所有活皮套（主人离线的皮套直接移除）；用于回合结束。
     */
    public static void clearAll() {
        for (Map.Entry<UUID, Active> entry : List.copyOf(ACTIVE.entrySet())) {
            if (ACTIVE.remove(entry.getKey(), entry.getValue())) entry.getValue().entity.discard();
        }
        READY.clear();
    }

    /**
     * Disconnect cleanup: discards the owner's puppet and drops the cached recording and the ability debounce entry.
     * 断线清理：移除主人的皮套，并丢弃缓存录制与技能去抖记录。
     */
    static void onDisconnect(UUID id) {
        Active active = ACTIVE.remove(id);
        if (active != null) active.entity.discard();
        READY.remove(id);
        MagicianAbility.forget(id);
    }

    private static void stop(
            ServerPlayerEntity owner,
            boolean silent,
            @Nullable MagicianPlaybackEntity hit,
            Identifier eventId,
            @Nullable ServerPlayerEntity attacker,
            @Nullable String weapon,
            @Nullable Identifier deathReason
    ) {
        if(owner==null)return; Active a=ACTIVE.remove(owner.getUuid()); MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(owner);
        if(a==null && c.stage()!=MagicianStage.PLAYING)return;
        if(a!=null){ if(hit!=null && deathReason!=null) spawnCorpse(owner,hit,deathReason); a.entity.discard(); }
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
        finish(owner, silent, eventId, data);
    }

    private static void finish(ServerPlayerEntity owner, boolean silent, Identifier eventId, @Nullable NbtCompound data) {
        MagicianPlayerComponent.KEY.get(owner).finishPlaying(!silent);
        if (!silent) GameRecordManager.recordGlobalEvent(owner.getServerWorld(), eventId, owner, data);
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
        // Decoy (owner decisions 2026-10-07 D1/D2): every body-role display reads the Civilian cover role instead of
        // the copied player's live role, and the server-only registry lets Prophet/Vulture/Kidnapper skip it.
        // 诱饵（所有者 2026-10-07 决定 D1/D2）：所有尸体身份显示读取平民掩护身份而非被复制玩家的实时身份；
        // 仅服务端的登记表让先知、秃鹫与绑匪跳过它。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(WatheRoles.CIVILIAN.identifier());
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
        if (owner.getWorld().spawnEntity(body)) MagicianDecoyBodies.mark(body);
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
