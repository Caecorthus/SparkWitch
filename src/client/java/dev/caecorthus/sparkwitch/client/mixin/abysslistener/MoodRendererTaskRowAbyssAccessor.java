package dev.caecorthus.sparkwitch.client.mixin.abysslistener;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of Wathe's private task row ({@code MoodRenderer$TaskRenderer}): the animated row offset and the
 * row text, so the pseudo 「快离开这里！！！」 line can sit below the last row. Wathe-named fields only, so
 * {@code remap = false}; the {@code text} type is remapped with this class like other {@code remap = false} members.
 * 对 Wathe 私有任务行（{@code MoodRenderer$TaskRenderer}）的只读视图：带动画的行偏移与行文本，使临时任务行
 * 「快离开这里！！！」位于最后一行下方。只使用 Wathe 命名的字段，因此 {@code remap = false}；{@code text} 的类型会像其他
 * {@code remap = false} 成员一样随本类重映射。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer", remap = false)
public interface MoodRendererTaskRowAbyssAccessor {
    @Accessor("offset")
    float sparkwitch$getAbyssRowOffset();

    @Accessor("text")
    Text sparkwitch$getAbyssRowText();
}
