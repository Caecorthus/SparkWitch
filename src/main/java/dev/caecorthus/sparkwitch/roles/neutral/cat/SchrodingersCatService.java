package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Server side of 薛定谔之猫.
 * <p>Every kill except "fell out of the train" is cancelled and the cat becomes the killer's faction: a killer of the
 * witch family grants 魔化使, the household family grants 内应, an ordinary killer grants the plain killer role and a
 * civilian grants a police role. A neutral killer — or no killer at all — simply kills the cat.
 * 薛定谔之猫的服务端：除“推出列车外”以外的致命伤全部不生效，猫转变为击杀者的阵营——魔女系给魔化使、豪族系给内应、
 * 普通杀手给杀手、乘客给警职。击杀者为中立、或找不到击杀者时，猫正常死亡。
 */
public final class SchrodingersCatService {

    /** Witch-family ids: the kill turns the cat into 魔化使. 魔女系：击杀后猫变为魔化使。 */
    /**
     * Identity check, not faction check: only these five witch identities turn the cat into a Bewitched.
     * Curser and Bewitched itself are deliberately excluded (owner spec), and murderous_witch is a separate faction.
     * 按身份而非阵营判定：只有这五个魔女身份会把猫变成魔化使。
     * 诅咒者与魔化使本身被排除（所有者要求），杀意魔女属于另一个阵营。
     */
    private static final Set<Identifier> WITCH_ROLES = Set.of(
            Identifier.of("sparkwitch", "grand_witch"),      // 大魔女
            Identifier.of("sparkwitch", "abyss_listener"),   // 聆渊者
            Identifier.of("sparkwitch", "accomplice"),       // 共犯
            Identifier.of("sparkwitch", "potion_gunner"),    // 药炮手
            Identifier.of("sparkwitch", "riftwalker")        // 隙行者
    );

    /** Household-family ids: the kill turns the cat into 内应. 豪族系：击杀后猫变为内应。 */
    private static final Set<Identifier> HOUSEHOLD_ROLES = Set.of(
            Identifier.of("sparkwitch", "insider")
    );

    /** Players that were assigned the cat this round. Kept because {@code getRole} may not answer after a kill. */
    /** 本局被分配为猫的玩家。保留这份记录，因为被击杀后 getRole 可能查不到。 */
    private static final java.util.Set<java.util.UUID> CATS = new java.util.HashSet<>();
    /** Cats that already used their one revival. 已经用掉唯一一次复活的猫。 */
    private static final java.util.Set<java.util.UUID> TRANSFORMED = new java.util.HashSet<>();
    /** Black cop (neutral, NoellesRoles) — pairs with Insider to form the 嘉豪 faction. */
    /** 黑警（中立，诺艾尔模组）——与内应共同构成嘉豪阵营。 */
    private static final Identifier CORRUPT_COP_ID = Identifier.of("noellesroles", "corrupt_cop");

    /** Insider itself (neutral, SparkWitch). 内应本体（中立，SparkWitch）。 */
    private static final Identifier INSIDER_ID = Identifier.of("sparkwitch", "insider");

    /**
     * Death causes that leave the cat dead instead of transforming it: thrown off the train, falling off the train,
     * and the voodoo curse. Every other lethal blow transforms it.
     * 不触发转化的死法：被击飞下列车、坠落列车、巫毒诅咒。其余任何致命伤都会触发转化。
     */
    private static final Set<Identifier> NO_TRANSFORM_REASONS = Set.of(
            // Both "shoved off" and "fell off" report this one reason.
            // 被推下车与自然掉落都上报这一个死因。
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            // Voodoo kills through NoellesRoles' own reason, verified at VoodooPlayerComponent:77.
            // 巫毒走诺艾尔模组自己的死因，已在 VoodooPlayerComponent:77 核实。
            Identifier.of("noellesroles", "voodoo")
    );

    /** Three seconds at 20 TPS. 三秒（20TPS）。 */
    private static final int HOLD_TICKS = 60;
    /** Cats waiting out their spectre window. 正在等待幽魂阶段结束的猫。 */
    private static final java.util.Map<java.util.UUID, Pending> PENDING = new java.util.HashMap<>();

    private record Pending(ServerWorld world, Role next, long dueTick) {
    }
    /** Where each cat stood when the round started: a known-safe spot to revive into. */
    /** 每只猫开局时所在的位置：一个已知安全的复活点。 */
    private static final java.util.Map<java.util.UUID, net.minecraft.util.math.Vec3d> SAFE_SPOTS =
            new java.util.HashMap<>();

    private SchrodingersCatService() {
    }

    public static void register() {
        // Record who is the cat at assignment time, and clear the roster when a new round starts.
        // 分配时记录谁是猫；新一局开始时清空名单。
        dev.doctor4t.wathe.api.event.RoleAssigned.EVENT.register((player, role) -> {
            if (role != null && SchrodingersCatRules.ROLE_ID.equals(role.identifier())
                    && player instanceof ServerPlayerEntity serverPlayer) {
                CATS.add(serverPlayer.getUuid());
                SAFE_SPOTS.put(serverPlayer.getUuid(), serverPlayer.getPos());
            }
        });

        // 25 coins per finished task. 每完成一个任务获得 25 金币。
        TaskComplete.EVENT.register((player, taskType) -> {
            if (isCat(player)) {
                PlayerShopComponent.KEY.get(player).addToBalance(SchrodingersCatRules.TASK_MONEY_REWARD);
            }
        });

        // Wathe's melee/gun kills pass force=true, which bypasses KillPlayer.BEFORE's cancel result entirely
        // (see GameFunctions.killPlayer: `if (!force && beforeResult.cancelled()) return;`). So the cat cannot
        // block the death up front; it takes the same route Last Stand uses - let the death happen, then undo it
        // immediately and swap the identity.
        // Wathe 的近战/枪击传入 force=true，会完全绕过 KillPlayer.BEFORE 的 cancel
        // （见 GameFunctions.killPlayer：`if (!force && beforeResult.cancelled()) return;`）。
        // 因此猫无法在事前拦下死亡，只能沿用背水一战的路子：让死亡发生，随即撤销并替换身份。
        // BEFORE, not AFTER: by the time AFTER runs Wathe has already written the death, so a delayed revive three
        // seconds later can no longer bring the player back (it left them dead until an admin switched game mode).
        // BEFORE 而非 AFTER：AFTER 执行时 Wathe 已经把死亡写进状态，三秒后再复活根本救不回来
        // （之前表现为玩家一直躺着，直到管理员手动切创造模式）。
        KillPlayer.BEFORE.register((victim, killer, deathReason) -> {
            if (!isCat(victim)) {
                return null;
            }
            // The cat transforms exactly once per round. 猫每局只转化一次。
            if (TRANSFORMED.contains(victim.getUuid())) {
                return null;
            }
            if (NO_TRANSFORM_REASONS.contains(deathReason)) {
                return null;
            }
            if (killer == null || !(victim.getWorld() instanceof ServerWorld world)) {
                return null;
            }
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            Role killerRole = game.getRole(killer);
            if (killerRole == null) {
                // getRole can answer null during death bookkeeping; the UUID-keyed map still has the answer.
                // getRole 在死亡收尾期间可能返回 null；按 UUID 索引的角色表仍有记录。
                killerRole = game.getRoles().get(killer.getUuid());
            }
            if (killerRole == null) {
                dev.caecorthus.sparkwitch.SparkWitch.LOGGER.warn(
                        "[cat] killer role unresolved for {} - cat dies without transforming",
                        killer.getGameProfile().getName());
                return null;
            }
            Role next = transformTarget(killerRole, game, world);
            if (next == null) {
                return null;
            }
            TRANSFORMED.add(victim.getUuid());
            // Owner spec: three seconds as a spectre (invisible, cannot be hit) before the totem fires and the
            // identity changes. The death must be cancelled for that window, otherwise there is nobody left to revive.
            // 所有者要求：先以幽魂形态滞留三秒（不可见、无法被攻击），之后播放图腾音效并转变身份。
            // 这三秒必须取消死亡，否则三秒后已经没有可复活的玩家了。
            victim.setInvisible(true);
            victim.setInvulnerable(true);
            PENDING.put(victim.getUuid(), new Pending(world, next, world.getServer().getTicks() + HOLD_TICKS));
            return KillPlayer.KillResult.cancel();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) {
                return;
            }
            long now = server.getTicks();
            java.util.Iterator<java.util.Map.Entry<java.util.UUID, Pending>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<java.util.UUID, Pending> entry = it.next();
                if (entry.getValue().dueTick() > now) {
                    continue;
                }
                it.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                ServerWorld world = entry.getValue().world();
                if (player == null || world == null) {
                    continue;
                }
                GameWorldComponent game = GameWorldComponent.KEY.get(world);
                leaveSpectre(player);
                reviveAndTransform(player, world, game, entry.getValue().next());
                announceTransition(player, world, game, entry.getValue().next());
            }
        });
    }

    /** Which identity the cat becomes for a given killer, or null when the cat should just die. 猫应变成什么身份；返回 null 表示正常死亡。 */
    private static @Nullable Role transformTarget(Role killerRole, GameWorldComponent game, ServerWorld world) {
        Identifier killerId = killerRole.identifier();

        // 1) Black cop and Insider form the 嘉豪 faction together, so either one turns the cat into an Insider.
        // 1) 黑警与内应共同构成嘉豪阵营，任一方击杀都让猫变成内应。
        if (CORRUPT_COP_ID.equals(killerId) || INSIDER_ID.equals(killerId)) {
            return SparkWitchRoleRegistry.insider();
        }

        // 2) The seven witch-faction roles turn the cat into a Bewitched.
        // 2) 魔女阵营那七个角色让猫变成魔化使。
        if (WITCH_ROLES.contains(killerId)) {
            return SparkWitchRoleRegistry.bewitched();
        }

        // 3) Any killer-faction role (including passengers carrying an Insider) turns the cat into a killer.
        // 3) 任何杀手阵营角色（含携带内应的乘客）让猫变成杀手。
        if (killerRole.getFaction() == Faction.KILLER) {
            return WatheRoles.KILLER;
        }

        // 4) Any civilian-faction role turns the cat into a random police role.
        // 4) 任何乘客阵营角色让猫变成随机警职。
        if (killerRole.getFaction() == Faction.CIVILIAN) {
            return firstUnusedPoliceRole(game, world);
        }

        // 5) Every other faction (neutral) leaves the cat dead.
        // 5) 其余阵营（中立）击杀直接让猫死亡。
        return null;
    }

    /**
     * Police candidates in preference order. Owner-specified set: vigilante, judge, Emma, seeker, veteran, knight,
     * control expert, USEC.
     * 警职候选，按优先级排列。所有者指定集合：义警、法官、樱羽艾玛、搜寻者、老兵、骑士、控场专家、USEC。
     */
    private static final String[] POLICE_PATHS =
            {"vigilante", "judge", "emma", "seeker", "veteran", "knight", "control_expert", "usec"};

    /** Picks a police role nobody holds this round; falls back to the vigilante. 挑一个本局没人担任的警职，回退义警。 */
    private static @Nullable Role firstUnusedPoliceRole(GameWorldComponent game, ServerWorld world) {
        if (game == null) {
            return null;
        }
        java.util.Set<Identifier> present = new java.util.HashSet<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            Role role = game.getRole(player);
            if (role != null) {
                present.add(role.identifier());
            }
        }
        for (String path : POLICE_PATHS) {
            Role candidate = policeRole(path);
            if (candidate != null && !present.contains(candidate.identifier())) {
                return candidate;
            }
        }
        return WatheRoles.VIGILANTE;
    }

    /** Resolves a police role by path, using SparkWitch's own getters first and NoellesRoles' static fields after. 按路径取警职：先 SparkWitch 自有 getter，再诺艾尔模组的静态字段。 */
    private static @Nullable Role policeRole(String path) {
        switch (path) {
            case "judge" -> {
                return SparkWitchRoleRegistry.judge();
            }
            case "usec" -> {
                return SparkWitchRoleRegistry.usec();
            }
            case "knight" -> {
                return SparkWitchRoleRegistry.knight();
            }
            default -> {
                try {
                    Class<?> noelles = Class.forName("org.agmas.noellesroles.Noellesroles");
                    Object value = noelles.getField(path.toUpperCase(java.util.Locale.ROOT)).get(null);
                    return value instanceof Role role ? role : null;
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
    }

    /**
     * Called from the {@code killPlayer} mixin. Returns true when the kill was taken over (the cat turned into a
     * spectre instead of dying). Needed because knife and gun kills pass {@code force=true}, which makes Wathe ignore
     * any {@code KillPlayer.BEFORE} cancellation.
     * 由 {@code killPlayer} 混入调用。返回 true 表示这次致命伤已被接管（猫进入幽魂状态而非死亡）。
     * 之所以必须这样，是因为刀与枪传入 {@code force=true}，Wathe 会忽略 KillPlayer.BEFORE 的取消结果。
     */
    /**
     * Applies the three-second spectre state: invisible, no collision, silent.
     * 施加三秒幽魂状态：隐身、无碰撞、静音。
     */
    private static void enterSpectre(ServerPlayerEntity player) {
        player.setInvisible(true);
        player.setInvulnerable(true);
        // noClip removes collision so the body cannot be shoved off the train during the window.
        // noClip 关闭碰撞，避免三秒内被推下车。
        player.noClip = true;
        player.setSilent(true);
    }

    /** Undoes {@link #enterSpectre}. 撤销幽魂状态。 */
    private static void leaveSpectre(ServerPlayerEntity player) {
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.noClip = false;
        player.setSilent(false);
    }

    public static boolean interceptLethalHit(ServerPlayerEntity victim,
                                             @Nullable ServerPlayerEntity killer,
                                             Identifier deathReason) {
        if (!isCat(victim)) {
            return false;
        }
        if (TRANSFORMED.contains(victim.getUuid())) {
            dev.caecorthus.sparkwitch.SparkWitch.LOGGER.info(
                    "[cat] {} already transformed this round; dying for real", victim.getGameProfile().getName());
            return false;
        }
        dev.caecorthus.sparkwitch.SparkWitch.LOGGER.info(
                "[cat] lethal hit on {} by {} (reason={}) - evaluating transformation",
                victim.getGameProfile().getName(),
                killer == null ? "null" : killer.getGameProfile().getName(), deathReason);
        if (NO_TRANSFORM_REASONS.contains(deathReason)) {
            return false;
        }
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Role killerRole = killer == null ? null : game.getRole(killer);
        if (killerRole == null && killer != null) {
            killerRole = game.getRoles().get(killer.getUuid());
        }
        if (killerRole == null) {
            return false;
        }
        Role next = transformTarget(killerRole, game, world);
        dev.caecorthus.sparkwitch.SparkWitch.LOGGER.info(
                "[cat] killer role={} faction={} -> next={}", killerRole.identifier(),
                killerRole.getFaction(), next == null ? "DEATH" : next.identifier());
        if (next == null) {
            return false;
        }
        TRANSFORMED.add(victim.getUuid());
        enterSpectre(victim);
        PENDING.put(victim.getUuid(), new Pending(world, next, world.getServer().getTicks() + HOLD_TICKS));
        return true;
    }

    private static boolean isCat(ServerPlayerEntity player) {
        // Roster first: after a kill the live role lookup can already be unreliable.
        // 先查名单：被击杀之后实时角色查询可能已经不可靠。
        if (CATS.contains(player.getUuid())) {
            return true;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && SchrodingersCatRules.ROLE_ID.equals(role.identifier());
    }

    /** Swaps the role, resets sanity and sends the cat back to its spawn room; carried coins are untouched. 换身份、重置理智并送回出生房间；金币保留。 */
    /**
     * Mirrors what Wathe's own {@code RoleAssigned} handler hands out, without firing that event.
     * <p>Firing the event would also run every other mod's listener, which is not the cat's business. The logic here is
     * copied from {@code WatheEventHandlers.registerRoleAssignedHandler} so the two stay independent.
     * 借鉴 Wathe 自己在 {@code RoleAssigned} 里的发放逻辑，但不触发该事件。
     * 触发事件会把其他模组的监听器一并叫起来，那不是猫该管的事。这里的实现照抄
     * {@code WatheEventHandlers.registerRoleAssignedHandler}，使两边互不干扰。
     */
    private static void grantStartingKit(ServerPlayerEntity player, Role role) {
        if (role == null) {
            return;
        }
        Identifier id = role.identifier();
        String path = id.getPath();

        // Per-role starting weapon, taken from each role's own loadout code rather than a generic guess.
        // 按身份发放初始武器，取值自各身份自己的装备代码，而不是笼统猜测。
        switch (path) {
            // tip.letter.judge / control_expert / seeker: “开局持有左轮手枪”; Emma's revolver comes from her own kit.
            // 法官 / 控场专家 / 搜寻者的说明都写着“开局持有左轮手枪”，艾玛的左轮来自她自己的配置。
            case "vigilante", "judge", "emma", "control_expert" ->
                    player.giveItemStack(new net.minecraft.item.ItemStack(dev.doctor4t.wathe.index.WatheItems.REVOLVER));
            case "seeker" -> {
                player.giveItemStack(new net.minecraft.item.ItemStack(dev.doctor4t.wathe.index.WatheItems.REVOLVER));
                // tip.letter.seeker.tooltip1: revolver, seeker car and tablet. The tablet is issued by SparkStrength
                // for free at round start (see SparkStrengthTabletCompat), so only the car is granted here.
                // 说明写的是左轮、搜寻小车与平板电脑；平板由 SparkStrength 开局免费发放
                // （见 SparkStrengthTabletCompat），所以这里只补小车。
                net.minecraft.item.Item car = net.minecraft.registry.Registries.ITEM
                        .get(dev.caecorthus.sparkwitch.SparkWitchItems.SEEKER_CAR_ID);
                if (car != net.minecraft.item.Items.AIR) {
                    player.giveItemStack(new net.minecraft.item.ItemStack(car));
                }
            }
            case "veteran" -> {
                dev.doctor4t.wathe.cca.PlayerVeteranComponent.KEY.get(player).initialize();
                player.giveItemStack(new net.minecraft.item.ItemStack(dev.doctor4t.wathe.index.WatheItems.KNIFE));
            }
            case "knight" ->
                    // Knight fights with a baton and can be issued knives; the crowbar is its signature tool.
                    // 骑士以警棍作战、也可获发小刀；撬棍是它的标志性工具。
                    player.giveItemStack(new net.minecraft.item.ItemStack(dev.doctor4t.wathe.index.WatheItems.CROWBAR));
            case "usec" -> {
                // USEC's signature weapon is the scoped rifle, registered as sparkwitch:usec_rifle.
                // USEC 的标志性武器是带镜步枪，注册名 sparkwitch:usec_rifle。
                net.minecraft.item.Item rifle = net.minecraft.registry.Registries.ITEM
                        .get(dev.caecorthus.sparkwitch.SparkWitchItems.USEC_RIFLE_ID);
                if (rifle != net.minecraft.item.Items.AIR) {
                    player.giveItemStack(new net.minecraft.item.ItemStack(rifle));
                }
            }
            default -> {
            }
        }

        // Killer-faction kit mirrors Wathe's own handler (starting money scales with excess players).
        // 杀手阵营装备与 Wathe 自身逻辑一致（起始金币随多余玩家数增长）。
        if (role.getFaction() == dev.doctor4t.wathe.api.Faction.KILLER && player.getWorld() instanceof ServerWorld world) {
            dev.doctor4t.wathe.cca.GameWorldComponent game = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(world);
            int totalPlayers = world.getPlayers().size();
            int killerCount = game.getAllKillerTeamPlayers().size();
            int killerRatio = game.getKillerDividend();
            int excessPlayers = Math.max(0, totalPlayers - (killerCount * killerRatio));
            int dynamicStartingMoney = dev.doctor4t.wathe.game.GameConstants.MONEY_START + (excessPlayers * 15);
            dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(player).setBalance(dynamicStartingMoney);
            player.giveItemStack(new net.minecraft.item.ItemStack(dev.doctor4t.wathe.index.WatheItems.WALKIE_TALKIE));
        }
    }

    /**
     * Replays Wathe's own role announcement for the new identity: the same three lines every other role change uses
     * (welcome / premise with the killer count / goal), so the cat's transformation reads like any other.
     * 复用 Wathe 自己的身份报幕：与其他身份变化相同的三行（欢迎语 / 含杀手数的前提 / 目标），
     * 让猫的转变看起来和普通身份变化一致。
     */
    private static void announceTransition(ServerPlayerEntity player, ServerWorld world,
                                           GameWorldComponent game, Role next) {
        if (next == null) {
            return;
        }
        int killers = 0;
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (game.isPlayerDead(other.getUuid())) {
                continue;
            }
            Role role = game.getRole(other);
            if (role != null && role.getFaction() == dev.doctor4t.wathe.api.Faction.KILLER) {
                killers++;
            }
        }
        int civilians = world.getPlayers().size() - killers;
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new dev.doctor4t.wathe.util.AnnounceWelcomePayload(
                        next.identifier().toString(), killers,
                        // Sentinel: tells the client this is the cat's transformation, so the first line becomes
                        // “薛定谔之猫 → 变化的身份” instead of the generic welcome.
                        // 哨兵值：告诉客户端这是猫的身份转变，第一行改为“薛定谔之猫 → 变化的身份”，
                        // 而不是通用欢迎语。
                        -999));
    }

    private static void reviveAndTransform(ServerPlayerEntity player, ServerWorld world, GameWorldComponent game, Role next) {
        // Everything here is guarded: a single failure must not abort the AFTER dispatch for every other listener.
        // 这里全程防护：任何一步失败都不能把 AFTER 的分发整个打断。
        try {
            // Clear the death bookkeeping first, then make the swap, then bring the body back.
            // 先清掉死亡记录，再换身份，最后把身体拉起来。
            reviveAndSwap(player, world, game, next);
        } catch (Throwable failure) {
            dev.caecorthus.sparkwitch.SparkWitch.LOGGER.error(
                    "[cat] revive failed for {}: {}", player.getGameProfile().getName(), failure.toString(), failure);
        }
    }

    private static void reviveAndSwap(ServerPlayerEntity player, ServerWorld world, GameWorldComponent game, Role next) {
        Object accessor = game;
        if (accessor instanceof dev.caecorthus.sparkwitch.mixin.cat.GameWorldComponentDeadAccessor dead) {
            dead.sparkwitch$getDeadPlayers().remove(player.getUuid());
        } else {
            // Accessor mixin did not apply: fall back to the public API so the player is at least not counted dead.
            // accessor 混入没生效：退回公开接口，至少别把玩家算成已死。
            dev.caecorthus.sparkwitch.SparkWitch.LOGGER.warn(
                    "[cat] deadPlayers accessor missing, cannot clear death flag for {}",
                    player.getGameProfile().getName());
        }
        game.sync();
        game.addRole(player, next);
        game.sync();
        grantStartingKit(player, next);
        // A dead player is left spectating; without these two calls the body heals but never comes back.
        // 死亡后玩家会停在旁观状态；缺了这两步，身体虽然回血但人不会回来。
        player.setCameraEntity(player);
        player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
        // Wathe re-applies the spectator mode while finishing the death it just processed, and that runs after this
        // listener. Restore control once more on the next tick, when the death bookkeeping is done.
        // Wathe 在收尾这次死亡时会重新设置旁观模式，而那发生在本监听器之后。
        // 因此下一 tick（死亡收尾已完成）再恢复一次控制权。
        world.getServer().execute(() -> {
            if (player.isAlive() && player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
                player.setCameraEntity(player);
                player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            }
        });
        player.setHealth(player.getMaxHealth());
        player.setAir(player.getMaxAir());
        player.setInvulnerable(false);
        // Revive in place: the cat never left the train, so teleporting to the world spawn could drop it outside.
        // 原地复活：猫并没有离开列车，传送到世界出生点反而可能把它扔到车外。
        // Kill off the momentum first: a gunshot knocks the body around, and reviving with that velocity sends the
        // cat straight off the train. Also reset fall distance so the revive itself cannot be ruled a fall.
        // 先清掉动量：枪击会把身体推走，带着这个速度复活会直接把猫送出车外。
        // 同时清零坠落距离，避免复活本身被判定成坠落。
        // Mirrors BlackRavenCrowLandingService.land(): teleport first, then zero the velocity, flag it for the client
        // (velocityModified), clear the fall-distance field directly, and drop the active item.
        // 与 BlackRavenCrowLandingService.land() 一致：先传送，再清零速度、标记 velocityModified、
        // 直接清 fallDistance 字段，最后清掉正在使用的物品。
        net.minecraft.util.math.Vec3d target = SAFE_SPOTS.getOrDefault(player.getUuid(), player.getPos());
        player.clearActiveItem();
        player.teleport(world, target.x, target.y, target.z,
                java.util.Set.of(), player.getYaw(), player.getPitch());
        player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;

        // The classic totem sting marks the identity change audibly.
        // 用不死图腾的音效，让这次身份转变听得见。
        // The revived player must leave the dead-talkers voice group, or they keep chatting with corpses.
        // 复活的玩家必须退出死亡语音组，否则还能和尸体对话。
        dev.caecorthus.sparkwitch.voice.SparkWitchVoiceChatPlugin.leaveDeathVoiceGroup(player.getUuid());
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.recordSkill(
                player, dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.CAT_TRANSFORM, null, null);
        player.sendMessage(Text.translatable("message.sparkwitch.cat.transformed",
                Text.translatable("announcement.role." + next.identifier().getPath())), true);
        // Traits must follow the new identity; last_stand is excluded so it cannot claim the second life.
        // 词条跟随新身份重发；排除 last_stand，避免它认领这条第二次生命。
        try {
            Class.forName("dev.caecorthus.sparktraits.impl.assignment.TraitAssignmentService")
                    .getMethod("reassignForPlayer", ServerPlayerEntity.class, java.util.Set.class)
                    .invoke(null, player, java.util.Set.of(Identifier.of("sparktraits", "last_stand")));
        } catch (ReflectiveOperationException ignored) {
            // SparkTraits absent or older: leave traits untouched.
            // 未装词条模组或版本过旧：词条保持原样。
        }
    }

    private static void applyTransform(ServerPlayerEntity player, ServerWorld world, GameWorldComponent game, Role next) {
        game.addRole(player, next);
        game.sync();
        player.sendMessage(Text.translatable("message.sparkwitch.cat.transformed",
                Text.translatable("announcement.role." + next.identifier().getPath())), true);
        // Clear the old traits and re-roll for the new role, if SparkTraits is installed (soft dependency).
        // 若装有词条模组，清掉旧词条并按新身份重发同一数量的词条（软依赖，缺失时静默跳过）。
        try {
            // A cat turned police officer must never roll 绝处逢生: that trait would hand the new identity a second
            // life on top of the one the cat just spent. The exclusion happens before the pick, so the trait stays
            // available to whoever legitimately rolled it this round.
            // 变成警职的猫绝不能抽到「绝处逢生」：那会在这条刚用掉的命之外再给一次复活。
            // 排除发生在抽取之前，因此该词条仍可正常分配给本局真正抽到它的人。
            Class.forName("dev.caecorthus.sparktraits.impl.assignment.TraitAssignmentService")
                    .getMethod("reassignForPlayer", ServerPlayerEntity.class, java.util.Set.class)
                    .invoke(null, player, java.util.Set.of(Identifier.of("sparktraits", "last_stand")));
        } catch (ReflectiveOperationException ignored) {
            // SparkTraits absent or older without the helper: trait state stays as-is.
            // 未装词条模组，或版本过旧没有该方法：词条保持原样。
        }
        // Same teleport overload Last Stand uses when it sends a revived player back.
        // 使用与“背水一战”复活回送时相同的 teleport 重载。
        var spawn = world.getSpawnPos();
        player.teleport(world, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                java.util.Set.of(), player.getYaw(), player.getPitch());
    }
}
