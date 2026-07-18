package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Owns Wind Spirit movement, blackout vision, shop access, and task income.
 * 统一持有风精灵的移动、熄灯视野、商店与任务收入。
 */
public final class WindSpiritFeatureService {
    public static final Identifier WIND_CHARGE_SHOP_ID = SparkWitch.id("wind_spirit_wind_charge");
    public static final int WIND_CHARGE_PRICE = 50;
    public static final int TASK_REWARD = 50;
    private static final int NIGHT_VISION_REFRESH_TICKS = 40;
    private static final int NIGHT_VISION_REFRESH_THRESHOLD_TICKS = 20;
    private static boolean registered;

    private WindSpiritFeatureService() {
    }

    /**
     * Registers Wind Spirit-owned hooks once; callers must invoke this during common initialization.
     * 仅注册一次风精灵自有事件；调用方必须在通用初始化阶段调用本方法。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        BuildShopEntries.EVENT.register(WindSpiritFeatureService::addWindCharge);
        TaskComplete.EVENT.register((player, taskType) -> rewardTask(player));
        BlackoutEffect.BEFORE.register(WindSpiritFeatureService::beforeBlackoutEffect);
        ServerTickEvents.END_WORLD_TICK.register(WindSpiritFeatureService::tickWorld);
    }

    private static void addWindCharge(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!WindSpiritRules.isWindSpirit(player)
                || context.getEntries().stream().anyMatch(entry ->
                WIND_CHARGE_SHOP_ID.toString().equals(entry.id())
                        || entry.stack().isOf(Items.WIND_CHARGE))) {
            return;
        }
        context.addEntry(new ShopEntry.Builder(
                WIND_CHARGE_SHOP_ID.toString(),
                Items.WIND_CHARGE.getDefaultStack(),
                WIND_CHARGE_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
    }

    private static void rewardTask(ServerPlayerEntity player) {
        if (WindSpiritRules.shouldRewardTask(WindSpiritRules.isActivePromotedWindSpirit(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(TASK_REWARD);
        }
    }

    private static BlackoutEffect.BlackoutResult beforeBlackoutEffect(
            ServerPlayerEntity player,
            int durationTicks
    ) {
        if (!WindSpiritRules.isWindSpirit(player)) {
            return null;
        }
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        applyNightVision(player, durationTicks);
        return BlackoutEffect.BlackoutResult.cancel();
    }

    private static void tickWorld(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning()) {
            return;
        }
        boolean blackoutActive = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !WindSpiritRules.isWindSpirit(player)) {
                continue;
            }
            maintainSpeed(player);
            maintainInfiniteStamina(player);
            if (WindSpiritRules.shouldMaintainBlackoutVision(true, blackoutActive)) {
                maintainNightVision(player);
            }
        }
    }

    private static void maintainSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        int amplifier = current == null ? -1 : current.getAmplifier();
        int remainingTicks = current == null
                ? 0
                : current.isInfinite() ? Integer.MAX_VALUE : current.getDuration();
        if (!WindSpiritRules.shouldRefreshSpeed(amplifier, remainingTicks)) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                StatusEffectInstance.INFINITE,
                WindSpiritRules.SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    private static void maintainInfiniteStamina(ServerPlayerEntity player) {
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        boolean changed = false;
        if (stamina.getMaxSprintTime() != -1) {
            stamina.setMaxSprintTime(-1);
            changed = true;
        }
        if (stamina.isExhausted()) {
            stamina.setExhausted(false);
            changed = true;
        }
        if (changed) {
            stamina.sync();
        }
    }

    private static void maintainNightVision(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current != null
                && (current.isInfinite() || current.getDuration() > NIGHT_VISION_REFRESH_THRESHOLD_TICKS)) {
            return;
        }
        applyNightVision(player, NIGHT_VISION_REFRESH_TICKS);
    }

    private static void applyNightVision(ServerPlayerEntity player, int durationTicks) {
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                Math.max(1, durationTicks),
                0,
                false,
                false,
                true
        ));
    }
}
