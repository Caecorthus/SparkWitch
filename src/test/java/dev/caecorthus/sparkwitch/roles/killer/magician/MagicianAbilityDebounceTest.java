package dev.caecorthus.sparkwitch.roles.killer.magician;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicianAbilityDebounceTest {
    @Test
    void allowsTheFirstRequestWhenNoPreviousTickExists() {
        // 首次技能请求没有历史时间，不应被当成重复包拦截。
        assertFalse(MagicianAbility.shouldRejectDebouncedRequest(100L, null));
    }

    @Test
    void rejectsRequestsInsideTheFourTickDebounceWindow() {
        assertTrue(MagicianAbility.shouldRejectDebouncedRequest(103L, 100L));
    }

    @Test
    void allowsRequestsAtTheDebounceWindowBoundary() {
        // 去抖条件是“间隔小于 4 tick”，到达第 4 tick 时即可接受下一次操作。
        assertFalse(MagicianAbility.shouldRejectDebouncedRequest(104L, 100L));
    }
}
