package dev.caecorthus.sparkwitch.item.tofana;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TofanaRetaliationQueueTest {
    @Test
    void retaliationWaitsForTheTickAfterItWasQueued() {
        TofanaRetaliationQueue<String> queue = new TofanaRetaliationQueue<>();
        queue.enqueue("holder-b", "killer-a");

        List<TofanaRetaliationQueue.Attempt<String>> firstTick = new ArrayList<>();
        queue.tick(firstTick::add);
        assertEquals(List.of(), firstTick);
        assertEquals(1, queue.size());

        List<TofanaRetaliationQueue.Attempt<String>> secondTick = new ArrayList<>();
        queue.tick(secondTick::add);
        assertEquals(List.of(
                new TofanaRetaliationQueue.Attempt<>("holder-b", "killer-a")
        ), secondTick);
        assertEquals(0, queue.size());
    }

    @Test
    void chainedRetaliationWaitsForAnotherTick() {
        TofanaRetaliationQueue<String> queue = new TofanaRetaliationQueue<>();
        queue.enqueue("holder-b", "killer-a");
        queue.tick(attempt -> { });

        List<TofanaRetaliationQueue.Attempt<String>> retaliationTick = new ArrayList<>();
        queue.tick(attempt -> {
            retaliationTick.add(attempt);
            queue.enqueue("holder-a", "killer-b");
        });
        assertEquals(List.of(
                new TofanaRetaliationQueue.Attempt<>("holder-b", "killer-a")
        ), retaliationTick);
        assertEquals(1, queue.size());

        List<TofanaRetaliationQueue.Attempt<String>> chainedTick = new ArrayList<>();
        queue.tick(chainedTick::add);
        assertEquals(List.of(
                new TofanaRetaliationQueue.Attempt<>("holder-a", "killer-b")
        ), chainedTick);
    }
}
