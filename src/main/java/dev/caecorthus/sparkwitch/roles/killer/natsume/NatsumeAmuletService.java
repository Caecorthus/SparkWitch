package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 诺亚的蝴蝶护身符: a lethal hit is absorbed, the player lingers three seconds as a ghost, then stands back up
 * exactly where they died — the same route 薛定谔之猫 uses, because Wathe's melee/gun kills pass {@code force = true}
 * and skip {@code KillPlayer.BEFORE}'s cancel entirely.
 * <p>诺亚的蝴蝶护身符：致命一击被吃掉，玩家以幽魂形态滞留三秒，然后在倒下的位置站起来。
 * 走的正是薛定谔之猫那条路——因为 Wathe 的近战/枪击都会传 {@code force = true}，
 * 完全绕过 {@code KillPlayer.BEFORE} 的 cancel，只看 BEFORE 的话护身符永远不会生效。
 */
public final class NatsumeAmuletService {

    private static final int HOLD_TICKS = 3 * 20;
    /** Players whose next death is covered by an amulet. 下一次死亡由护身符处理的玩家。 */
    private static final Set<UUID> ARMED = new HashSet<>();
    /** Pending revives: where to stand up and when. 待复活的记录：复活位置与时间。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(ServerWorld world, Vec3d pos, long dueTick) {
    }

    private NatsumeAmuletService() {
    }

    public static void register() {
        KillPlayer.BEFORE.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayerEntity player)) {
                return null;
            }
            if (ARMED.contains(player.getUuid()) || !consume(player)) {
                return null;
            }
            ARMED.add(player.getUuid());
            // Same ghost window as 薛定谔之猫: force=true ignores the cancel, so the body must already be
            // untouchable while the death is being written. 与薛定谔之猫相同的幽魂窗口：
            // force=true 会无视 cancel，所以要在死亡写入期间就让身体不可被攻击。
            player.setInvisible(true);
            player.setInvulnerable(true);
            player.getWorld().playSound(null, player.getBlockPos(),
                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.sendMessage(Text.translatable("message.sparkwitch.natsume.amulet_triggered")
                    .formatted(Formatting.LIGHT_PURPLE), true);
            return KillPlayer.KillResult.cancel();
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayerEntity player)) {
                return;
            }
            boolean armed = ARMED.remove(player.getUuid());
            // Fallback: if BEFORE never armed us (e.g. a kill path that skips it), consume right here.
            // 兜底：若 BEFORE 没来得及登记（某些击杀路径），就在这里直接消耗。
            if (!armed && !consume(player)) {
                return;
            }
            if (!(player.getWorld() instanceof ServerWorld world)) {
                return;
            }
            // The hit went through despite the cancel: hold the body and revive it three seconds later in place.
            // 这一击绕过了 cancel：留下身体，三秒后原地复活。
            PENDING.put(player.getUuid(),
                    new Pending(world, player.getPos(), world.getServer().getTicks() + HOLD_TICKS));
        });
        ServerTickEvents.END_SERVER_TICK.register(NatsumeAmuletService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTicks();
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().dueTick() > now) {
                continue;
            }
            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            revive(player, entry.getValue());
        }
    }

    private static void revive(ServerPlayerEntity player, Pending pending) {
        try {
            GameWorldComponent game = GameWorldComponent.KEY.get(pending.world());
            if (game instanceof dev.caecorthus.sparkwitch.mixin.cat.GameWorldComponentDeadAccessor dead) {
                dead.sparkwitch$getDeadPlayers().remove(player.getUuid());
            }
            game.sync();
            // A dead player is parked as a spectator; without these the body never comes back.
            // 玩家死后会停在旁观状态；少了这两步身体不会回来。
            player.setCameraEntity(player);
            player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            pending.world().getServer().execute(() -> {
                if (player.isAlive() && player.interactionManager.getGameMode()
                        == net.minecraft.world.GameMode.SPECTATOR) {
                    player.setCameraEntity(player);
                    player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                }
            });
            player.setHealth(player.getMaxHealth());
            player.setInvulnerable(false);
            player.setInvisible(false);
            player.teleport(pending.world(), pending.pos().x, pending.pos().y, pending.pos().z,
                    player.getYaw(), player.getPitch());
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
            player.fallDistance = 0.0F;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY,
                    HOLD_TICKS, 0, false, false, false));
            player.sendMessage(Text.translatable("message.sparkwitch.natsume.amulet_saved",
                    player.getGameProfile().getName()).formatted(Formatting.LIGHT_PURPLE), true);
        } catch (Throwable failure) {
            dev.caecorthus.sparkwitch.SparkWitch.LOGGER.error(
                    "[natsume] amulet revive failed for {}: {}",
                    player.getGameProfile().getName(), failure.toString(), failure);
        }
    }

    private static boolean consume(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(dev.caecorthus.sparkwitch.SparkWitchItems.natsumeAmulet())) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }
}
