package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 招魂 (the Medium's binding skill), server-authoritative. It has no key and no server menu any more: the client lists
 * the candidates in the inventory screen from the synced round roster ({@link MediumRules#mayHaunt}), and the confirm
 * ({@link SubmitMediumHauntTargetC2SPacket}) is the paid act, fully re-validated here. A bound target is never told.
 * Its vote weighs {@link MediumRules#BIND_VOTE_WEIGHT} in both window kinds, and when the round's death count is over
 * half its own vote is revealed to the Medium. Bindings accumulate for the round; repeats are refused before the
 * charge.
 * 招魂（通灵师的绑定技能），服务端权威。它不再有按键，也不再有服务端菜单：客户端在背包界面里根据同步的本局名单列出候选
 * （{@link MediumRules#mayHaunt}），确认（{@link SubmitMediumHauntTargetC2SPacket}）才是付费动作，并在此完整重新校验。
 * 被绑定者不会收到任何提示。其投票在两种窗口中都记 {@link MediumRules#BIND_VOTE_WEIGHT} 票，当局死者过半时其个人投票会
 * 额外给通灵师看。绑定在本局内累积；重复绑定在扣费前即被拒绝。
 */
public final class MediumSecondSkillService {
    private static boolean registered;

    private MediumSecondSkillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(MediumRules.HAUNT_SKILL_ID, MediumSecondSkillService::formatHaunt);
    }

    /**
     * The confirmed pick: re-validated (not the caster, round member, online in this world, not yet bound, gates and
     * price) before the charge; the picked player is never told. The client builds its own candidate list, so nothing
     * it sends is trusted.
     * 确认选择：扣费前重新校验（不是施放者本人、本局成员、在同一世界在线、尚未绑定、门槛与价格）；被选玩家不会收到任何提示。
     * 候选列表由客户端自行生成，因此它发来的内容一概不可信。
     */
    public static void submit(ServerPlayerEntity caster, @Nullable UUID targetUuid) {
        if (caster == null || targetUuid == null || !MediumSkillService.mayUse(caster)) {
            return;
        }
        // Never the caster: a self-binding could only matter if the Medium died mid-window and then voted in their
        // own window at triple weight. / 不含施放者本人：自绑只在通灵师于窗口中途死亡后以三倍票权给自己的窗口投票时
        // 才有意义。
        if (!MediumRules.mayHaunt(caster.getUuid(), targetUuid, GameWorldComponent.KEY.get(caster.getWorld()))) {
            return;
        }
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(caster);
        if (component.isBound(targetUuid)) {
            return;
        }
        ServerPlayerEntity target = caster.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || target.getServerWorld() != caster.getServerWorld()) {
            return;
        }
        int price = MediumRules.hauntPrice();
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(caster);
        if (shop.getBalance() < price || MediumSkillService.deniedByFear(caster)) {
            return;
        }
        if (price > 0) {
            shop.addToBalance(-price);
        }
        component.addBoundTarget(targetUuid);
        GameRecordManager.recordSkillUse(caster, MediumRules.HAUNT_SKILL_ID, target, null);
    }

    private static @Nullable Text formatHaunt(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                "replay.skill.sparkwitch.medium_haunt_skill",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
