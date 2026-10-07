package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** 一条带时间戳的语义动作。轨迹帧负责位置，动作负责真正的交互。 */
public record MagicianRecordedAction(
        int tick,
        Type type,
        @Nullable Hand hand,
        int intValue,
        double x,
        double y,
        double z,
        @Nullable BlockPos blockPos,
        @Nullable Direction blockSide,
        boolean insideBlock
) {
    public enum Type { ATTACK, USE_MAIN_HAND, USE_OFF_HAND, RELEASE_USE_ITEM, SWING_MAIN_HAND, SWING_OFF_HAND, SELECT_SLOT, GUN_SHOOT, KNIFE_STAB }
    public static MagicianRecordedAction attack(int tick) { return simple(tick, Type.ATTACK, null, 0); }
    public static MagicianRecordedAction use(int tick, Hand hand) { return simple(tick, hand == Hand.MAIN_HAND ? Type.USE_MAIN_HAND : Type.USE_OFF_HAND, hand, 0); }
    public static MagicianRecordedAction useBlock(int tick, Hand hand, BlockHitResult hit) {
        Vec3d p = hit.getPos();
        return new MagicianRecordedAction(tick, hand == Hand.MAIN_HAND ? Type.USE_MAIN_HAND : Type.USE_OFF_HAND,
                hand, 0, p.x, p.y, p.z, hit.getBlockPos(), hit.getSide(), hit.isInsideBlock());
    }
    public static MagicianRecordedAction release(int tick) { return simple(tick, Type.RELEASE_USE_ITEM, null, 0); }
    public static MagicianRecordedAction swing(int tick, Hand hand) { return simple(tick, hand == Hand.MAIN_HAND ? Type.SWING_MAIN_HAND : Type.SWING_OFF_HAND, hand, 0); }
    public static MagicianRecordedAction slot(int tick, int slot) { return simple(tick, Type.SELECT_SLOT, null, slot); }
    public static MagicianRecordedAction gun(int tick) { return simple(tick, Type.GUN_SHOOT, null, 0); }
    public static MagicianRecordedAction knife(int tick) { return simple(tick, Type.KNIFE_STAB, null, 0); }
    public @Nullable BlockHitResult blockHit() {
        if (blockPos == null || blockSide == null) return null;
        return new BlockHitResult(new Vec3d(x, y, z), blockSide, blockPos, insideBlock);
    }
    private static MagicianRecordedAction simple(int tick, Type type, @Nullable Hand hand, int value) {
        return new MagicianRecordedAction(tick, type, hand, value, 0, 0, 0, null, null, false);
    }
}
