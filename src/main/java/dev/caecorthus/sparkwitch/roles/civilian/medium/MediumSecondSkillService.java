package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 招魂 (the Medium's Role Skill 2), server-authoritative. The press opens a menu of every other online round member,
 * alive or dead; the confirm is the paid act. A bound target is never told. Its vote weighs
 * {@link MediumRules#BIND_VOTE_WEIGHT}, and when the round's death count is over half its own answer is revealed to the
 * Medium. Bindings accumulate for the round; repeats are refused before the charge.
 * 招魂（通灵师职业技能 2），服务端权威。按键打开除自己外所有在线本局成员（无论生死）的菜单；确认才是付费动作。被绑定者不会收到
 * 任何提示。其投票记 {@link MediumRules#BIND_VOTE_WEIGHT} 票，当局死者过半时其个人答案会额外给通灵师看。绑定在本局
 * 内累积；重复绑定在扣费前即被拒绝。
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
     * Server entry of the Role Skill 2 key: opens the target menu with the live price. Nothing is charged here.
     * 职业技能 2 键的服务端入口：以实时价格打开目标菜单。此处不扣任何费用。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !MediumSkillService.mayUse(caster)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        int price = MediumRules.hauntPrice();
        if (PlayerShopComponent.KEY.get(caster).getBalance() < price) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID uuid : game.getAllPlayers()) {
            ServerPlayerEntity online = caster.getServer().getPlayerManager().getPlayer(uuid);
            // Never the caster: a self-binding could only matter if the Medium died mid-window and then answered
            // their own question at triple weight. / 不含施放者本人：自绑只在通灵师于窗口中途死亡后以三倍票权回答
            // 自己的提问时才有意义。
            if (online == null || online == caster || online.getServerWorld() != caster.getServerWorld()) {
                continue;
            }
            ids.add(uuid);
            names.add(online.getGameProfile().getName());
        }
        if (ids.isEmpty() || MediumSkillService.deniedByFear(caster)
                || !ServerPlayNetworking.canSend(caster, OpenMediumHauntSelectS2CPacket.ID)) {
            return;
        }
        ServerPlayNetworking.send(caster, new OpenMediumHauntSelectS2CPacket(ids, names, price));
    }

    /**
     * The confirmed pick: re-validated (not the caster, round member, online in this world, not yet bound, gates and
     * price) before the charge; the picked player is never told.
     * 确认选择：扣费前重新校验（不是施放者本人、本局成员、在同一世界在线、尚未绑定、门槛与价格）；被选玩家不会收到任何提示。
     */
    public static void submit(ServerPlayerEntity caster, @Nullable UUID targetUuid) {
        if (caster == null || targetUuid == null || targetUuid.equals(caster.getUuid())
                || !MediumSkillService.mayUse(caster)) {
            return;
        }
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(caster);
        if (component.isBound(targetUuid)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        if (!game.getAllPlayers().contains(targetUuid)) {
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
