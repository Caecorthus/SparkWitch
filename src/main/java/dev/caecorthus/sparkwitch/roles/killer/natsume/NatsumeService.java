package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 夏目安安 runtime: the hypnosis cast, the victim's 12 s state and its per-second drain.
 * 夏目安安运行期：催眠施放、受害者的 12 秒状态与逐秒理智扣除。
 */
public final class NatsumeService {

    /** Hypnosis deadline per victim. 每名受害者的催眠截止 tick。 */
    private static final Map<UUID, Long> HYPNOSIS_UNTIL = new HashMap<>();
    /** When each painist fired last, for the HUD. 每个施术者的冷却截止 tick。 */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    /** Last whole second shown to each victim, so the action bar only updates on change. */
    /** 每个受害者上次显示的整秒数，只在变化时更新动作栏。 */
    private static final Map<UUID, Integer> LAST_SHOWN = new HashMap<>();

    private NatsumeService() {
    }

    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && role != null && NatsumeRules.ROLE_ID.equals(role.identifier())) {
                COOLDOWN_UNTIL.put(serverPlayer.getUuid(),
                        serverPlayer.getServer().getTicks() + (long) NatsumeRules.INITIAL_COOLDOWN_TICKS);
                NatsumeComponent.KEY.get(serverPlayer)
                        .setCooldownSeconds(NatsumeRules.INITIAL_COOLDOWN_TICKS / 20);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(NatsumeService::tick);
        // The killer shop also goes through BuildShopEntries.
        // 杀手商店同样走 BuildShopEntries，因此护身符可以在这里上架。
        dev.doctor4t.wathe.api.event.BuildShopEntries.EVENT.register((player, context) -> {
            if (!isNatsume(player)) {
                return;
            }
            // Poison drops to 25; the 疯魔 / grenade / scorpion / firecracker / note / crowbar rows are removed.
            // 毒药降到 25；疯魔、手雷、蝎子、鞭炮、便签、撬棍整行移除。
            List<dev.doctor4t.wathe.util.ShopEntry> entries = context.getEntries();
            for (int i = entries.size() - 1; i >= 0; i--) {
                var entry = entries.get(i);
                String id = entry.id() == null ? "" : entry.id().toLowerCase(java.util.Locale.ROOT);
                if (isBlockedShopRow(id)) {
                    entries.remove(i);
                    continue;
                }
                if (id.contains("poison") && entry.price() != 25) {
                    entries.set(i, new dev.doctor4t.wathe.util.ShopEntry.Builder(
                            entry.id(), entry.displayStack(), 25, entry.type())
                            .cooldown(0)
                            .build());
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            HYPNOSIS_UNTIL.clear();
            COOLDOWN_UNTIL.clear();
            LAST_SHOWN.clear();
        });
    }

    public static boolean isNatsume(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && NatsumeRules.ROLE_ID.equals(role.identifier());
    }

    /** Victims whose items are locked out (civilians only). 被封锁道具的受害者（只有平民）。 */
    private static final java.util.Set<UUID> BLOCKED_ITEMS = new java.util.HashSet<>();

    private static boolean blocksItems(ServerPlayerEntity player) {
        return isHypnotised(player) && BLOCKED_ITEMS.contains(player.getUuid());
    }

    /** Police, killers and desperadoes. 警职、杀手与亡命徒。 */
    public static boolean isHostileToHypnosis(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null) {
            return false;
        }
        String path = role.identifier().getPath();
        // 亡命徒 is wathe:loose_end. 亡命徒的身份 id 是 wathe:loose_end。
        if (path.contains("loose_end") || path.contains("desperado") || path.contains("outlaw")) {
            return true;
        }
        return !role.isInnocent();
    }

    /** 诺亚 is immune to hypnosis (owner 2026-11-03). 诺亚免疫催眠（所有者 2026-11-03）。 */
    /** Rows 夏目安安 must never see in her shop. 夏目安安商店里不允许出现的条目。 */
    private static boolean isBlockedShopRow(String id) {
        return id.contains("psycho")
                || id.contains("grenade")
                || id.contains("scorpion")
                || id.contains("firecracker")
                || id.contains("crowbar")
                || id.equals("note");
    }

    public static boolean isNoa(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules.ROLE_ID
                .equals(role.identifier());
    }

    public static boolean isHypnotised(ServerPlayerEntity player) {
        Long until = HYPNOSIS_UNTIL.get(player.getUuid());
        return until != null && player.getServer().getTicks() < until;
    }

    /** Sends the target list (herself included) to the client so the menu can open. 把目标列表（含自己）发给客户端打开菜单。 */
    public static void requestMenu(ServerPlayerEntity caster) {
        if (!isNatsume(caster) || !GameFunctions.isPlayerPlayingAndAlive(caster)) {
            return;
        }
        long now = caster.getServer().getTicks();
        Long until = COOLDOWN_UNTIL.get(caster.getUuid());
        if (until != null && now < until) {
            // Cooldown is shown by the HUD line above the action bar; do not duplicate it here.
            // 冷却由动作栏上方的 HUD 行显示，这里不再重复发一条。
            return;
        }
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        ids.add(caster.getUuid());
        names.add(caster.getGameProfile().getName());
        if (caster.getWorld() instanceof ServerWorld world) {
            for (ServerPlayerEntity candidate : world.getPlayers()) {
                if (candidate == caster || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
                    continue;
                }
                ids.add(candidate.getUuid());
                names.add(candidate.getGameProfile().getName());
            }
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(caster,
                new dev.caecorthus.sparkwitch.net.OpenNatsumeMenuS2CPacket(ids, names));
    }

    /** Casts hypnosis on the chosen target. 对选定目标施放催眠。 */
    public static void cast(ServerPlayerEntity caster, UUID targetId) {
        if (!isNatsume(caster) || !GameFunctions.isPlayerPlayingAndAlive(caster)) {
            return;
        }
        long now = caster.getServer().getTicks();
        Long until = COOLDOWN_UNTIL.get(caster.getUuid());
        if (until != null && now < until) {
            return;
        }
        ServerPlayerEntity target = caster.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null) {
            return;
        }
        if (caster.squaredDistanceTo(target) > NatsumeRules.RANGE * NatsumeRules.RANGE) {
            caster.sendMessage(Text.translatable("message.sparkwitch.natsume.too_far",
                    target.getGameProfile().getName()).formatted(Formatting.RED), true);
            return;
        }
        // Line of sight: the ray must reach the target without hitting a wall. 视线判定：射线必须打到目标而不是墙。
        if (!caster.canSee(target)) {
            caster.sendMessage(Text.translatable("message.sparkwitch.natsume.no_line_of_sight")
                    .formatted(Formatting.RED), true);
            return;
        }
        // Police / killers / desperadoes get a shorter window and keep their items.
        // 警职、杀手、亡命徒：时效更短，且不会被封道具。
        boolean hostile = isHostileToHypnosis(target);
        int ticks = hostile ? NatsumeRules.HYPNOSIS_TICKS_HOSTILE : NatsumeRules.HYPNOSIS_TICKS;
        COOLDOWN_UNTIL.put(caster.getUuid(), now + (long) NatsumeRules.COOLDOWN_TICKS);
        // Owner 2026-11-03: 诺亚 is immune — the cast still fires and still costs the cooldown, but no effect lands.
        // 所有者 2026-11-03：诺亚免疫——技能照常触发、照常消耗冷却，但不产生任何效果。
        if (isNoa(target)) {
            target.getWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 1.0F, 0.7F);
            // 诺亚 only learns that she is immune; the shared "X resisted Y's hypnosis" line is replay-only.
            // 诺亚本人只收到「你免疫了催眠效果」；「X 免疫了 Y 的催眠效果」那句只出现在回放里。
            target.sendMessage(Text.translatable("message.sparkwitch.natsume.immune_self")
                    .formatted(Formatting.AQUA), true);
            caster.sendMessage(Text.translatable("message.sparkwitch.natsume.immune_caster",
                    target.getGameProfile().getName()).formatted(Formatting.GRAY), true);
            NbtCompound immuneReplay = new NbtCompound();
            immuneReplay.putString("action", "immune");
            immuneReplay.putUuid("target", target.getUuid());
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    caster, NatsumeRules.ROLE_ID, target, immuneReplay);
            return;
        }
        HYPNOSIS_UNTIL.put(target.getUuid(), now + (long) ticks);
        BLOCKED_ITEMS.add(target.getUuid());
        if (hostile) {
            BLOCKED_ITEMS.remove(target.getUuid());
        }
        LAST_SHOWN.remove(target.getUuid());
        NatsumeComponent.KEY.get(target).setHypnosisSeconds(ticks / 20);

        target.getWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.0F, 0.7F);
        caster.sendMessage(Text.translatable("message.sparkwitch.natsume.cast",
                target.getGameProfile().getName()).formatted(Formatting.DARK_PURPLE), true);
        target.sendMessage(Text.translatable("message.sparkwitch.natsume.hypnotised")
                .formatted(Formatting.DARK_PURPLE), true);

        NbtCompound replay = new NbtCompound();
        replay.putString("action", "hypnosis");
        replay.putUuid("target", target.getUuid());
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                caster, NatsumeRules.ROLE_ID, target, replay);
    }

    /** Nearest player within the caster's look direction; falls back to the closest player in range. */
    /** 视线方向上最近的玩家；取不到时退化为范围内最近的玩家。 */
    private static ServerPlayerEntity pickTarget(ServerPlayerEntity caster) {
        Vec3d look = caster.getRotationVector();
        ServerPlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        if (!(caster.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == caster || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
                continue;
            }
            if (caster.squaredDistanceTo(candidate) > NatsumeRules.RANGE * NatsumeRules.RANGE) {
                continue;
            }
            Vec3d to = candidate.getPos().subtract(caster.getPos()).normalize();
            double dot = look.dotProduct(to);
            if (dot < 0.6D) {
                continue;
            }
            double score = caster.squaredDistanceTo(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                refreshCooldown(server, player, now);
                tickHypnosis(server, player, now);
            }
        }
    }

    private static void refreshCooldown(MinecraftServer server, ServerPlayerEntity player, long now) {
        if (!isNatsume(player)) {
            return;
        }
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        int seconds = 0;
        if (until != null) {
            long left = until - now;
            seconds = left > 0 ? (int) Math.ceil(left / 20.0) : 0;
            if (left <= 0) {
                COOLDOWN_UNTIL.remove(player.getUuid());
            }
        }
        NatsumeComponent.KEY.get(player).setCooldownSeconds(seconds);
    }

    private static void tickHypnosis(MinecraftServer server, ServerPlayerEntity player, long now) {
        Long until = HYPNOSIS_UNTIL.get(player.getUuid());
        if (until == null) {
            NatsumeComponent.KEY.get(player).setHypnosisSeconds(0);
            return;
        }
        long left = until - now;
        if (left <= 0) {
            HYPNOSIS_UNTIL.remove(player.getUuid());
            BLOCKED_ITEMS.remove(player.getUuid());
            LAST_SHOWN.remove(player.getUuid());
            NatsumeComponent.KEY.get(player).setHypnosisSeconds(0);
            player.sendMessage(Text.translatable("message.sparkwitch.natsume.ended")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        int seconds = (int) Math.ceil(left / 20.0);
        NatsumeComponent.KEY.get(player).setHypnosisSeconds(seconds);
        // Blindness is refreshed while the hypnosis lasts. 催眠期间持续刷新失明。
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));
        // A shriek keeps the victim company for the whole 12 s. 尖啸体在 12 秒里持续作响。
        if (left % 40L == 0L) {
            player.getWorld().playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 0.7F, 1.0F);
        }
        if (left % 20L == 0L) {
            // 4% of the bar per second. 每秒 4% 理智。
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
            mood.setMood(Math.max(-1.0F, mood.getMood() - NatsumeRules.SANITY_PER_SECOND));
            Integer shown = LAST_SHOWN.get(player.getUuid());
            if (shown == null || shown != seconds) {
                LAST_SHOWN.put(player.getUuid(), seconds);
                player.sendMessage(Text.translatable("message.sparkwitch.natsume.remaining", seconds)
                        .formatted(Formatting.DARK_PURPLE), true);
            }
        }
    }
}
