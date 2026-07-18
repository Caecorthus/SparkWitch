package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import net.minecraft.item.Item;

/** The one-use shop item; platter interaction stays server-authoritative in the role-owned adapter. */
public final class PoisonAppleItem extends Item {
    public PoisonAppleItem(Settings settings) {
        super(settings);
    }
}
