package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会计 runtime: the audit slip, the passbook listing and the cooldown readout.
 * 会计运行期：查账纸条、存折上架与冷却显示。
 */
public final class AccountantService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private AccountantService() {
    }

    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && role != null && AccountantRules.ROLE_ID.equals(role.identifier())) {
                COOLDOWN_UNTIL.put(serverPlayer.getUuid(),
                        serverPlayer.getServer().getTicks() + (long) AccountantRules.INITIAL_COOLDOWN_TICKS);
                AccountantComponent.KEY.get(serverPlayer)
                        .setCooldownSeconds(AccountantRules.INITIAL_COOLDOWN_TICKS / 20);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(AccountantService::tick);
        BuildShopEntries.EVENT.register(AccountantService::buildShop);
    }

    public static boolean isAccountant(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && AccountantRules.ROLE_ID.equals(role.identifier());
    }

    /** Cooldown in seconds for the HUD. HUD 用的剩余秒数。 */
    public static int cooldownSeconds(ServerPlayerEntity player) {
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until == null) {
            return 0;
        }
        long left = until - player.getServer().getTicks();
        return left > 0 ? (int) Math.ceil(left / 20.0) : 0;
    }

    /** The audit: builds the slip and hands it over. 查账：生成纸条并交给玩家。 */
    public static void audit(ServerPlayerEntity player) {
        if (!isAccountant(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        long now = player.getServer().getTicks();
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until != null && now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.accountant.cooldown",
                    (until - now) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        COOLDOWN_UNTIL.put(player.getUuid(), now + (long) AccountantRules.COOLDOWN_TICKS);
        List<String> lines = leaderboard(player);
        ItemStack slip = new ItemStack(Items.PAPER);
        slip.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.sparkwitch.accountant_note").formatted(Formatting.GOLD));
        List<Text> lore = new ArrayList<>();
        lore.add(Text.translatable("item.sparkwitch.accountant_note.header").formatted(Formatting.GRAY));
        for (String line : lines) {
            lore.add(Text.literal(line).formatted(Formatting.WHITE));
        }
        slip.set(DataComponentTypes.LORE, new LoreComponent(lore));
        if (!player.getInventory().insertStack(slip)) {
            player.dropItem(slip, false);
        }
        player.sendMessage(Text.translatable("message.sparkwitch.accountant.got_slip")
                .formatted(Formatting.GOLD), true);
        NbtCompound replay = new NbtCompound();
        replay.putString("action", "audit");
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                player, AccountantRules.ROLE_ID, null, replay);
    }

    /** Top five wallets among living players. 存活玩家里金币前 5 名。 */
    private static List<String> leaderboard(ServerPlayerEntity player) {
        List<String> lines = new ArrayList<>();
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return lines;
        }
        record Row(String name, int coins) {
        }
        List<Row> rows = new ArrayList<>();
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(other)) {
                continue;
            }
            rows.add(new Row(other.getGameProfile().getName(),
                    PlayerShopComponent.KEY.get(other).getBalance()));
        }
        rows.sort(Comparator.comparingInt(Row::coins).reversed());
        for (int i = 0; i < Math.min(AccountantRules.LEADERBOARD_SIZE, rows.size()); i++) {
            Row row = rows.get(i);
            lines.add((i + 1) + ". " + row.name() + " — " + row.coins());
        }
        return lines;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isAccountant(player)) {
                    continue;
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
                AccountantComponent.KEY.get(player).setCooldownSeconds(seconds);
            }
        }
    }

    /** The shop row: the passbook, four purchases, 30 s apart. 商店条目：存折，4 次，间隔 30 秒。 */
    private static void buildShop(net.minecraft.entity.player.PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!isAccountant(player)) {
            return;
        }
        ItemStack passbook = new ItemStack(
                dev.caecorthus.sparkwitch.SparkWitchItems.accountantPassbook());
        passbook.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.sparkwitch.accountant_passbook"));
        context.addEntry(new ShopEntry.Builder("sparkwitch:accountant_passbook", passbook,
                AccountantRules.PASSBOOK_PRICE, ShopEntry.Type.TOOL)
                .cooldown(AccountantRules.PASSBOOK_PURCHASE_COOLDOWN_TICKS)
                .stock(AccountantRules.PASSBOOK_STOCK)
                .build());
    }
}
