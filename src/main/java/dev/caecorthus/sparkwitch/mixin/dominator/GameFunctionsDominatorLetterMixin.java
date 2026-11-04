package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the Dominator letter's two postscript lines the exact gold, green and red colours of the design mock-up.
 * Wathe builds every letter lore line from a translation key, so the plain {@code §} palette cannot express the
 * mock-up's #C6B53A / #008000 / #C00000; this rewrites just those two lines into segmented translatable texts with
 * RGB styles after Wathe has built the rest of the letter. Each line's segments ({@code tooltip3.a}-{@code .e},
 * {@code tooltip4.a}-{@code .e}) concatenate to the plain {@code tooltip3} / {@code tooltip4} text, which Wathe's
 * lookup still needs and which shows if this rewrite ever misses.
 * 把支配者信件附言的两行改成设计稿里的精确金/绿/红色。Wathe 的信件行全部来自翻译键，原版 {@code §} 调色板无法表达
 * 设计稿的 #C6B53A / #008000 / #C00000；这里在 Wathe 建好其余内容后，只把这两行重写成带 RGB 样式的分段翻译文本。每行的
 * 分段（{@code tooltip3.a}-{@code .e}、{@code tooltip4.a}-{@code .e}）拼接后等于整行的 {@code tooltip3} / {@code tooltip4}
 * 文本；Wathe 的查找仍需要整行键，且重写未生效时显示的就是它。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsDominatorLetterMixin {
    private static final int GOLD = 0xC6B53A;
    private static final int GREEN = 0x008000;
    private static final int RED = 0xC00000;
    private static final String LINE_3_KEY = "tip.letter.dominator.tooltip3";
    private static final String LINE_4_KEY = "tip.letter.dominator.tooltip4";

    @Inject(method = "applyLetterLore", at = @At("TAIL"))
    private static void sparkwitch$recolorDominatorPostscript(
            ItemStack stack,
            ServerPlayerEntity player,
            Role role,
            String roleKey,
            String factionKey,
            int color,
            int roleColor,
            CallbackInfo ci
    ) {
        if (!DominatorRules.isDominator(role)) {
            return;
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return;
        }
        List<Text> lines = new ArrayList<>(lore.lines());
        boolean changed = false;
        for (int index = 0; index < lines.size(); index++) {
            Text line = lines.get(index);
            if (!(line.getContent() instanceof TranslatableTextContent content)) {
                continue;
            }
            if (LINE_3_KEY.equals(content.getKey())) {
                lines.set(index, postscriptLine3(line.getStyle()));
                changed = true;
            } else if (LINE_4_KEY.equals(content.getKey())) {
                lines.set(index, postscriptLine4(line.getStyle()));
                changed = true;
            }
        }
        if (changed) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
    }

    private static Text postscriptLine3(Style base) {
        return Text.empty()
                .setStyle(base)
                .append(Text.translatable("tip.letter.dominator.tooltip3.a"))
                .append(colored("tip.letter.dominator.tooltip3.b", base, GOLD))
                .append(Text.translatable("tip.letter.dominator.tooltip3.c"))
                .append(colored("tip.letter.dominator.tooltip3.d", base, GREEN))
                .append(Text.translatable("tip.letter.dominator.tooltip3.e"));
    }

    private static Text postscriptLine4(Style base) {
        return Text.empty()
                .setStyle(base)
                .append(Text.translatable("tip.letter.dominator.tooltip4.a"))
                .append(colored("tip.letter.dominator.tooltip4.b", base, RED))
                .append(Text.translatable("tip.letter.dominator.tooltip4.c"))
                .append(colored("tip.letter.dominator.tooltip4.d", base, GREEN))
                .append(Text.translatable("tip.letter.dominator.tooltip4.e"));
    }

    private static MutableText colored(String key, Style base, int rgb) {
        return Text.translatable(key).setStyle(base.withColor(rgb));
    }
}
