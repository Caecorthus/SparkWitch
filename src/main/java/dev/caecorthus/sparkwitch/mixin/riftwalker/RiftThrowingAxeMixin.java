package dev.caecorthus.sparkwitch.mixin.riftwalker;

import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved slot (G0) for P4: the NoellesRoles throwing axe kills players in its own loop before {@code super.tick} and
 * its entity lookup ignores non-players, so the vanilla deflection seam misses it; P4 adds an inject/wrap here (never a
 * redirect), stacked next to {@code SeekerThrowingAxeMixin}. Empty, and therefore inert, until P4 lands.
 * 为 P4 预留的 mixin（G0）：NoellesRoles 飞斧在 {@code super.tick} 之前用自己的循环击杀玩家，且实体查找忽略非玩家，
 * 原版偏转接缝捕获不到它；P4 在此添加 inject/wrap（不得使用 redirect），与 {@code SeekerThrowingAxeMixin} 并列。
 * P4 落地前为空，因此无效果。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class RiftThrowingAxeMixin {
}
