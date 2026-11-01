package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Ferryman's second skill on the secondary key: ferry a fresh corpse for 50 gold and mark it with 怜悯. Only a body
 * that died within 60 s can be ferried, only once per body, and only from within reach; every check runs on the server
 * against the synced body entity, never on a client claim. Server only.
 * 摆渡人第二技能（第二技能键）：摆渡一具新鲜遗体，获得 50 金币并挂上「怜悯」。只有死亡 60 秒内的遗体、每具只能摆渡一次、
 * 且必须够得着；所有判定都在服务端针对已同步的遗体实体执行，从不相信客户端声明。仅服务端。
 */
public final class FerrymanFerryService {
    private static final String DONE_KEY = "message.sparkwitch.ferryman.ferry.done";
    private static final String ALREADY_KEY = "message.sparkwitch.ferryman.ferry.already";
    private static final String STALE_KEY = "message.sparkwitch.ferryman.ferry.stale";
    private static final String FAR_KEY = "message.sparkwitch.ferryman.ferry.too_far";
    private static final String BLOCKED_KEY = "message.sparkwitch.ferryman.ferry.unavailable";

    private FerrymanFerryService() {
    }

    /** Server entry for the secondary-key payload. {@code bodyEntityId} comes from the client's crosshair. */
    /** 第二技能键数据包的服务端入口；{@code bodyEntityId} 来自客户端准星。 */
    public static void ferry(ServerPlayerEntity ferryman, int bodyEntityId) {
        if (!FerrymanParticipation.isActiveFerryman(ferryman)) {
            return;
        }
        Entity entity = ferryman.getServerWorld().getEntityById(bodyEntityId);
        if (!(entity instanceof PlayerBodyEntity body)) {
            ferryman.sendMessage(Text.translatable(BLOCKED_KEY), true);
            return;
        }
        UUID bodyOwner = ferryBodyOwner(body);
        if (bodyOwner == null) {
            ferryman.sendMessage(Text.translatable(BLOCKED_KEY), true);
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(ferryman);
        if (component.hasFerried(bodyOwner)) {
            ferryman.sendMessage(Text.translatable(ALREADY_KEY), true);
            return;
        }
        long now = ferryman.getServerWorld().getTime();
        if (!FerrymanRules.isFreshBody(now, body.getDeathGameTime())) {
            ferryman.sendMessage(Text.translatable(STALE_KEY), true);
            return;
        }
        if (!FerrymanRules.inFerryRange(ferryman.squaredDistanceTo(body))) {
            ferryman.sendMessage(Text.translatable(FAR_KEY), true);
            return;
        }
        component.addFerried(bodyOwner);
        PlayerShopComponent.KEY.get(ferryman).addToBalance(FerrymanRules.FERRY_GOLD);
        ferryman.getServerWorld().playSound(null, ferryman.getX(), ferryman.getY(), ferryman.getZ(),
                SoundEvents.BLOCK_SOUL_SAND_STEP, SoundCategory.PLAYERS, 0.7F, 0.6F);
        ferryman.sendMessage(Text.translatable(DONE_KEY, FerrymanRules.FERRY_GOLD), true);
    }

    /** The body's owning player id, or null for a body that carries none. / 遗体所属玩家 id；无归属时为 null。 */
    private static @Nullable UUID ferryBodyOwner(PlayerBodyEntity body) {
        return body.getPlayerUuid();
    }
}
