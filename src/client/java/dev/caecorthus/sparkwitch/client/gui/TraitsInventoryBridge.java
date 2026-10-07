package dev.caecorthus.sparkwitch.client.gui;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Optional reflection speaks only the public facade. No Traits classes in descriptors or imports. */
public final class TraitsInventoryBridge {
    private final Supplier<Class<?>> facade;
    private final Class<?> playerType;
    private final BooleanSupplier ownership;
    private Method register;
    private Method visit;
    private boolean accepted;
    private boolean incompatible;
    private boolean disabled;

    public TraitsInventoryBridge(Supplier<Class<?>> facade, Class<?> playerType, BooleanSupplier ownership) {
        this.facade = facade;
        this.playerType = playerType;
        this.ownership = ownership;
    }

    /** Empty Optional means failure/unavailable; Optional.of(empty list) is a successful empty visit. */
    public <T> Optional<List<T>> collect(Object player, BiFunction<Object, List<?>, T> copyEntry) {
        if (disabled || incompatible) return Optional.empty();
        try {
            if (visit == null) {
                Class<?> type = facade.get();
                if (type == null) return Optional.empty();
                Method resolvedVisit = type.getMethod("visitOwnerInventoryTraits", playerType, BiConsumer.class);
                Method resolvedRegister = type.getMethod("registerExternalInventoryPresenterV1", BooleanSupplier.class);
                if (resolvedVisit.getReturnType() != void.class || resolvedRegister.getReturnType() != boolean.class) {
                    incompatible = true;
                    return Optional.empty();
                }
                visit = resolvedVisit;
                register = resolvedRegister;
            }
            // A false response may simply mean Traits' client initializer has not run yet.
            if (!accepted) accepted = Boolean.TRUE.equals(register.invoke(null, ownership));
            if (!accepted) return Optional.empty();
            List<T> entries = new ArrayList<>();
            BiConsumer<Object, List<?>> visitor = (tag, tooltip) -> entries.add(copyEntry.apply(tag, tooltip));
            visit.invoke(null, player, visitor);
            return Optional.of(List.copyOf(entries));
        } catch (NoSuchMethodException oldVersion) {
            incompatible = true;
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    public void disable() { disabled = true; }
    public void reset() { disabled = false; }
}
