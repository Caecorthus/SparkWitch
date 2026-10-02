package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseSender;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.TypedActionResult;

/**
 * Server side of the White Cane: the tap (validated here, no custom packet), its public sound (D6), the CANE pulse to
 * the Blind (15-block environment, players within 5 blocks, invisible ones included, D4), the 10-tick re-scan that adds
 * players entering the radius while the 5 s window lasts, and the raise-only hotbar cooldown. Server thread only.
 * 盲杖的服务端部分：敲击（在此校验，无自定义数据包）、公开的敲击声（D6）、发给盲人的 CANE 脉冲（15 格环境、5 格内玩家，
 * 含隐身者，D4）、在 5 秒窗口内每 10 刻补充新进入半径的玩家，以及只增不减的快捷栏冷却。仅服务端线程。
 */
public final class BlindCaneService {
    private static final float TAP_VOLUME = 1.0F;
    private static final float TAP_PITCH = 1.0F;
    /** Entity ids already revealed in each Blind's current cane window. / 每名盲人当前盲杖窗口内已显示的实体 id。 */
    private static final Map<UUID, IntOpenHashSet> REVEALED = new HashMap<>();

    private BlindCaneService() {
    }

    /**
     * A cane tap from {@code WhiteCaneItem#use} on the server. A refusal costs nothing; vanilla has already refused the
     * use while the item cooldown runs.
     * 来自服务端 {@code WhiteCaneItem#use} 的盲杖敲击。拒绝时不消耗任何东西；物品冷却期间原版已拒绝使用。
     */
    public static TypedActionResult<ItemStack> use(ServerPlayerEntity blind, ItemStack stack) {
        ServerWorld world = blind.getServerWorld();
        long now = world.getTime();
        BlindComponent state = BlindComponent.KEY.get(blind);
        boolean roundActive = GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!BlindKitRules.canUseCane(BlindParticipants.isActiveBlind(blind), roundActive,
                BlindLoadoutService.isGranted(blind), ControlExpertStun.isStunned(blind), state.caneReady(now))) {
            return TypedActionResult.fail(stack);
        }
        BlindKitRules.Window window = BlindKitRules.caneUse(now);
        state.setCane(window.activeUntilTick(), window.readyTick());
        writeItemCooldown(blind, BlindKitRules.CANE_USE_ITEM_COOLDOWN_TICKS);
        playTap(blind, world);
        int[] players = playersInReach(blind);
        IntOpenHashSet revealed = new IntOpenHashSet(players);
        REVEALED.put(blind.getUuid(), revealed);
        send(blind, BlindRules.CANE_ENVIRONMENT_RADIUS, BlindRules.CANE_ACTIVE_TICKS, players);
        // CONSUME, never SUCCESS: no arm swing reveals the hidden cane. / 返回 CONSUME 而非 SUCCESS：不挥手暴露隐藏的盲杖。
        return TypedActionResult.consume(stack);
    }

    /**
     * Per-tick upkeep for an active Blind: while the cane window lasts, every {@link BlindKitRules#CANE_RESCAN_TICKS}
     * the players who entered the 5-block radius are sent as a zero-radius CANE pulse lasting the rest of the window
     * (no new environment reveal); the window's memory is dropped when it ends.
     * 激活盲人的每 tick 维护：盲杖窗口内每隔 {@link BlindKitRules#CANE_RESCAN_TICKS} 刻，把新进入 5 格半径的玩家以零半径、
     * 持续到窗口结束的 CANE 脉冲发送（不新增环境照亮）；窗口结束时丢弃其记录。
     */
    public static void tickHolder(ServerPlayerEntity blind) {
        long now = blind.getServerWorld().getTime();
        long activeUntil = BlindComponent.KEY.get(blind).caneActiveUntilTick();
        if (now >= activeUntil) {
            REVEALED.remove(blind.getUuid());
            return;
        }
        if (!BlindKitRules.caneRescanDue(now, activeUntil)) {
            return;
        }
        IntOpenHashSet revealed = REVEALED.computeIfAbsent(blind.getUuid(), ignored -> new IntOpenHashSet());
        int[] entrants = BlindKitRules.entrants(playersInReach(blind), revealed::contains,
                BlindPulseS2CPayload.MAX_PLAYER_IDS);
        if (entrants.length == 0) {
            return;
        }
        for (int id : entrants) {
            revealed.add(id);
        }
        send(blind, 0.0F, (int) (activeUntil - now), entrants);
    }

    /** Forgets one Blind's cane window. / 忘记某名盲人的盲杖窗口。 */
    static void forget(UUID player) {
        REVEALED.remove(player);
    }

    static void forgetAll() {
        REVEALED.clear();
    }

    /**
     * Hotbar cooldown of the cane: raise-only (a forced cooldown such as the Fiend aura is never shortened), written
     * exactly through SparkTraits so Fast Hands does not shorten it, with a vanilla fallback; never both.
     * 盲杖的快捷栏冷却：只增不减（魔人光环等强制冷却永不被缩短），经 SparkTraits 精确写入使“快手”无法缩短，
     * 并在其缺失时回退原版写入；二者不会同时执行。
     */
    static void writeItemCooldown(ServerPlayerEntity player, int ticks) {
        Item cane = SparkWitchItems.whiteCane();
        BlindKitRules.writeRaisedCooldown(remainingItemCooldown(player, cane), ticks,
                merged -> SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, cane, merged),
                merged -> player.getItemCooldownManager().set(cane, merged));
    }

    /**
     * Public tap for everyone else in vanilla range (the perception pipeline relays it to other Blinds, D6), plus a
     * private copy for the tapping Blind; private sounds never reach the perception hook, so no extra pulse follows.
     * 公开敲击声供原版范围内的其他人听到（感知管线会转给其他盲人，D6），另给敲击的盲人一份私有副本；
     * 私有声音不会进入感知钩子，因此不会多出脉冲。
     */
    private static void playTap(ServerPlayerEntity blind, ServerWorld world) {
        world.playSound(blind, blind.getX(), blind.getY(), blind.getZ(), BlindSounds.caneTap(), SoundCategory.PLAYERS,
                TAP_VOLUME, TAP_PITCH);
        blind.playSoundToPlayer(BlindSounds.caneTap(), SoundCategory.PLAYERS, TAP_VOLUME, TAP_PITCH);
    }

    private static int[] playersInReach(ServerPlayerEntity blind) {
        IntArrayList ids = new IntArrayList();
        for (ServerPlayerEntity other : blind.getServerWorld().getPlayers()) {
            if (ids.size() >= BlindPulseS2CPayload.MAX_PLAYER_IDS) {
                break;
            }
            if (BlindKitRules.isCaneTarget(other == blind, GameFunctions.isPlayerPlayingAndAlive(other),
                    other.isSpectator(), WraithStateService.isActive(other),
                    NoellesTaotieSeekerBridge.isSwallowed(other), other.squaredDistanceTo(blind))) {
                ids.add(other.getId());
            }
        }
        return ids.toIntArray();
    }

    private static void send(ServerPlayerEntity blind, float radius, int durationTicks, int[] players) {
        BlindPulseSender.send(blind, new BlindPulseS2CPayload(
                (float) blind.getX(), (float) blind.getBodyY(0.5D), (float) blind.getZ(), radius,
                (short) Math.min(Short.MAX_VALUE, Math.max(0, durationTicks)), BlindPulseS2CPayload.CANE,
                BlindPulseS2CPayload.NO_EMITTER, players));
    }

    private static int remainingItemCooldown(ServerPlayerEntity player, Item item) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)
                || !(accessor.sparkwitch$getEntries().get(item) instanceof ItemCooldownEntryAccessor entry)) {
            return 0;
        }
        return Math.max(0, entry.sparkwitch$getEndTick() - accessor.sparkwitch$getTick());
    }
}
