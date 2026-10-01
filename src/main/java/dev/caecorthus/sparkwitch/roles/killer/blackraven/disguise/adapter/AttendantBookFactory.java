package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

/**
 * Builds the Attendant's written room book exactly like NoellesRoles' round-start kit (literal title and author).
 * Replica of the pinned NoellesRoles 1.7.6 RoleAssigned ATTENDANT branch; keep the literals byte-identical.
 * 按诺艾尔职业开局物品的方式构建乘务员的房间手册（标题与作者为原文字面值）。
 * 复刻固定版本 NoellesRoles 1.7.6 RoleAssigned 中乘务员分支；字面值必须逐字一致。
 */
public final class AttendantBookFactory {
    static final String TITLE = "列车信息手册";
    static final String AUTHOR = "乘务员";
    static final String ROOM_HEADER_PREFIX = "§l§1【";
    static final String ROOM_HEADER_SUFFIX = "】§r\n\n";
    static final String EMPTY_ROOM = "§8（空房间）§r";
    static final String UNKNOWN_PLAYER = "未知";
    static final String PLAYER_PREFIX = "§0• ";
    static final String PLAYER_SUFFIX = "§r\n";

    private AttendantBookFactory() {
    }

    public static ItemStack create(ServerPlayerEntity player) {
        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(player.getWorld());
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        HashMap<Integer, GameWorldComponent.RoomData> rooms = gameWorldComponent.getRooms();
        HashMap<UUID, GameProfile> profiles = gameWorldComponent.getGameProfiles();
        if (!rooms.isEmpty()) {
            List<GameWorldComponent.RoomData> sortedRooms = new ArrayList<>(rooms.values());
            sortedRooms.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
            for (GameWorldComponent.RoomData room : sortedRooms) {
                List<String> names = new ArrayList<>();
                for (UUID playerUuid : room.getPlayers()) {
                    GameProfile profile = profiles.get(playerUuid);
                    names.add(profile != null ? profile.getName() : null);
                }
                pages.add(RawFilteredPair.of(Text.literal(pageContent(room.getName(), names))));
            }
        }

        WrittenBookContentComponent bookContent = new WrittenBookContentComponent(
                RawFilteredPair.of(TITLE),
                AUTHOR,
                0,
                pages,
                true
        );
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent);
        return book;
    }

    /** One room page; a null name renders as the upstream "unknown" literal. / 单个房间页；null 名称显示为上游“未知”。 */
    static String pageContent(String roomName, List<String> playerNames) {
        StringBuilder pageContent = new StringBuilder();
        pageContent.append(ROOM_HEADER_PREFIX).append(roomName).append(ROOM_HEADER_SUFFIX);
        if (playerNames.isEmpty()) {
            pageContent.append(EMPTY_ROOM);
        } else {
            for (String name : playerNames) {
                pageContent.append(PLAYER_PREFIX).append(name != null ? name : UNKNOWN_PLAYER).append(PLAYER_SUFFIX);
            }
        }
        return pageContent.toString();
    }
}
