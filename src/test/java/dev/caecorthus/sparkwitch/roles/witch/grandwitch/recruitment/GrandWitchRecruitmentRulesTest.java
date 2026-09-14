package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.GrandWitchRecruitmentRules.*;
import static org.junit.jupiter.api.Assertions.*;

class GrandWitchRecruitmentRulesTest {
    @Test
    void capacityUsesOpeningPopulationAndNeverReplenishesFromDeaths() {
        assertEquals(0, limit(-1));
        assertEquals(0, limit(17));
        assertEquals(0, limit(18));
        assertEquals(0, limit(23));
        assertEquals(1, limit(24));
        assertEquals(1, limit(29));
        assertEquals(2, limit(30));
        assertEquals(0, remaining(24, 1));
        assertEquals(0, remaining(24, 2));
        assertEquals(1, remaining(30, 1));
    }

    @Test
    void unknownItemsUseTwentyFivePerItemIncludingFoodAndDrinks() {
        assertEquals(175, refund(Map.of("food", 4, "drink", 3), Map.of()));
    }

    @Test
    void aggregateBundlesBeforeFlooringAndPreserveZeroPrice() {
        assertEquals(87, refund(Map.of("notes", 7), Map.of("notes", List.of(new Price(50, 4)))));
        assertEquals(0, refund(Map.of("free", 9), Map.of("free", List.of(new Price(0, 1)))));
    }

    @Test
    void useEffectiveDiscountedPriceAndChooseConservativeDuplicateOffer() {
        assertEquals(56, refund(Map.of("notes", 5), Map.of("notes", List.of(new Price(45, 4)))));
        assertEquals(600, refund(Map.of("grenade", 2),
                Map.of("grenade", List.of(new Price(350, 1), new Price(300, 1)))));
        assertEquals(200, refund(Map.of("random_food", 2),
                Map.of("random_food", List.of(new Price(100, 1)))));
    }

    @Test
    void originalGoldIsAddedExactlyOnceAndOverflowIsRejected() {
        assertEquals(787, balanceAfter(700, 87));
        assertThrows(ArithmeticException.class, () -> balanceAfter(Integer.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class, () -> refund(Map.of("unknown", Integer.MAX_VALUE), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Price(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Price(1, 0));
    }
}
