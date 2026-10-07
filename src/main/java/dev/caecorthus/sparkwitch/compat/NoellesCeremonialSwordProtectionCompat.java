package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModEffects;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.effect.WhiskeyShieldEffect;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.agmas.noellesroles.professor.IronManPlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Sword-only equivalent of the applicable branches in NoellesRoles 1.7.6's BEFORE listener.
 * Its internal early returns cannot merely be changed to allow(): iron man would hide whiskey.
 * 仅复现 NoellesRoles 1.7.6 监听中适用于仪礼剑的分支；仅替换返回值会漏掉铁人之后的威士忌。
 */
public final class NoellesCeremonialSwordProtectionCompat {
    private NoellesCeremonialSwordProtectionCompat() {
    }

    public static void consumeProtections(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                          Identifier reason) {
        // Stasis has no consumable, but retains its activation record. Other early branches require
        // ASSASSINATED/GUN/DEMON_HUNTER/KNIFE and cannot apply to CEREMONIAL_BLADE.
        // 禁锢无消耗品但保留触发记录；其余前置分支限定其他死因，不适用于仪礼剑。
        if (GameWorldComponent.KEY.get(victim.getWorld()).isRole(victim, Noellesroles.JESTER)
                && JesterPlayerComponent.KEY.get(victim).inStasis) {
            recordBlocked(victim, killer, reason, "jester_stasis");
        }
        IronManPlayerComponent ironMan = IronManPlayerComponent.KEY.get(victim);
        if (ironMan.hasBuff()) {
            playShieldSound(victim);
            recordBlocked(victim, killer, reason, "iron_man_buff");
            recordActivation(victim, killer, "iron_man_activated");
            ironMan.removeBuff();
        }
        if (victim.hasStatusEffect(ModEffects.WHISKEY_SHIELD)) {
            WhiskeyShieldEffect.consumeShield(victim);
            playShieldSound(victim);
            recordBlocked(victim, killer, reason, "whiskey_shield");
            recordActivation(victim, killer, "whiskey_shield_activated");
        }
    }

    private static void playShieldSound(ServerPlayerEntity victim) {
        victim.getWorld().playSound(null, victim.getBlockPos(), WatheSounds.ITEM_PSYCHO_ARMOUR,
                SoundCategory.MASTER, 5.0F, 1.0F);
    }

    private static void recordBlocked(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                      Identifier reason, String source) {
        var event = GameRecordManager.event("death_blocked").actor(victim)
                .put("block_reason", source).put("death_reason", reason.toString());
        if (killer != null) {
            event.target(killer);
        }
        event.record();
    }

    private static void recordActivation(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                         String type) {
        var event = GameRecordManager.event(type).actor(victim).put("action", "block_damage");
        if (killer != null) {
            event.target(killer);
        }
        event.record();
    }
}
