package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * A fired Potion Gunner shell. Server-authoritative: only the server resolves impact and the blast; the client only
 * predicts flight and draws the tinted trail. The shell type is carried by the synced item stack. It is a role-owned
 * entity, never Wathe's grenade, so grenade-only trait and addon hooks never apply; it is never saved.
 * 发射出的药炮手炮弹。由服务端权威决定：只有服务端结算命中与爆炸；客户端只预测飞行并绘制着色烟迹。炮弹种类由同步的
 * 物品堆携带。它是本职业自有实体而非 Wathe 手雷，因此只针对手雷的词条与附属模组钩子永不生效；也从不存盘。
 */
public class PotionShellEntity extends ThrownItemEntity {
    public PotionShellEntity(EntityType<? extends PotionShellEntity> type, World world) {
        super(type, world);
    }

    public PotionShellEntity(World world, LivingEntity owner) {
        super(PotionGunnerEntities.potionShell(), owner, world);
    }

    /**
     * Spawns a shell of {@code type} from the gunner's eye along {@code yaw}/{@code pitch}. Returns false when nothing
     * was spawned, so the caller keeps the loaded shell.
     * 沿 {@code yaw}/{@code pitch} 从药炮手眼部发射一颗 {@code type} 炮弹。未生成时返回 false，调用方保留已装填的炮弹。
     */
    public static boolean launch(ServerPlayerEntity gunner, PotionShellType type, float yaw, float pitch) {
        // WP2 implements. / 由 WP2 实现。
        return false;
    }

    @Override
    protected Item getDefaultItem() {
        return SparkWitchItems.potionShell(PotionShellType.TR);
    }
}
