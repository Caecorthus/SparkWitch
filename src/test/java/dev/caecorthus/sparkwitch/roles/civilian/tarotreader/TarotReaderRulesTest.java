package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.api.Faction;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TarotReaderRulesTest {
    private static final String RULES =
            "dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderRules";

    @Test
    void exposesApprovedRoleEconomyShopAndSessionValues() throws Exception {
        Class<?> rules = Class.forName(RULES);

        assertEquals(0xAEE1CF, rules.getField("COLOR").getInt(null));
        assertEquals(0, rules.getField("INITIAL_MONEY").getInt(null));
        assertEquals(50, rules.getField("TASK_MONEY_REWARD").getInt(null));
        assertEquals(200, rules.getField("REGULAR_PRICE").getInt(null));
        assertEquals(50, rules.getField("IDENTITY_PRICE").getInt(null));
        assertEquals(50, rules.getField("SURVIVAL_PRICE").getInt(null));
    }

    @Test
    void factionClassificationIsExclusiveAndFallsBackOnlyForCustomFactions() throws Exception {
        assertEquals("WITCH", bucket(SparkWitchFactions.WITCH, Faction.CIVILIAN));
        assertEquals("CIVILIAN", bucket(FactionIds.CIVILIAN, Faction.KILLER));
        assertEquals("KILLER", bucket(FactionIds.KILLER, Faction.CIVILIAN));
        assertEquals("NEUTRAL", bucket(FactionIds.NEUTRAL, Faction.CIVILIAN));
        assertEquals("NEUTRAL", bucket(Identifier.of("sparkwitch", "custom"), Faction.NEUTRAL));
        assertNull(bucket(FactionIds.NONE, Faction.CIVILIAN));
        assertNull(bucket(Identifier.of("sparkwitch", "custom"), Faction.NONE));
    }

    @Test
    void activeCountAndDivinationPredicatesMatchServerAuthorityRules() throws Exception {
        assertEquals(true, invoke("shouldCountActivePlayer",
                new Class<?>[]{boolean.class, boolean.class, boolean.class}, true, false, false));
        assertEquals(false, invoke("shouldCountActivePlayer",
                new Class<?>[]{boolean.class, boolean.class, boolean.class}, false, false, false));
        assertEquals(false, invoke("shouldCountActivePlayer",
                new Class<?>[]{boolean.class, boolean.class, boolean.class}, true, true, false));
        assertEquals(false, invoke("shouldCountActivePlayer",
                new Class<?>[]{boolean.class, boolean.class, boolean.class}, true, false, true));

        assertEquals(true, invoke("identityWasAssigned",
                new Class<?>[]{boolean.class, int.class}, true, 0));
        assertEquals(true, invoke("identityWasAssigned",
                new Class<?>[]{boolean.class, int.class}, false, 1));
        assertEquals(false, invoke("identityWasAssigned",
                new Class<?>[]{boolean.class, int.class}, false, 0));
        assertEquals(true, invoke("isTargetAlive",
                new Class<?>[]{boolean.class, boolean.class}, true, false));
        assertEquals(false, invoke("isTargetAlive",
                new Class<?>[]{boolean.class, boolean.class}, true, true));
        assertEquals(false, invoke("isTargetAlive",
                new Class<?>[]{boolean.class, boolean.class}, false, false));
    }

    private static String bucket(Identifier effectiveFaction, Faction nativeFaction) throws Exception {
        Object value = invoke(
                "classifyFaction",
                new Class<?>[]{Identifier.class, Faction.class},
                effectiveFaction,
                nativeFaction
        );
        return value == null ? null : ((Enum<?>) value).name();
    }

    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) throws Exception {
        Class<?> rules = Class.forName(RULES);
        Method method = rules.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
}
