package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Harmless TR burning: visible fire with every fire-damage event vetoed while the owned burn lasts, so no hurt
 * flash, sound, or damage hook fires.
 * Wathe already cancels the health loss of a playing player, but {@code damage()} would still flash, play the hurt
 * sound, and reach damage hooks every 20 ticks; the Fabric {@code ALLOW_DAMAGE} veto stops all of that. The veto
 * covers only the owned window: a longer burn from another source keeps its fire ticks, and its fire damage is
 * vetoed until the window ends and allowed afterwards. The window ledger is server-only and never saved.
 * 无伤的 TR 燃烧：显示火焰，并在本效果持续期间否决所有火焰伤害事件，因此不会出现受伤闪红、受伤音效或伤害钩子。
 * Wathe 已取消对局中玩家的扣血，但 {@code damage()} 每 20 刻仍会闪红、播放受伤音效并触达伤害钩子；Fabric 的
 * {@code ALLOW_DAMAGE} 否决会全部拦下。否决只覆盖自有窗口：其他来源更长的燃烧保留其着火时长，其火焰伤害在窗口内被否决、
 * 窗口结束后照常放行。窗口登记表仅存在于服务端，从不存盘。
 */
public final class PotionShellBurn {
    private static final PotionShellBurnWindow WINDOWS = new PotionShellBurnWindow();
    private static boolean registered;

    private PotionShellBurn() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PotionShellBurn::allowDamage);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!WINDOWS.isEmpty()) {
                WINDOWS.expire(server.getTicks());
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> release(victim));
        ResetPlayer.EVENT.register(PotionShellBurn::release);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.player));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> releaseAll(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WINDOWS.clear());
    }

    /**
     * Server only: sets visible fire for at least {@code ticks} (a longer existing burn is kept) and opens or extends
     * the owned veto window.
     * 仅服务端：设置至少 {@code ticks} 的可见火焰（保留更长的已有燃烧），并开启或延长自有否决窗口。
     */
    public static void ignite(ServerPlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        player.setFireTicks(PotionShellBurnWindow.fireTicksAfterIgnite(player.getFireTicks(), ticks));
        WINDOWS.open(player.getUuid(), now(player), ticks);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player) || WINDOWS.isEmpty()) {
            return true;
        }
        return !PotionShellBurnWindow.vetoes(isFireDamage(source), WINDOWS.isActive(player.getUuid(), now(player)));
    }

    private static boolean isFireDamage(DamageSource source) {
        return source.isIn(DamageTypeTags.IS_FIRE)
                || source.isOf(DamageTypes.ON_FIRE)
                || source.isOf(DamageTypes.IN_FIRE);
    }

    /** Death, reset, disconnect: forget the window and put out a burn we own. / 死亡、重置、断线：遗忘窗口并熄灭自有燃烧。 */
    private static void release(ServerPlayerEntity player) {
        if (player != null && WINDOWS.close(player.getUuid())) {
            player.extinguish();
        }
    }

    private static void releaseAll(World world) {
        MinecraftServer server = world.getServer();
        for (UUID owner : WINDOWS.drain()) {
            ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.extinguish();
            }
        }
    }

    private static long now(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        return server == null ? 0L : server.getTicks();
    }
}
