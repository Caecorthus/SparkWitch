package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import dev.doctor4t.wathe.record.replay.DefaultReplayFormatters;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.UUID;

public final class GrandWitchShopReplayService {
    private static final String MANA_REPLAY_TRANSLATION_KEY = "replay.shop_purchase.sparkwitch.mana";

    private GrandWitchShopReplayService() {
    }

    /**
     * Wathe exposes one global shop formatter; non-spell and malformed records use its exact default fallback.
     * Wathe 只公开一个全局商店格式化器；非魔法和格式异常的记录完全回退到其默认实现。
     */
    public static void register() {
        ReplayRegistry.registerFormatter(GameRecordTypes.SHOP_PURCHASE,
                GrandWitchShopReplayService::formatShopPurchase);
    }

    static Text formatShopPurchase(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        GrandWitchRules.GrandWitchSpell spell = GrandWitchRules.GrandWitchSpell.fromEntryId(
                data.getString("entry_id")
        );
        if (spell == null || !data.containsUuid("actor")) {
            return DefaultReplayFormatters.formatShopPurchase(event, match, world);
        }

        UUID actorUuid = data.getUuid("actor");
        Text playerText = ReplayGenerator.formatPlayerName(
                actorUuid,
                ReplayGenerator.getPlayerInfoCache(match)
        );
        Text itemName = ReplayGenerator.formatItemName(data, world);
        return Text.translatable(MANA_REPLAY_TRANSLATION_KEY, playerText, itemName, spell.manaCost());
    }
}
