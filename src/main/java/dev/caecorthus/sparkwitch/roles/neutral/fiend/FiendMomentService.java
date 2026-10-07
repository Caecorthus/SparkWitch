package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Fiend Moment owner (WP2): the one {@code register()} line in {@link FiendFeatureService}, the purchase that starts
 * the moment, the single end path, and the moment crowbar cooldown. Server authority: only this service starts or ends
 * the synced {@link FiendMomentWorldComponent}; the moment is bound to the Wathe match id it started in.
 * 魔人时刻的归属模块（WP2）：{@link FiendFeatureService} 中唯一的 {@code register()} 行、开启时刻的购买、唯一的结束路径
 * 以及时刻撬棍冷却。服务端权威：只有本服务开始或结束已同步的 {@link FiendMomentWorldComponent}；时刻绑定到开始时的
 * Wathe 对局 id。
 */
public final class FiendMomentService {
    static final String TITLE_KEY = "title.sparkwitch.fiend.moment";
    static final String SUBTITLE_KEY = "title.sparkwitch.fiend.moment.subtitle";
    static final String FELL_KEY = "title.sparkwitch.fiend.fell";
    static final String ENDED_KEY = "title.sparkwitch.fiend.moment_ended";
    private static final int FADE_IN_TICKS = 10;
    private static final int STAY_TICKS = 70;
    private static final int FADE_OUT_TICKS = 20;
    private static final float START_SOUND_VOLUME = 0.8F;
    private static final float START_SOUND_PITCH = 1.0F;
    private static boolean registered;

    private FiendMomentService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FiendEconomyService.register();
        FiendShopService.register();
        FiendReplayFormatters.register();
        FiendWinService.register();
        FiendLifecycleService.register();
    }

    /**
     * Shop {@code onBuy}: all-or-nothing, server only. Wathe runs it before charging, so returning false means no
     * charge; it returns true only after the moment, crowbar, effects, announcement and replay line are in place.
     * 商店 {@code onBuy}：全有或全无，仅服务端。Wathe 在扣费前调用它，返回 false 即不扣费；只有在时刻、撬棍、效果、
     * 公告与回放记录全部完成后才返回 true。
     */
    public static boolean tryStart(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity fiend)) {
            return false;
        }
        ServerWorld world = fiend.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(world);
        UUID matchId = currentMatchId();
        if (!FiendMomentRules.canStart(FiendParticipation.isDormantFiend(fiend),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE, matchId, moment.isActive())) {
            return false;
        }

        moment.start(fiend.getUuid(), FiendRules.MOMENT_DURATION_TICKS, matchId);
        fiend.getInventory().offerOrDrop(FiendMomentCrowbar.create());
        FiendMomentEffects.grant(fiend);
        announceStart(world);

        NbtCompound data = new NbtCompound();
        data.putUuid(FiendReplayFormatters.FIEND_KEY, fiend.getUuid());
        data.putInt(FiendReplayFormatters.DURATION_KEY, FiendRules.MOMENT_DURATION_TICKS);
        GameRecordManager.recordGlobalEvent(world, FiendMomentRules.START_EVENT_ID, fiend, data);
        return true;
    }

    /**
     * The single end path: clears the synced moment, marks the Fiend spent for the moment's match when the reason says
     * so (Taotie swallow), removes the Speed the moment still owns, takes the marked crowbar back unless
     * the moment was won, and, per the reason, records the replay line and tells every player while the round is
     * ACTIVE. A no-op without a moment.
     * 唯一的结束路径：清除已同步的时刻，按原因（饕餮吞噬）将魔人登记为该时刻所属对局中已耗尽，移除时刻仍拥有的速度效果，
     * 除获胜外收回带标记的撬棍，并按原因记录回放、在对局 ACTIVE 时告知所有玩家。无时刻时为空操作。
     */
    static void end(ServerWorld world, FiendMomentRules.EndReason reason) {
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(world);
        UUID fiendId = moment.fiend();
        if (fiendId == null) {
            return;
        }
        UUID momentMatch = moment.matchId();
        boolean takesCrowbarBack = FiendMomentRules.takesCrowbarBack(reason, moment.isComplete());
        moment.clear();
        if (reason.marksSpent()) {
            moment.markSpent(fiendId, momentMatch);
        }
        ServerPlayerEntity fiend = world.getServer().getPlayerManager().getPlayer(fiendId);
        if (fiend != null) {
            FiendMomentEffects.release(fiend);
            if (takesCrowbarBack) {
                FiendMomentCrowbar.takeBack(fiend);
            }
        } else {
            FiendMomentEffects.forget(fiendId);
        }
        if (reason.recordsReplay()) {
            NbtCompound data = new NbtCompound();
            data.putUuid(FiendReplayFormatters.FIEND_KEY, fiendId);
            data.putString(FiendReplayFormatters.REASON_KEY, FiendReplayFormatters.endReasonValue(reason));
            GameRecordManager.recordGlobalEvent(world, FiendMomentRules.END_EVENT_ID, fiend, data);
        }
        if (reason.announces()
                && GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            announceEnd(world, reason);
        }
    }

    /**
     * Crowbar door pry by the moment Fiend: an exact 5 s cooldown, written after Wathe's (and SparkTraits') own writes.
     * SparkTraits' exact write performs the vanilla set itself, so the vanilla write runs only when it is absent or
     * older (never both). Non-Fiend users and creative players are untouched.
     * 时刻中的魔人用撬棍撬门：写入精确的 5 秒冷却，位于 Wathe（及 SparkTraits）自身写入之后。SparkTraits 的精确写入会
     * 自行完成原版写入，因此仅在其缺失或过旧时回退到原版写入（二者不会同时执行）。非魔人与创造模式玩家不受影响。
     */
    public static void applyCrowbarCooldown(ServerPlayerEntity player, Item crowbar) {
        if (player.isCreative() || !FiendParticipation.isMomentFiend(player)) {
            return;
        }
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, crowbar,
                FiendRules.MOMENT_CROWBAR_COOLDOWN_TICKS)) {
            player.getItemCooldownManager().set(crowbar, FiendRules.MOMENT_CROWBAR_COOLDOWN_TICKS);
        }
    }

    /** Current Wathe match id; {@code null} between rounds. / 当前 Wathe 对局 id；回合之间为 {@code null}。 */
    static @Nullable UUID currentMatchId() {
        return FiendMatch.currentId();
    }

    private static void announceStart(ServerWorld world) {
        Text title = Text.translatable(TITLE_KEY).styled(style -> style.withColor(FiendRules.COLOR).withBold(true));
        Text subtitle = Text.translatable(SUBTITLE_KEY).formatted(Formatting.RED);
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
            player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, START_SOUND_VOLUME,
                    START_SOUND_PITCH);
        }
    }

    private static void announceEnd(ServerWorld world, FiendMomentRules.EndReason reason) {
        Text title = reason == FiendMomentRules.EndReason.DIED
                ? Text.translatable(FELL_KEY).styled(style -> style.withColor(FiendRules.COLOR).withBold(true))
                : Text.translatable(ENDED_KEY).formatted(Formatting.GRAY);
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            // Drop a still-visible start subtitle first. / 先清除可能仍在显示的开场副标题。
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
}
