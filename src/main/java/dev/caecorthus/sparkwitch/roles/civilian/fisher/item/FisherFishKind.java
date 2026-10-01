package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.util.Identifier;

/** The instantly-used fish; each maps to one registered {@link FisherFishItem}. / 即时使用的鱼，各对应一个物品。 */
public enum FisherFishKind {
    SALMON(FisherRules.SALMON_ID, 1),
    COD(FisherRules.COD_ID, 1),
    CLOWNFISH(FisherRules.CLOWNFISH_ID, 1),
    GOLDFISH(FisherRules.GOLDFISH_ID, 1),
    GLIMMERFISH(FisherRules.GLIMMERFISH_ID, 2);

    private final Identifier itemId;
    private final int tooltipLines;

    FisherFishKind(Identifier itemId, int tooltipLines) {
        this.itemId = itemId;
        this.tooltipLines = tooltipLines;
    }

    public Identifier itemId() {
        return itemId;
    }

    public int tooltipLines() {
        return tooltipLines;
    }
}
