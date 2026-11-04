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
        BuildShopEntries.EVENT.register(AccountantService::buildShop);
        // Tasks pay coins. 任务给金币。
        dev.doctor4t.wathe.api.event.TaskComplete.EVENT.register((player, taskType) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isAccountant(serverPlayer)) {
                PlayerShopComponent shop = PlayerShopComponent.KEY.get(serverPlayer);
                shop.setBalance(shop.getBalance() + AccountantRules.TASK_COINS);
                serverPlayer.sendMessage(Text.translatable("message.sparkwitch.accountant.task_coins",
                        AccountantRules.TASK_COINS).formatted(Formatting.GOLD), true);
            }
        });
        // Natural income every 10 s. 每 10 秒自然收入。
        ServerTickEvents.END_SERVER_TICK.register(AccountantService::tick);
    }

    public static boolean isAccountant(net.minecraft.entity.player.PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && AccountantRules.ROLE_ID.equals(role.identifier());
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

    /** Natural income: +5 coins every 10 s. 自然收入：每 10 秒 +5 金币。 */
    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        if (now <= 0L || now % AccountantRules.PASSIVE_INTERVAL_TICKS != 0L) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (isAccountant(player)) {
                    PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
                    shop.setBalance(shop.getBalance() + AccountantRules.PASSIVE_COINS);
                }
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
                // Owner 2026-11-03: no stock cap, but the 30 s purchase cooldown stays.
                // 所有者 2026-11-03：不限购，但保留 30 秒购买冷却。
                .cooldown(AccountantRules.PASSBOOK_PURCHASE_COOLDOWN_TICKS)
                .build());
    }
}
