package dev.caecorthus.sparkwitch.item.defense;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.agmas.noellesroles.ModEffects;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shield layers plus the drinks-tray bookkeeping.
 * <p>
 * Layers reuse NoellesRoles' whiskey shield effect, the same one the Iron Man buff and the whiskey stack ride on, so
 * every existing "shield layer" consumer (Wathe's damage handling, the AXMC pierce loop) already understands it. Layer
 * count is the effect amplifier + 1, which is why applying a layer means "current amplitude + 1".
 * <p>
 * 护盾层，外加饮料托盘的记账。
 * <p>
 * 层数复用 NoellesRoles 的威士忌护盾效果——铁人增益与威士忌叠层用的就是它——因此所有既有的「护盾层」消费方
 * （Wathe 的伤害处理、AXMC 穿盾循环）天然认得。层数等于效果等级 + 1，所以「加一层」就是「当前等级 + 1」。
 */
public final class DefenseVialService {
    /** Tray position -> the world time the vial stays active there. / 托盘坐标 -> 该处药剂保持有效的世界时间。 */
    private static final Map<BlockPos, Long> ARMED_TRAYS = new ConcurrentHashMap<>();
    /** How long a poured vial keeps working, in ticks: 10 minutes is longer than any round. / 倒进去的药剂有效期，tick：10 分钟，长于任何一局。 */
    private static final long TRAY_LIFETIME_TICKS = 10L * 60L * 20L;

    private DefenseVialService() {
    }

    /**
     * Adds exactly one layer on top of whatever the player already carries.
     * 在玩家已有的层数之上，正好加一层。
     */
    public static void applyShield(PlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        int nextAmplifier = current == null ? 0 : current.getAmplifier() + 1;
        player.addStatusEffect(new StatusEffectInstance(
                ModEffects.WHISKEY_SHIELD,
                StatusEffectInstance.INFINITE,
                nextAmplifier,
                false,
                false,
                true
        ));
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.translatable("message.sparkwitch.defense_vial.shield"), true);
            serverPlayer.getWorld().playSound(
                    null,
                    serverPlayer.getBlockPos(),
                    SoundEvents.ITEM_BOTTLE_FILL,
                    SoundCategory.PLAYERS,
                    0.8F,
                    1.2F
            );
        }
    }

    /** Remembers that this tray now carries a vial. / 记下这个托盘现在装有药剂。 */
    public static void armTray(BlockPos pos, long now) {
        ARMED_TRAYS.put(pos.toImmutable(), now + TRAY_LIFETIME_TICKS);
    }

    /** True while the tray still carries an unexpired vial. / 托盘上的药剂尚未过期时为真。 */
    public static boolean isArmed(BlockPos pos, long now) {
        Long until = ARMED_TRAYS.get(pos);
        if (until == null) {
            return false;
        }
        if (now > until) {
            ARMED_TRAYS.remove(pos);
            return false;
        }
        return true;
    }

    /** Called after someone actually took a drink or a bite from that tray. / 有人真的从那个托盘吃喝之后调用。 */
    public static void onTrayConsumed(ServerPlayerEntity player, BlockPos pos) {
        long now = player.getWorld().getTime();
        if (!isArmed(pos, now)) {
            return;
        }
        applyShield(player);
    }

    public static void clear() {
        ARMED_TRAYS.clear();
    }
}
