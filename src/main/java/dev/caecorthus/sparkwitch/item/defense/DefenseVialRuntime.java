package dev.caecorthus.sparkwitch.item.defense;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wiring of the Defense Vial: its replay lines (one per recorded {@code action}: drink, pour, tray shield) and the
 * round-edge reset of the tray ledger. Registering by this item id leaves every other item's formatter untouched.
 * 防御药剂的接线：回放文本（每种记录的 {@code action} 一条：饮用、倒入、托盘护盾）以及回合两端对托盘账本的清理。按本物品
 * id 注册，不影响其他物品的格式器。
 */
public final class DefenseVialRuntime {
    private static boolean registered;

    private DefenseVialRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(DefenseVialRules.ITEM_ID, DefenseVialRuntime::format);
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> DefenseVialTrays.clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> DefenseVialTrays.clear());
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        return switch (data.getString("action")) {
            case DefenseVialRules.ACTION_DRINK -> Text.translatable("replay.item_use.sparkwitch.defense_vial.drink", actor);
            case DefenseVialRules.ACTION_POUR -> Text.translatable("replay.item_use.sparkwitch.defense_vial.pour", actor);
            case DefenseVialRules.ACTION_TRAY_SHIELD -> data.containsUuid("pourer")
                    ? Text.translatable("replay.item_use.sparkwitch.defense_vial.tray_shield", actor,
                            ReplayGenerator.formatPlayerName(data.getUuid("pourer"), players))
                    : null;
            default -> null;
        };
    }
}
