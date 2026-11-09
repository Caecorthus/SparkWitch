package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Specter's Otherworld knife path, rewritten as one self-contained authority.
 * <p>
 * Wathe's knife receiver is wrapped by knife-only guards from SparkTraits (Close Quarters / forced melee / Last
 * Escape), SparkStrength (Veteran / Silencer) and the Knight duel. The gun has no such receiver, so a gun shot can kill
 * while the knife packet is silently dropped. Priority 900 runs before every default-1000 guard (lower priority numbers
 * execute first in Mixin), and this handler settles the stab itself.
 * <p>
 * Behaviour: Specter + Otherworld active + Wathe knife in main/off hand + living player target within the normal 3-block
 * knife reach. The normal Otherworld targeting rule decides whether the hit lands; a valid hit records the knife use,
 * calls the standard KNIFE kill, plays the stab sound and swings, then cancels the vanilla receiver (and therefore every
 * knife-only guard). Anything else is left untouched or cancelled without side effects.
 * <p>
 * 幽魂的里世界刀刺路径，重写为单一的自包含权威。
 * Wathe 的刀刺接收器外面挂着 SparkTraits（近身/强制近战/脱险）、SparkStrength（老兵/沉默者）和骑士决斗的刀专属守卫；
 * 枪没有这条接收器，所以会出现枪能杀、刀包被静默丢弃。priority 900 排在所有默认 1000 守卫之前（Mixin 数字越小越先执行），
 * 由本处理器自己结算。
 * 条件：幽魂 + 里世界开启 + 主/副手 Wathe 小刀 + 存活玩家目标在正常 3 格刀刺距离内。是否命中由里世界索敌规则决定；
 * 有效命中记录刀的使用、走标准 KNIFE 击杀、播放刺击音与挥手，然后 cancel 原接收器（连同所有刀专属守卫）。
 * 其他情况保持原样，或直接 cancel 且不产生副作用。
 */
// 900 runs before the default-1000 knife guards; 2000 would run after them and never see the packet.
// 900 先于默认 1000 的刀守卫；2000 会排在它们之后，根本看不到包。
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false, priority = 900)
public abstract class SpecterOtherworldKnifeMixin {
    /** Normal Wathe knife reach. / Wathe 小刀正常刺击距离。 */
    private static final double KNIFE_REACH = 3.0D;

    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$settleOtherworldKnife(
            KnifeStabPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        ServerPlayerEntity attacker = context.player();
        if (attacker == null || !(attacker.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!SpecterRules.isSpecter(game.getRole(attacker))
                || !isOtherworldActive(attacker, world)
                || !GameFunctions.isPlayerPlayingAndAlive(attacker)) {
            return;
        }
        if (!attacker.getMainHandStack().isOf(WatheItems.KNIFE)
                && !attacker.getOffHandStack().isOf(WatheItems.KNIFE)) {
            return;
        }

        Entity hit = world.getEntityById(payload.target());
        if (!(hit instanceof ServerPlayerEntity victim)
                || !GameFunctions.isPlayerPlayingAndAlive(victim)
                || attacker.distanceTo(victim) > KNIFE_REACH) {
            ci.cancel();
            return;
        }
        if (SpecterOtherworldService.blocksKill(victim, attacker)) {
            // The Otherworld rule rejects this target; the vanilla receiver would have the same outcome, but its
            // knife-only guards would see the packet first. Drop it here without side effects.
            // 里世界规则拒绝该目标；原接收器结果相同，但它的刀专属守卫会先看到包。这里无副作用丢弃。
            ci.cancel();
            return;
        }

        GameRecordManager.recordItemUse(attacker, Registries.ITEM.getId(WatheItems.KNIFE), victim, null);
        GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.KNIFE);
        world.playSound(
                null,
                victim.getX(),
                victim.getY(),
                victim.getZ(),
                WatheSounds.ITEM_KNIFE_STAB,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
        attacker.swingHand(Hand.MAIN_HAND);
        ci.cancel();
    }

    /** World window is authoritative; the per-player window is a fallback if the world component is unavailable. / 世界窗口为准，玩家窗口兜底。 */
    private static boolean isOtherworldActive(ServerPlayerEntity attacker, ServerWorld world) {
        return SpecterOtherworldComponent.KEY.get(world).isActive()
                || SpecterSecondSkillComponent.KEY.get(attacker).isActive();
    }
}
