package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The two 15x15 crosshairs a loaded buckshot shows: the empty one while no eligible target is on the spread, the
 * target one once a player (or a live Magician puppet as its copied player) can be hit. Only the buckshot state is
 * taken over; a slug keeps Wathe's own target crosshair, and an empty gun or another item keeps Wathe's 3x3. The
 * 4-tick follow-up cooldown deliberately keeps the custom crosshair (owner decision 2026-11-02) so a held trigger
 * does not flicker back to the old sprite between shots.
 * 已装填霰弹时的两枚 15x15 准星：散射范围上没有合格目标时用空准星，有玩家（或视同其复制玩家的存活魔术师皮套）可被
 * 命中时用目标准星。只接管霰弹状态；独头弹保持 Wathe 自己的目标准星，空枪与其他物品保持 Wathe 的 3x3 准星。4 tick
 * 连发冷却刻意保留自定义准星（所有者 2026-11-02 决定），按住右键连发时不会在两次射击之间闪回旧贴图。
 */
public final class HunterShotgunCrosshair {
    public static final Identifier IDLE = Identifier.of("sparkwitch", "hud/double_barrel_crosshair_idle");
    public static final Identifier TARGET = Identifier.of("sparkwitch", "hud/double_barrel_crosshair_target");
    /** Sprite edge at the auto GUI scale, in GUI pixels. / 自动界面尺寸下的贴图边长（GUI 像素）。 */
    public static final int SIZE = 15;

    /** The shotgun pick's player box growth ({@code TARGET_BOX_EXPANSION}). / 猎枪选取玩家时的箱体扩大量。 */
    private static final double PLAYER_BOX_EXPANSION = 0.1D;

    private HunterShotgunCrosshair() {
    }

    /**
     * Sprite edge for the current GUI scale: the same physical size this sprite would have at the window's automatic
     * GUI scale, so a smaller manual scale does not shrink these two crosshairs only. Every other HUD element keeps
     * its own scale. / 当前界面尺寸下的贴图边长：与该贴图在窗口自动界面尺寸下的物理大小一致，因此手动调小界面尺寸
     * 时不会只让这两枚准星跟着变小；其他 HUD 元素仍按各自的尺寸绘制。
     */
    public static int sizeFor(MinecraftClient client) {
        int autoScale = client.getWindow().calculateScaleFactor(0,
                client.options.getForceUnicodeFont().getValue());
        double currentScale = Math.max(1.0D, client.getWindow().getScaleFactor());
        return Math.max(1, (int) Math.round(SIZE * autoScale / currentScale));
    }

    /**
     * Draw offset that centres a {@code size}-edge rect where Wathe's 3x3 crosshair sits: its matrix was translated
     * by (-1.5,-1.5), so local (1.5,1.5) is the screen centre. / 让 {@code size} 边长的矩形居中于 Wathe 3x3 准星处：
     * 其矩阵已平移 (-1.5,-1.5)，本地坐标 (1.5,1.5) 即屏幕中心。
     */
    public static int drawOffset(int size) {
        return Math.floorDiv(3 - size, 2);
    }

    /**
     * The custom sprite a loaded buckshot should draw right now, or {@code null} when the main hand is not a shotgun
     * whose next shell is a buckshot (empty and slug guns included).
     * 当前应绘制的自定义准星；主手不是下一发为霰弹的猎枪时（含空枪与独头弹）为 {@code null}。
     */
    @Nullable
    public static Identifier spriteFor(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DoubleBarrelShotgunItem)
                || DoubleBarrelShotgunItem.peekShell(stack) != HunterRules.SHELL_BUCKSHOT) {
            return null;
        }
        return hasTarget(player, stack) ? TARGET : IDLE;
    }

    /**
     * The next shot's rays ({@link DoubleBarrelShotgunItem#shotAims}) hold a player in a public, visible state, or a
     * live Magician puppet. The player pick deliberately uses public state only, like the Taser hint: the server's own
     * eligibility (role, round, faction veto) is not repeated here, so the hint never leaks hidden roles and fake or
     * lobby players still light it; the server still decides every hit.
     * 下一发射线（{@link DoubleBarrelShotgunItem#shotAims}）上有处于公开可见状态的玩家，或有存活魔术师皮套。玩家选取刻意
     * 只按公开状态过滤，与电击枪提示相同：这里不重复服务端的资格判定（职业、对局、阵营否决），因此提示不会泄露隐藏职业，
     * 假玩家或大厅玩家同样能点亮；是否命中仍由服务端决定。
     */
    public static boolean hasTarget(ClientPlayerEntity player, ItemStack stack) {
        return playerInSpread(player, stack) || puppetInSight(player, stack);
    }

    /**
     * Any block-clipped shot ray enters another player's box (grown like the shotgun pick). The same public filter as
     * Wathe's own gun crosshair. / 任一按方块截断的射击射线进入其他玩家的箱体（与猎枪选取同样扩大）。过滤条件与 Wathe
     * 自身枪械准星相同，只看公开状态。
     */
    private static boolean playerInSpread(ClientPlayerEntity player, ItemStack stack) {
        for (DoubleBarrelShotgunItem.Aim aim : DoubleBarrelShotgunItem.shotAims(player, stack)) {
            for (AbstractClientPlayerEntity candidate : player.clientWorld.getPlayers()) {
                if (candidate == player || !isVisibleCandidate(candidate)) {
                    continue;
                }
                if (HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(),
                        List.of(candidate.getBoundingBox().expand(PLAYER_BOX_EXPANSION))) >= 0.0D) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Alive, not removed, in survival/adventure, and not hidden. / 存活、未移除、处于生存/冒险且未隐身。 */
    private static boolean isVisibleCandidate(AbstractClientPlayerEntity candidate) {
        return candidate.isAlive() && !candidate.isRemoved()
                && GameFunctions.isPlayerAliveAndSurvival(candidate)
                && !candidate.isInvisible();
    }

    /**
     * Magician seam: a live puppet on the same block-clipped aim ray at the next shell's range (its box grown like the
     * player pick) lights the crosshair as its copied player would; the server decides the shot
     * ({@code MagicianPuppetHits}).
     * 魔术师接缝：下一发弹药射程、按方块截断的瞄准射线上的存活皮套（箱体与玩家选取同样扩大）与其复制的玩家一样点亮准星；
     * 这一枪由服务端判定（{@code MagicianPuppetHits}）。
     */
    private static boolean puppetInSight(ClientPlayerEntity player, ItemStack stack) {
        Vec3d eye = player.getEyePos();
        double range = DoubleBarrelShotgunItem.peekShell(stack) == HunterRules.SHELL_SLUG
                ? HunterRules.SLUG_RANGE : HunterRules.SHOTGUN_RANGE;
        Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(range));
        HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d clipped = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        for (MagicianPlaybackEntity puppet : player.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class,
                new Box(eye, clipped).expand(1.0), candidate -> candidate.isAlive() && !candidate.isRemoved())) {
            Box box = puppet.getBoundingBox().expand(PLAYER_BOX_EXPANSION);
            if (box.contains(eye) || box.raycast(eye, clipped).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
