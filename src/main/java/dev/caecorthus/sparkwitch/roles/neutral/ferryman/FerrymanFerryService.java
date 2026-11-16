package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.compat.SparkTraitsBodyDragBridge;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianDecoyBodies;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroFakeBodies;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFakeBodies;
import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Ferryman's second skill on the secondary key: ferry a fresh corpse for 50 gold and mark it with 怜悯. Only a real
 * corpse can be ferried: its owner must be dead in Wathe, and no fake body (the Charon's feign corpses, Fiend feign
 * corpses, Magician decoys, SparkTraits fake-death bodies) ever qualifies. The body must have died within 60 s, be
 * targetable, within reach and in line of sight, and each one pays once; the Charon must be playing, alive, visible
 * (not a spectator) and not swallowed. Every check runs on the server against the synced body entity, never on a
 * client claim. Server only.
 * 摆渡人第二技能（第二技能键）：摆渡一具新鲜遗体，获得 50 金币并挂上「怜悯」。只有真正的尸体才能被摆渡：其主人必须在
 * Wathe 中已死亡，任何假尸体（摆渡人引发的假死尸体、魔人假死尸体、魔术师诱饵、SparkTraits 假死尸体）都不算。遗体须死亡
 * 60 秒内、可被选中、够得着且在视线内，每具只付一次；摆渡人须在局内存活、可见（非旁观）且未被吞下。所有判定都在服务端
 * 针对已同步的遗体实体执行，从不相信客户端声明。仅服务端。
 */
public final class FerrymanFerryService {
    private static final String DONE_KEY = "message.sparkwitch.ferryman.ferry.done";
    private static final String ALREADY_KEY = "message.sparkwitch.ferryman.ferry.already";
    private static final String STALE_KEY = "message.sparkwitch.ferryman.ferry.stale";
    private static final String FAR_KEY = "message.sparkwitch.ferryman.ferry.too_far";
    private static final String BLOCKED_KEY = "message.sparkwitch.ferryman.ferry.unavailable";
    private static final String COOLDOWN_KEY = "message.sparkwitch.ferryman.ferry.cooldown";

    private FerrymanFerryService() {
    }

    /** Outcome of one ferry request after the cooldown check. / 冷却判定之后单次摆渡请求的结果。 */
    enum Verdict {
        BLOCKED,
        ALREADY,
        STALE,
        TOO_FAR,
        OK
    }

    /**
     * Pure order of the body checks: a body that is not a real, targetable corpse of a dead player is "nothing to
     * ferry" before anything else, so a fake body never reveals itself through a different refusal.
     * 遗体判定的纯顺序：不是可选中的、已死亡玩家的真实尸体时，先一律回答“没有可摆渡的”，因此假尸体绝不会通过不同的拒绝
     * 提示暴露自己。
     */
    static Verdict verdict(boolean targetable, boolean fakeBody, boolean ownerDead, boolean alreadyFerried,
                           boolean fresh, boolean inRange, boolean lineOfSight) {
        if (!targetable || fakeBody || !ownerDead) {
            return Verdict.BLOCKED;
        }
        if (alreadyFerried) {
            return Verdict.ALREADY;
        }
        if (!fresh) {
            return Verdict.STALE;
        }
        return inRange && lineOfSight ? Verdict.OK : Verdict.TOO_FAR;
    }

    /** The Charon may ferry only while playing, alive, visible and unswallowed. / 摆渡人只有在局内存活、可见且未被吞下时才能摆渡。 */
    static boolean canAct(boolean activeFerryman, boolean spectator, boolean swallowed) {
        return activeFerryman && !spectator && !swallowed;
    }

    /** Server entry for the secondary-key payload. {@code bodyEntityId} comes from the client's crosshair. */
    /** 第二技能键数据包的服务端入口；{@code bodyEntityId} 来自客户端准星。 */
    public static void ferry(ServerPlayerEntity ferryman, int bodyEntityId) {
        if (!canAct(FerrymanParticipation.isActiveFerryman(ferryman), ferryman.isSpectator(),
                SwallowedPlayerComponent.isPlayerSwallowed(ferryman))) {
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(ferryman);
        // The secondary key cools down for 5 s after every successful ferry, before anything else is decided.
        // 第二技能键在每次成功摆渡后冷却 5 秒；判定放在最前面。
        int cooldown = component.ferryCooldownTicks();
        if (cooldown > 0) {
            ferryman.sendMessage(Text.translatable(COOLDOWN_KEY, (cooldown + 19) / 20), true);
            return;
        }
        Entity entity = ferryman.getServerWorld().getEntityById(bodyEntityId);
        if (!(entity instanceof PlayerBodyEntity body) || body.isRemoved()) {
            ferryman.sendMessage(Text.translatable(BLOCKED_KEY), true);
            return;
        }
        UUID bodyOwner = body.getPlayerUuid();
        if (bodyOwner == null) {
            ferryman.sendMessage(Text.translatable(BLOCKED_KEY), true);
            return;
        }
        long now = ferryman.getServerWorld().getTime();
        Verdict verdict = verdict(
                // Wathe's crosshair seam (Scavenger-hidden bodies and other listeners), as the Kidnapper's server-side
                // aim check (KidnapperTargeting.findAimedBody) asks it.
                // Wathe 的准星接缝（拾荒者隐藏的尸体等监听器），与绑匪服务端瞄准判定（KidnapperTargeting.findAimedBody）一致。
                CanTargetBody.EVENT.invoker().canTarget(ferryman, body),
                isFakeBody(body),
                GameWorldComponent.KEY.get(ferryman.getServerWorld()).isPlayerDead(bodyOwner),
                component.hasFerried(bodyOwner),
                FerrymanRules.isFreshBody(now, body.getDeathGameTime()),
                FerrymanRules.inFerryRange(ferryman.squaredDistanceTo(body)),
                // Line of sight like KidnapperTargeting.findAimedBody: no ferrying through walls or closed doors.
                // 与 KidnapperTargeting.findAimedBody 相同的视线判定：不能隔墙或隔着关闭的门摆渡。
                ferryman.canSee(body));
        switch (verdict) {
            case BLOCKED -> ferryman.sendMessage(Text.translatable(BLOCKED_KEY), true);
            case ALREADY -> ferryman.sendMessage(Text.translatable(ALREADY_KEY), true);
            case STALE -> ferryman.sendMessage(Text.translatable(STALE_KEY), true);
            case TOO_FAR -> ferryman.sendMessage(Text.translatable(FAR_KEY), true);
            case OK -> pay(ferryman, component, bodyOwner);
        }
    }

    private static void pay(ServerPlayerEntity ferryman, FerrymanPlayerComponent component, UUID bodyOwner) {
        component.addFerried(bodyOwner);
        component.setFerryCooldown(FerrymanRules.FERRY_COOLDOWN_TICKS);
        PlayerShopComponent.KEY.get(ferryman).addToBalance(FerrymanRules.FERRY_GOLD);
        ferryman.getServerWorld().playSound(null, ferryman.getX(), ferryman.getY(), ferryman.getZ(),
                SoundEvents.BLOCK_SOUL_SAND_STEP, SoundCategory.PLAYERS, 0.7F, 0.6F);
        ferryman.sendMessage(Text.translatable(DONE_KEY, FerrymanRules.FERRY_GOLD), true);
    }

    /**
     * Every fake-body registry the repo knows, server only: the Charon's own feign corpses, Fiend feign corpses,
     * Magician decoys, and SparkTraits fake-death bodies through its public facade (fail closed: a present but broken
     * facade counts as fake, an absent SparkTraits as real).
     * 仓库已知的所有假尸体登记（仅服务端）：摆渡人引发的假死尸体、魔人假死尸体、魔术师诱饵，以及经公共门面查询的
     * SparkTraits 假死尸体（失败关闭：已安装但门面异常视为假尸体，未安装视为真尸体）。
     */
    static boolean isFakeBody(@Nullable PlayerBodyEntity body) {
        return body == null
                || FerrymanFeignService.isFakeBody(body)
                || FiendFakeBodies.isFake(body)
                // The fake corpse of a 二阶堂希罗 victim (2026-11-11). / 二阶堂希罗受害者的假尸体（2026-11-11）。
                || HiroFakeBodies.isFake(body)
                || MagicianDecoyBodies.isDecoy(body)
                || !SparkTraitsBodyDragBridge.canDragBody(body);
    }
}
