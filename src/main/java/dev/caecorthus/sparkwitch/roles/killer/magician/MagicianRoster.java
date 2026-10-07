package dev.caecorthus.sparkwitch.roles.killer.magician;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Magician's disguise roster (owner decision 2026-10-07 D7): every round participant (Wathe's role map) captured
 * when the round starts or when a player becomes the Magician, fixed for the round. The server accepts any roster
 * member as a disguise, alive or dead, so neither the list nor a pick reveals a death.
 * 魔术师的伪装名单（所有者 2026-10-07 决定 D7）：回合开始或玩家成为魔术师时记录的全部本局参与者（Wathe 身份表），
 * 本局内固定不变。服务端接受名单内任何成员作为伪装，无论生死，因此名单和选择都不会暴露死亡。
 */
public final class MagicianRoster {
    private MagicianRoster() {
    }

    /** Captures the roster for every Magician in {@code world}. / 为 {@code world} 中每名魔术师记录名单。 */
    public static void captureAll(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            var role = game.getRole(player);
            if (role != null && SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())) {
                capture(player);
            }
        }
    }

    /** Captures {@code magician}'s roster from the current round. / 按当前回合记录 {@code magician} 的名单。 */
    public static void capture(ServerPlayerEntity magician) {
        GameWorldComponent game = GameWorldComponent.KEY.get(magician.getWorld());
        MinecraftServer server = magician.getServer();
        List<MagicianPlayerComponent.RosterEntry> entries = new ArrayList<>();
        boolean self = false;
        for (UUID uuid : game.getAllPlayers()) {
            entries.add(new MagicianPlayerComponent.RosterEntry(uuid, nameOf(server, uuid)));
            self |= uuid.equals(magician.getUuid());
        }
        if (!self) {
            entries.add(new MagicianPlayerComponent.RosterEntry(magician.getUuid(), magician.getGameProfile().getName()));
        }
        MagicianPlayerComponent.KEY.get(magician).setRoster(order(entries, magician.getUuid()));
    }

    /**
     * The Magician first, then by name (case-insensitive), then by UUID: a stable order for the paged list.
     * 魔术师本人在前，其余按名字（不区分大小写）再按 UUID 排序：分页列表的稳定顺序。
     */
    static List<MagicianPlayerComponent.RosterEntry> order(List<MagicianPlayerComponent.RosterEntry> entries,
                                                           UUID self) {
        List<MagicianPlayerComponent.RosterEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.<MagicianPlayerComponent.RosterEntry>comparingInt(e -> e.uuid().equals(self) ? 0 : 1)
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT))
                .thenComparing(MagicianPlayerComponent.RosterEntry::uuid));
        return sorted;
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        if (server == null) {
            return "";
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getUserCache() == null ? ""
                : server.getUserCache().getByUuid(uuid).map(GameProfile::getName).orElse("");
    }
}
