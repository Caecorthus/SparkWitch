package dev.caecorthus.sparkwitch.block.service;

import dev.caecorthus.sparkwitch.block.SparkBedBlock;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Pays out the strange bed's bill. Wathe fulfils its sleep task by counting ticks while {@code isSleeping()}, then fires
 * {@link TaskComplete}; at that moment the player still records which bed they are lying in, so the block under
 * {@code getSleepingPosition()} is exactly the bed that has to be blamed.
 * <p>
 * 结算奇怪床的账单。Wathe 的睡觉任务靠在 {@code isSleeping()} 期间累减 tick 完成，随后触发 {@link TaskComplete}；
 * 那一刻玩家仍然记录着自己躺在哪张床上，因此 {@code getSleepingPosition()} 指向的方块正是要算账的那张床。
 */
public final class SparkBedService {
    /** Debuff length the strange bed hands out, in ticks: 5 s. / 奇怪床给出的负面效果时长，tick：5 秒。 */
    public static final int STRANGE_DEBUFF_TICKS = 5 * 20;

    private SparkBedService() {
    }

    public static void register() {
        TaskComplete.EVENT.register(SparkBedService::onTaskComplete);
    }

    private static void onTaskComplete(net.minecraft.entity.player.PlayerEntity player, PlayerMoodComponent.Task task) {
        if (task != PlayerMoodComponent.Task.SLEEP || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        BlockPos sleepingPos = serverPlayer.getSleepingPosition().orElse(null);
        if (sleepingPos == null) {
            return;
        }
        if (!(serverPlayer.getWorld().getBlockState(sleepingPos).getBlock() instanceof SparkBedBlock bed)) {
            return;
        }
        if (bed.variant() != SparkBedBlock.Variant.GREEN_STRANGE) {
            return;
        }
        serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STRANGE_DEBUFF_TICKS, 0));
        serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, STRANGE_DEBUFF_TICKS, 0));
    }
}
