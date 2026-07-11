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

/**
 * Server-side entry point for Pig God's active skill.
 * 皮革噶的主动技能的服务端入口，只处理该职业自己的金币消耗与状态启动。
 */
public final class PigGodSkillService {
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
        serverWorld.playSound(
                null,
                context.player().getX(),
                context.player().getY(),
                context.player().getZ(),
                SparkWitchSounds.PIG_CHASE,
                SoundCategory.PLAYERS,
                PigGodRules.SOUND_VOLUME,
                PigGodRules.SOUND_PITCH
        );
    }

    /**
     * Stops the one-shot Pig Chase sound for nearby clients when the owner's chase ends early.
     * 当追杀者提前死亡或状态被清理时，通知附近客户端立刻停止这段一次性音效。
     */
    public static void stopChaseSound(ServerWorld world, double x, double y, double z) {
        StopSoundS2CPacket packet = new StopSoundS2CPacket(SparkWitchSounds.PIG_CHASE_ID, SoundCategory.PLAYERS);
        for (ServerPlayerEntity listener : world.getPlayers()) {
            if (PigGodRules.shouldStopSoundForListener(x, y, z, listener.getX(), listener.getY(), listener.getZ())) {
                listener.networkHandler.sendPacket(packet);
            }
        }
    }
}
