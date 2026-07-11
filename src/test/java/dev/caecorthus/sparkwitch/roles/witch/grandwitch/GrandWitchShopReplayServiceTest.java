package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import dev.doctor4t.wathe.record.replay.ReplayEventFormatter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchShopReplayServiceTest {
    private static final UUID ACTOR = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    void formatterUsesManaForOnlyTheFourGrandWitchSpells() throws Exception {
        ReplayEventFormatter formatter = GrandWitchShopReplayService::formatShopPurchase;
        GameRecordManager.MatchRecord match = emptyMatch();

        assertManaReplay(formatter, match, "sparkwitch_obscure", 80);
        assertManaReplay(formatter, match, "sparkwitch_blindness", 80);
        assertManaReplay(formatter, match, "sparkwitch_fear", 50);
        assertManaReplay(formatter, match, "sparkwitch_heaviness", 60);

        Text revolver = formatter.format(event("revolver", ACTOR, 300), match, null);
        assertTranslation(revolver, "replay.shop_purchase", 300);

        for (String entryId : List.of(
                "lockpick", "poison_vial", "scorpion", "sparkwitch_unknown", ""
        )) {
            Text text = formatter.format(event(entryId, ACTOR, 37), match, null);
            assertTranslation(text, "replay.shop_purchase", 37);
        }

        assertNull(formatter.format(event("sparkwitch_obscure", null, 37), match, null));
    }

    @Test
    void languageFilesContainTheExactManaReplayWording() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));

        assertTrue(english.contains("\"replay.shop_purchase.sparkwitch.mana\": \"%s purchased %s for §5%d§r mana\""));
        assertTrue(chinese.contains("\"replay.shop_purchase.sparkwitch.mana\": \"%s 购买了 %s，花费§5%d§r点魔力值\""));
    }

    private static void assertManaReplay(
            ReplayEventFormatter formatter,
            GameRecordManager.MatchRecord match,
            String entryId,
            int manaCost
    ) {
        Text text = formatter.format(event(entryId, ACTOR, 0), match, null);
        assertTranslation(text, "replay.shop_purchase.sparkwitch.mana", manaCost);
    }

    private static void assertTranslation(Text text, String key, int cost) {
        TranslatableTextContent content = assertInstanceOf(TranslatableTextContent.class, text.getContent());
        assertEquals(key, content.getKey());
        assertEquals(cost, content.getArgs()[2]);
    }

    private static GameRecordEvent event(String entryId, UUID actor, int pricePaid) {
        NbtCompound data = new NbtCompound();
        if (!entryId.isEmpty()) {
            data.putString("entry_id", entryId);
        }
        if (actor != null) {
            data.putUuid("actor", actor);
        }
        data.putInt("price_paid", pricePaid);
        return new GameRecordEvent(UUID.randomUUID(), 0, GameRecordTypes.SHOP_PURCHASE, 0, 0, data);
    }

    private static GameRecordManager.MatchRecord emptyMatch() throws Exception {
        Constructor<GameRecordManager.MatchRecord> constructor = GameRecordManager.MatchRecord.class
                .getDeclaredConstructor(
                        UUID.class,
                        Identifier.class,
                        Identifier.class,
                        Identifier.class,
                        long.class,
                        long.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(
                UUID.randomUUID(),
                Identifier.of("minecraft", "overworld"),
                Identifier.of("wathe", "classic"),
                Identifier.of("wathe", "none"),
                0L,
                0L
        );
    }
}
