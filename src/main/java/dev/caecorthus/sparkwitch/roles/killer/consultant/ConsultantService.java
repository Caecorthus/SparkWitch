package dev.caecorthus.sparkwitch.roles.killer.consultant;

import dev.caecorthus.sparkwitch.component.ConsultantPlayerComponent;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of 顾问: paid identity inspection, teammate-kill bonus and the shop restrictions.
 * 顾问的服务端：付费查看身份、同伙击杀加成与商店限制。
 */
public final class ConsultantService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    /** Shop entries the consultant may never buy. 顾问永远买不到的商店条目。 */
    private static final String[] BLOCKED_SHOP_IDS = {"psycho_mode", "grenade", "m67", "m67_grenade"};

    private ConsultantService() {
    }

    public static void register() {
        // Own record book at assignment. 分配到身份时发放记录册。
        RoleAssigned.EVENT.register((player, role) -> {
            if (role != null && ConsultantRules.ROLE_ID.equals(role.identifier())
                    && player instanceof ServerPlayerEntity serverPlayer) {
                COOLDOWN_UNTIL.put(serverPlayer.getUuid(), 0L);
                // Wipe last round's records: the component survives respawn, so stale names would show up.
                // 清空上一局的记录：组件跨局保留，不清会把旧名单带进来。
                ConsultantPlayerComponent.KEY.get(serverPlayer).clearRecords();
                // A real written book: right-clicking opens vanilla's paged reader.
                // 真正的成书：右键打开原版可翻页阅读界面。
                serverPlayer.getInventory().insertStack(buildRecordBook(new java.util.LinkedHashSet<>()));
            }
        });

        // Teammate kill: every living consultant gets a small extra cut on top of Wathe's 15.
        // 同伙击杀：每名存活的顾问在 Wathe 的 15 金币之外再拿一份。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (killer == null || !(victim.getWorld() instanceof ServerWorld world)) {
                return;
            }
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            Role killerRole = game.getRole(killer);
            if (killerRole == null || killerRole.getFaction() != Faction.KILLER) {
                return;
            }
            for (ServerPlayerEntity candidate : world.getPlayers()) {
                Role role = game.getRole(candidate);
                if (role == null || !ConsultantRules.ROLE_ID.equals(role.identifier())
                        || game.isPlayerDead(candidate.getUuid())) {
                    continue;
                }
                PlayerShopComponent.KEY.get(candidate).addToBalance(ConsultantRules.TEAMMATE_KILL_BONUS);
            }
        });

        // Shop: no madman / grenades, and the revolver costs 200 for this role only.
        // 商店：买不到疯魔与各类手雷；左轮对该身份是 200 金币。
        dev.doctor4t.wathe.api.event.BuildShopEntries.EVENT.register((player, context) -> {
            Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
            if (role == null || !ConsultantRules.ROLE_ID.equals(role.identifier())) {
                return;
            }
            context.getEntries().removeIf(entry -> {
                String id = entry.id();
                for (String blocked : BLOCKED_SHOP_IDS) {
                    if (blocked.equals(id)) {
                        return true;
                    }
                }
                return id.contains("m67");
            });
            context.getEntries().removeIf(entry -> "revolver".equals(entry.id()));
            context.getEntries().add(new dev.doctor4t.wathe.util.ShopEntry.Builder(
                    "revolver",
                    dev.doctor4t.wathe.index.WatheItems.REVOLVER.getDefaultStack(),
                    200,
                    dev.doctor4t.wathe.util.ShopEntry.Type.WEAPON
            ).build());
        });
    }

    /** Handles one paid inspection. 处理一次付费查看。 */
    public static void inspect(ServerPlayerEntity caster, UUID targetId) {
        if (caster == null || targetId == null || !(caster.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Role casterRole = game.getRole(caster);
        if (casterRole == null || !ConsultantRules.ROLE_ID.equals(casterRole.identifier())) {
            return;
        }
        // A dead consultant cannot inspect anything.
        // 已死亡的顾问不能使用技能。
        if (!caster.isAlive() || game.isPlayerDead(caster.getUuid())) {
            return;
        }
        long now = world.getTime();
        if (now < COOLDOWN_UNTIL.getOrDefault(caster.getUuid(), 0L)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null || target == caster) {
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(caster);
        if (shop.getBalance() < ConsultantRules.INSPECT_COST) {
            caster.sendMessage(Text.translatable(
                    "message.sparkwitch.planner.not_enough_money", ConsultantRules.INSPECT_COST), true);
            return;
        }
        Role targetRole = game.getRole(target);
        if (targetRole == null) {
            return;
        }
        // An undercover reads as the killer it is disguised as, not as "undercover".
        // 卧底翻出来的是他所伪装的那个杀手，而不是“卧底”。
        Identifier shownId = targetRole.identifier();
        if (Identifier.of("noellesroles", "undercover").equals(shownId)) {
            Identifier disguise = dev.caecorthus.sparkwitch.component.UndercoverDisguiseComponent.KEY
                    .get(target).disguise();
            if (disguise != null) {
                shownId = disguise;
            }
        }
        // Wathe names roles through announcement.role.<path>. 角色名走 announcement.role.<path> 翻译键。
        String roleName = Text.translatable("announcement.role." + shownId.getPath()).getString();
        String line = target.getGameProfile().getName() + " — " + roleName;
        ConsultantPlayerComponent component = ConsultantPlayerComponent.KEY.get(caster);
        boolean added = component.record(line);
        shop.addToBalance(-ConsultantRules.INSPECT_COST);
        COOLDOWN_UNTIL.put(caster.getUuid(), now + ConsultantRules.COOLDOWN_TICKS);
        dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.recordSkill(
                caster, dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.CONSULTANT_INSPECT, target, null);
        refreshRecordBook(caster, component.records());
        // Action bar, not chat: role feedback must not fill the chat log. / 走动作栏而非聊天框：身份反馈不占聊天记录。
        caster.sendMessage(Text.translatable(
                added ? "message.sparkwitch.consultant.recorded" : "message.sparkwitch.consultant.already", line), true);
    }

    /** Builds the record book item with one page per recorded identity. 构造记录册成书，每条记录一页。 */
    private static net.minecraft.item.ItemStack buildRecordBook(java.util.Collection<String> records) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
        java.util.List<String> pages = new java.util.ArrayList<>();
        if (records.isEmpty()) {
            pages.add("记录册\n\n还没有记录任何人的身份。");
        } else {
            StringBuilder builder = new StringBuilder("记录册\n\n");
            int perPage = 0;
            for (String line : records) {
                builder.append("· ").append(line).append("\n");
                if (++perPage >= 8) {
                    pages.add(builder.toString());
                    builder.setLength(0);
                    perPage = 0;
                }
            }
            if (perPage > 0 || pages.isEmpty()) {
                pages.add(builder.toString());
            }
        }
        java.util.List<net.minecraft.text.RawFilteredPair<net.minecraft.text.Text>> pagePairs =
                new java.util.ArrayList<>();
        for (String page : pages) {
            pagePairs.add(net.minecraft.text.RawFilteredPair.of(net.minecraft.text.Text.literal(page)));
        }
        stack.set(net.minecraft.component.DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new net.minecraft.component.type.WrittenBookContentComponent(
                        net.minecraft.text.RawFilteredPair.of("记录册"),
                        "顾问", 0, pagePairs, false));
        return stack;
    }

    /** Replaces the carried record book so its pages match the stored records. 用最新记录替换随身携带的记录册。 */
    private static void refreshRecordBook(ServerPlayerEntity player, java.util.Set<String> records) {
        var inventory = player.getInventory();
        // Drop every carried written book first, so duplicates never accumulate.
        // 先清掉背包里所有成书，避免重复累积。
        boolean hadBook = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(net.minecraft.item.Items.WRITTEN_BOOK)) {
                inventory.setStack(slot, net.minecraft.item.ItemStack.EMPTY);
                hadBook = true;
            }
        }
        net.minecraft.item.ItemStack book = buildRecordBook(records);
        if (hadBook) {
            inventory.insertStack(book);
        }
    }
}
