package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 橘雪莉's own 巨力魔法 — the same window the Apprentice gets, but only one side may hold it in a round.
 * <p>Owner spec 2026-11-02: "与携带巨力魔法的预备魔女冲突，两者二选一在场". Enforced at cast time: if a living
 * Apprentice currently owns Mighty Force, the detective's window is refused (and vice versa by symmetry — the
 * Apprentice's own ability already requires her window, which this never opens).
 * 橘雪莉自己的巨力魔法——与预备魔女同一个窗口，但一局里只能有一方持有。
 * <p>所有者规格 2026-11-02：“与携带巨力魔法的预备魔女冲突，两者二选一在场”。在施放时判定：
 * 若存活的预备魔女当前持有巨力魔法，则拒绝侦探开启窗口。
 */
public final class SherryMightyForce {

    /** The skill entry the inventory panel lists. 背包面板里列出的技能条目。 */
    public static final net.minecraft.util.Identifier SKILL_ID =
            net.minecraft.util.Identifier.of("sparkwitch", "sherry_mighty_force");

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();

    private SherryMightyForce() {
    }

    /** Round-start cooldown for Mighty Force. 巨力的回合初始冷却。 */
    public static void applyInitialCooldown(ServerPlayerEntity player) {
        long until = player.getServer().getTicks() + (long) SherryRules.INITIAL_COOLDOWN_TICKS;
        COOLDOWN_UNTIL.put(player.getUuid(), until);
        SherryComponent.KEY.get(player).setMightyState(0, SherryRules.INITIAL_COOLDOWN_TICKS / 20);
    }

    public static void use(ServerPlayerEntity player) {
        if (!SherryService.isSherry(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        long now = player.getServer().getTicks();
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.mighty.cooldown",
                    (until - now) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        if (apprenticeHoldsMightyForce(world, game)) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.mighty.taken")
                    .formatted(Formatting.RED), true);
            return;
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.spendMana(MightyForceAbility.MANA_COST)) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.mighty.no_mana",
                    MightyForceAbility.MANA_COST).formatted(Formatting.RED), true);
            return;
        }
        component.beginMightyForceWindow(MightyForceAbility.WINDOW_TICKS);
        COOLDOWN_UNTIL.put(player.getUuid(), now + (long) MightyForceAbility.COOLDOWN_TICKS);
        ACTIVE_UNTIL.put(player.getUuid(), now + MightyForceAbility.WINDOW_TICKS);
        SherryComponent.KEY.get(player).setMightyState(
                MightyForceAbility.WINDOW_TICKS / 20, MightyForceAbility.COOLDOWN_TICKS / 20);
        player.sendMessage(Text.translatable("message.sparkwitch.sherry.mighty.activated")
                .formatted(Formatting.AQUA), true);
    }

    private static boolean apprenticeHoldsMightyForce(ServerWorld world, GameWorldComponent game) {
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.apprenticeWitch())) {
            ServerPlayerEntity apprentice = world.getServer().getPlayerManager().getPlayer(uuid);
            if (apprentice != null && GameFunctions.isPlayerPlayingAndAlive(apprentice)
                    && MightyForceAbility.ID.equals(WitchPlayerComponent.KEY.get(apprentice).getActiveSkillId())) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        COOLDOWN_UNTIL.clear();
        ACTIVE_UNTIL.clear();
    }

    /** Mirrors window and cooldown seconds to the HUD once per changed second. 每秒把窗口与冷却剩余推给 HUD。 */
    public static void tickHud(ServerPlayerEntity player) {
        long now = player.getServer().getTicks();
        int active = 0;
        Long activeUntil = ACTIVE_UNTIL.get(player.getUuid());
        if (activeUntil != null) {
            long left = activeUntil - now;
            if (left > 0) {
                active = (int) Math.ceil(left / 20.0);
            } else {
                ACTIVE_UNTIL.remove(player.getUuid());
            }
        }
        int cooldown = 0;
        Long cooldownUntil = COOLDOWN_UNTIL.get(player.getUuid());
        if (cooldownUntil != null) {
            long left = cooldownUntil - now;
            if (left > 0) {
                cooldown = (int) Math.ceil(left / 20.0);
            } else {
                COOLDOWN_UNTIL.remove(player.getUuid());
            }
        }
        SherryComponent.KEY.get(player).setMightyState(active, cooldown);
    }
}
