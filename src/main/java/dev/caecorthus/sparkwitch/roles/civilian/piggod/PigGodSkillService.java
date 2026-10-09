package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side entry point for Pig God's active skill.
 * 皮革噶的主动技能的服务端入口，只处理该职业自己的金币消耗与状态启动。
 */
public final class PigGodSkillService {
    /**
     * Players sent each caster's Pig Chase sound. The clip is stereo, so clients play it at full volume
     * wherever they move; the stop must reach these exact players, not whoever is near the cast spot.
     * 每个施放者的皮革追杀音效发给了哪些玩家。该音频为立体声，客户端不随距离衰减，
     * 因此停止包必须发给这些玩家本身，而不是此刻在施放点附近的玩家。
     */
    private static final Map<UUID, Set<UUID>> CHASE_SOUND_LISTENERS = new HashMap<>();

    private PigGodSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!PigGodRules.isPigGod(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        if (component.hasActivePigChaseState()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.pig_chase.active");
        }

        PlayerShopComponent shop = PlayerShopComponent.KEY.get(context.player());
        if (shop.getBalance() < PigGodRules.COIN_COST) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.pig_chase.not_enough_money");
        }
        if (!ensurePsychoHotbarSlot(context.player())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.pig_chase.no_inventory_space");
        }

        shop.setBalance(shop.getBalance() - PigGodRules.COIN_COST);
        playChaseSound(context);
        PigGodChaseRuntime.begin(
                context.player(),
                component,
                PigGodRules.FREEZE_TICKS,
                PigGodRules.CHASE_TICKS
        );
        return WitchSkillUseResult.successAfterActiveWindow(
                PigGodRules.COOLDOWN_TICKS,
                "message.sparkwitch.skill.pig_chase.activated"
        );
    }

    /**
     * Prepares a hotbar slot before Wathe's psycho mode inserts its bat.
     * 在调用 Wathe 疯魔前准备快捷栏槽位，避免背包有空位但快捷栏满时启动失败。
     */
    private static boolean ensurePsychoHotbarSlot(ServerPlayerEntity player) {
        int slot = PigGodRules.psychoBatHotbarSlot(hotbarOccupiedSlots(player), player.getInventory().selectedSlot);
        ItemStack displaced = player.getInventory().getStack(slot);
        if (displaced.isEmpty()) {
            return true;
        }

        player.getInventory().setStack(slot, ItemStack.EMPTY);
        player.getInventory().selectedSlot = slot;
        player.dropItem(displaced.copy(), true, false);
        return true;
    }

    private static boolean[] hotbarOccupiedSlots(ServerPlayerEntity player) {
        boolean[] occupiedSlots = new boolean[PigGodRules.HOTBAR_SIZE];
        for (int slot = 0; slot < occupiedSlots.length; slot++) {
            occupiedSlots[slot] = !player.getInventory().getStack(slot).isEmpty();
        }
        return occupiedSlots;
    }

    private static void playChaseSound(WitchSkillUseContext context) {
        ServerWorld serverWorld = context.world();
        ServerPlayerEntity caster = context.player();
        // Same recipients as ServerWorld#playSound -> PlayerManager#sendToAround below.
        // 与下方 ServerWorld#playSound -> PlayerManager#sendToAround 的接收者完全一致。
        double range = SparkWitchSounds.PIG_CHASE.getDistanceToTravel(PigGodRules.SOUND_VOLUME);
        Set<UUID> listeners = new HashSet<>();
        for (ServerPlayerEntity listener : serverWorld.getPlayers()) {
            if (listener.squaredDistanceTo(caster.getX(), caster.getY(), caster.getZ()) < range * range) {
                listeners.add(listener.getUuid());
            }
        }
        CHASE_SOUND_LISTENERS.put(caster.getUuid(), listeners);
        serverWorld.playSound(
                null,
                caster.getX(),
                caster.getY(),
                caster.getZ(),
                SparkWitchSounds.PIG_CHASE,
                SoundCategory.PLAYERS,
                PigGodRules.SOUND_VOLUME,
                PigGodRules.SOUND_PITCH
        );
    }

    /**
     * Stops the caster's Pig Chase sound for every player it was sent to when the chase ends or is cleared early.
     * 追杀结束、追杀者死亡或状态被清理时，通知当初收到该音效的所有玩家立刻停止播放。
     */
    public static void stopChaseSound(ServerPlayerEntity caster) {
        Set<UUID> listeners = CHASE_SOUND_LISTENERS.remove(caster.getUuid());
        if (listeners == null || caster.getServer() == null) {
            return;
        }
        StopSoundS2CPacket packet = new StopSoundS2CPacket(SparkWitchSounds.PIG_CHASE_ID, SoundCategory.PLAYERS);
        for (UUID listenerId : listeners) {
            ServerPlayerEntity listener = caster.getServer().getPlayerManager().getPlayer(listenerId);
            if (listener != null) {
                listener.networkHandler.sendPacket(packet);
            }
        }
    }
}
