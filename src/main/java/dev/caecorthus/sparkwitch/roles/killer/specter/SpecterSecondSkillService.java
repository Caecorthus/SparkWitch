package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Specter skill two, server-authoritative: opens the 30 s Otherworld window, grants invisibility, and gives Wathe's
 * built-in psycho armour as the shield (3 layers per alive real-mood player below 25 sanity). Wathe's own kill path
 * spends a layer per blocked lethal hit and lets shield-piercing reasons (the Savior pistol/lightsaber) through.
 * The armour is cleared when the window ends. Speed II is a movement-speed attribute modifier owned by the component.
 * 幽魂技能二，服务端权威：开启 30 秒里世界窗口、给予隐身，并用 Wathe 自带的疯魔护甲作为护盾
 * （每个存活的真实理智低于 25 的玩家 3 层）。Wathe 的击杀路径每挡一次致命攻击消耗 1 层，并放行穿盾死因
 * （救世主手枪/光剑）。窗口结束时清空护甲。速度 II 由组件用移速属性修饰符实现。
 */
public final class SpecterSecondSkillService {
    private SpecterSecondSkillService() {
    }

    public static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        if (!SpecterRules.isSpecter(game.getRole(caster))
                || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || GameFunctions.isPlayerSpectatingOrCreative(caster)) {
            return;
        }
        SpecterSecondSkillComponent component = SpecterSecondSkillComponent.KEY.get(caster);
        if (!component.activate()) {
            return;
        }
        // The world window is independent of the caster: it survives the Specter's death and only the timer/round end stops it.
        // 世界窗口独立于施法者：幽魂死亡不结束，只有计时或回合结束才关闭。
        SpecterOtherworldComponent.KEY.get(caster.getServerWorld())
                .start(SpecterRules.SECOND_SKILL_DURATION_TICKS);
        component.grantPsychoShield(countLowSanityPlayers(caster.getServerWorld())
                * SpecterRules.SECOND_SKILL_SHIELD_PER_LOW_SANITY);
        caster.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                SpecterRules.SECOND_SKILL_DURATION_TICKS,
                0,
                true,
                false,
                true
        ));
        // Low-sanity swords appear and non-killers are slowed as soon as the window opens.
        // 窗口一开，低理智玩家的桃木剑立刻到位，非杀手立刻缓慢。
        SpecterPeachwoodService.refresh(caster.getServerWorld());
        SpecterOtherworldService.refresh(caster.getServerWorld());
        GameRecordManager.recordSkillUse(caster, SpecterRules.OTHERWORLD_SKILL_ID, null, null);
    }

    /** Alive players with a real mood bar below 25 sanity; fake-mood roles never count. / 真实理智条低于 25 的存活玩家；虚假理智不计。 */
    private static int countLowSanityPlayers(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        int count = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()
                    || GameFunctions.isPlayerSpectatingOrCreative(player)
                    || !GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            Role role = game.getRole(player);
            if (role == null || role.getMoodType() != Role.MoodType.REAL) {
                continue;
            }
            if (PlayerMoodComponent.KEY.get(player).getMood() * 100.0F < SpecterRules.LOW_SANITY_THRESHOLD) {
                count++;
            }
        }
        return count;
    }
}
