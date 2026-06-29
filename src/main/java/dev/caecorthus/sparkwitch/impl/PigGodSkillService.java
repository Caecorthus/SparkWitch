package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
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
        if (context.player().getInventory().getEmptySlot() < 0) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.pig_chase.no_inventory_space");
        }
        if (shop.getBalance() < PigGodRules.COIN_COST) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.pig_chase.not_enough_money");
        }

        shop.setBalance(shop.getBalance() - PigGodRules.COIN_COST);
        playChaseSound(context);
        component.beginPigChaseFreeze(
                PigGodRules.FREEZE_TICKS,
                PigGodRules.CHASE_TICKS,
                context.player().getX(),
                context.player().getY(),
                context.player().getZ()
        );
        return WitchSkillUseResult.successAfterActiveWindow(
                PigGodRules.COOLDOWN_TICKS,
                "message.sparkwitch.skill.pig_chase.activated"
        );
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
