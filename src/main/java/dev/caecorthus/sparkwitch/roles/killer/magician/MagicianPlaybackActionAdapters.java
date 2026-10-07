package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 后续 Witch 扩展职业复用的动作适配器注册表。 */
public final class MagicianPlaybackActionAdapters {
    private static final List<MagicianPlaybackActionAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private MagicianPlaybackActionAdapters() {}
    public static void register(MagicianPlaybackActionAdapter adapter){ if(adapter!=null) ADAPTERS.add(adapter); }
    public static MagicianPlaybackActionAdapter find(ItemStack stack){ for(var adapter:ADAPTERS) if(adapter.supports(stack)) return adapter; return null; }
}
