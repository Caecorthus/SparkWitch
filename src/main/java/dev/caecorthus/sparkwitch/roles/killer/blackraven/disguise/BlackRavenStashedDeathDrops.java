package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Plan v2 §3.4: a Black Raven dies with its stashed revolver too. On death, every stack in any disguise stash that
 * Wathe's {@code GameFunctions.shouldDropOnDeath(stack, victim)} would drop (the revolver, plus whatever other mods
 * add through {@code ShouldDropOnDeath}) is taken out of the stash and dropped the way Wathe's own death loop drops the
 * live inventory ({@code victim.dropItem(stack, true, false)}); the rest of each stash still vanishes in the default
 * phase ({@code BlackRavenDisguiseService.endForDeath}). Runs in its own {@code KillPlayer.AFTER} phase ordered BEFORE
 * {@link Event#DEFAULT_PHASE}: after BEFORE, where a death can still be cancelled, and before SparkTraits consumes its
 * Last Stand set in the default AFTER phase (a revival must not find a revolver left over). Raw role only. Server only.
 * 计划 v2 §3.4：黑羽鸦死亡时，暂存中的左轮同样掉落。死亡时，任何伪装暂存中满足 Wathe
 * {@code GameFunctions.shouldDropOnDeath(stack, victim)} 的物品（左轮，以及其他模组通过 {@code ShouldDropOnDeath} 加入的物品）
 * 会从暂存中取出，并按 Wathe 自身死亡循环掉落当前背包的方式掉落（{@code victim.dropItem(stack, true, false)}）；
 * 各暂存的其余物品仍在默认阶段消失（{@code BlackRavenDisguiseService.endForDeath}）。使用独立的 {@code KillPlayer.AFTER}
 * 阶段，排在 {@link Event#DEFAULT_PHASE} 之前：晚于死亡仍可被取消的 BEFORE，早于 SparkTraits 在 AFTER 默认阶段消费其
 * 背水一战集合（复活时不能多出一把左轮）。只按原始职业判断。仅服务端。
 */
public final class BlackRavenStashedDeathDrops {
    /** The early {@code KillPlayer.AFTER} phase. / {@code KillPlayer.AFTER} 的提前阶段。 */
    static final Identifier PHASE = SparkWitch.id("black_raven_stashed_death_drops");
    private static boolean registered;

    private BlackRavenStashedDeathDrops() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        KillPlayer.AFTER.register(PHASE, (victim, killer, deathReason) -> dropStashed(victim));
    }

    static void dropStashed(ServerPlayerEntity victim) {
        if (victim == null
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(victim.getServerWorld()).getRole(victim))) {
            return;
        }
        BlackRavenDisguiseState state = BlackRavenDisguiseComponent.KEY.get(victim).state();
        List<ItemStack> drops = new ArrayList<>();
        for (BlackRavenIdentityStash stash : state.stashes().values()) {
            drops.addAll(takeMatching(stash, stack -> GameFunctions.shouldDropOnDeath(stack, victim)));
        }
        for (ItemStack stack : drops) {
            victim.dropItem(stack, true, false);
        }
    }

    /**
     * Removes and returns every stashed stack (slots, then overflow) that the predicate drops; the stash keeps the rest.
     * 移除并返回暂存中（先槽位、后溢出）所有满足条件的物品；暂存保留其余物品。
     */
    static List<ItemStack> takeMatching(BlackRavenIdentityStash stash, Predicate<ItemStack> drops) {
        List<ItemStack> taken = new ArrayList<>();
        for (Iterator<BlackRavenIdentityStash.SlotStack> slots = stash.slots().iterator(); slots.hasNext(); ) {
            ItemStack stack = slots.next().stack();
            if (drops.test(stack)) {
                taken.add(stack);
                slots.remove();
            }
        }
        for (Iterator<ItemStack> overflow = stash.overflow().iterator(); overflow.hasNext(); ) {
            ItemStack stack = overflow.next();
            if (drops.test(stack)) {
                taken.add(stack);
                overflow.remove();
            }
        }
        return taken;
    }
}
