package dev.caecorthus.sparkwitch.item.defense;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.agmas.noellesroles.ModEffects;
import org.jetbrains.annotations.Nullable;

/**
 * Server effects of the Defense Vial: the drink, the layer a poured vial gives its taker, and their replay records.
 * <p>
 * A layer is one level of NoellesRoles' whiskey shield ({@code ModEffects.WHISKEY_SHIELD}, layers = amplifier + 1).
 * NoellesRoles' {@code KillPlayer.BEFORE} handler spends one layer per blocked non-forced kill (Wathe poison included),
 * and SparkWitch's AXMC pierce and ceremonial-sword protection read the same stack, so nothing else needs wiring. The
 * layer lasts the whole round, the Medium blessing's owner rule: the stack shares one duration, so it is rebuilt with
 * amplifier + 1 and an infinite duration, and layers already there become round-long too. Wathe clears status
 * effects at both round edges.
 * <p>
 * 防御药剂的服务端效果：饮用、倒入的药剂给取用者的护盾，以及对应的回放记录。
 * <p>
 * 一层即 NoellesRoles 威士忌护盾（{@code ModEffects.WHISKEY_SHIELD}，层数 = 等级 + 1）的一级。NoellesRoles 的
 * {@code KillPlayer.BEFORE} 处理器每挡下一次非强制击杀（含 Wathe 毒药）就消耗一层，SparkWitch 的 AXMC 穿盾与仪式剑保护也读取
 * 同一叠护盾，因此无需额外接线。该层持续整局，沿用通灵师加护的所有者规则：整叠护盾共用一个时长，因此以等级 + 1、无限时长
 * 重建，已有的层也随之变为整局有效。Wathe 在回合两端都会清除状态效果。
 */
public final class DefenseVialService {
    private DefenseVialService() {
    }

    /**
     * A living, non-spectator participant of an ACTIVE round. Reads only synced state, so the client never starts a
     * drink the server will refuse (the Hanna handkerchief gate).
     * ACTIVE 回合中存活且非旁观的参与者。只读取已同步的状态，因此客户端不会开始一次服务端会拒绝的饮用（沿用汉娜手帕的门槛）。
     */
    public static boolean inActiveRound(PlayerEntity player) {
        return GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator();
    }

    /** The running Wathe match, or {@code null} between rounds. / 进行中的 Wathe 对局；回合之间为 {@code null}。 */
    public static @Nullable UUID activeMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }

    /** A finished drink: +1 layer, its cue, its replay line. / 喝完一瓶：+1 层、提示与回放记录。 */
    static void drink(ServerPlayerEntity drinker) {
        addRoundLongShieldLayer(drinker);
        drinker.sendMessage(Text.translatable("message.sparkwitch.defense_vial.shield"), true);
        // The drink itself is public (DRINK pose and sound), so its finishing cue is too. / 饮用本身是公开的（饮用姿势与音效），收尾提示音同样公开。
        drinker.getWorld().playSound(null, drinker.getBlockPos(), SoundEvents.ITEM_BOTTLE_FILL,
                SoundCategory.PLAYERS, 0.8F, 1.2F);
        record(drinker, DefenseVialRules.ACTION_DRINK, null);
    }

    /**
     * The layer a poured vial gives the player who took the next serving. Private cue: the tray's vial is a secret.
     * 倒入的药剂给下一个取用者的护盾层。提示只给本人：托盘里的药剂是秘密。
     */
    static void shieldTaker(ServerPlayerEntity taker, UUID pourer, BlockPos platterPos) {
        addRoundLongShieldLayer(taker);
        taker.sendMessage(Text.translatable("message.sparkwitch.defense_vial.tray_shield"), true);
        taker.playSoundToPlayer(SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.8F, 1.2F);
        NbtCompound extra = new NbtCompound();
        extra.putUuid("pourer", pourer);
        GameRecordManager.putBlockPos(extra, "pos", platterPos);
        record(taker, DefenseVialRules.ACTION_TRAY_SHIELD, extra);
    }

    /** A pour into the tray at {@code platterPos}. / 倒入 {@code platterPos} 处托盘的记录。 */
    static void recordPour(ServerPlayerEntity pourer, BlockPos platterPos) {
        NbtCompound extra = new NbtCompound();
        GameRecordManager.putBlockPos(extra, "pos", platterPos);
        record(pourer, DefenseVialRules.ACTION_POUR, extra);
    }

    private static void record(ServerPlayerEntity actor, String action, @Nullable NbtCompound extra) {
        NbtCompound data = extra == null ? new NbtCompound() : extra;
        data.putString("action", action);
        GameRecordManager.recordItemUse(actor, DefenseVialRules.ITEM_ID, null, data);
    }

    private static void addRoundLongShieldLayer(ServerPlayerEntity player) {
        StatusEffectInstance existing = player.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        int amplifier = existing == null ? 0 : existing.getAmplifier() + 1;
        if (existing != null) {
            player.removeStatusEffect(ModEffects.WHISKEY_SHIELD);
        }
        player.addStatusEffect(new StatusEffectInstance(
                ModEffects.WHISKEY_SHIELD, StatusEffectInstance.INFINITE, amplifier, false, false, true));
    }
}
