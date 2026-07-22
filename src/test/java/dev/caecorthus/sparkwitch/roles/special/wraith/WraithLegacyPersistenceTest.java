package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.component.LegacyWraithPlayerComponent;
import dev.caecorthus.sparkwitch.component.LegacyWraithRoundComponent;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.component.WraithRoundComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.ladysnake.cca.api.v3.component.ComponentContainer;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.internal.base.asm.CcaBootstrap;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WraithLegacyPersistenceTest {
    private static final String COMPONENTS = "cardinal_components";
    private static final AtomicInteger FACTORY_IDS = new AtomicInteger();

    @BeforeAll
    static void initializeCcaContainerRuntime() throws Exception {
        if (FabricLauncherBase.getLauncher() != null) {
            return;
        }
        ClassLoader classLoader = WraithLegacyPersistenceTest.class.getClassLoader();
        GameProvider gameProvider = (GameProvider) Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{GameProvider.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGameId" -> "minecraft";
                    case "getGameName" -> "Minecraft";
                    case "getRawGameVersion", "getNormalizedGameVersion" -> "1.21.1";
                    case "getRuntimeNamespace" -> MappingConfiguration.NAMED_NAMESPACE;
                    case "getBuiltinMods" -> List.of();
                    case "getBuiltinTransforms" -> Set.of();
                    case "getLaunchDirectory" -> Path.of(".").toAbsolutePath();
                    case "isEnabled" -> true;
                    case "getLaunchArguments" -> new String[0];
                    default -> null;
                }
        );
        FabricLoaderImpl.INSTANCE.setGameProvider(gameProvider);
        MappingConfiguration mappings = new MappingConfiguration();
        FabricLauncher launcher = (FabricLauncher) Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{FabricLauncher.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMappingConfiguration" -> mappings;
                    case "getEnvironmentType" -> EnvType.SERVER;
                    case "getTargetClassLoader" -> classLoader;
                    case "isDevelopment" -> true;
                    case "getDefaultRuntimeNamespace" -> MappingConfiguration.NAMED_NAMESPACE;
                    case "getClassPath" -> List.<Path>of();
                    case "isClassLoaded" -> false;
                    case "loadIntoTarget" -> Class.forName((String) args[0], true, classLoader);
                    case "getResourceAsStream" -> classLoader.getResourceAsStream((String) args[0]);
                    case "getClassByteArray" -> classBytes(classLoader, (String) args[0]);
                    case "getManifest", "getEntrypoint" -> null;
                    default -> null;
                }
        );
        FabricLauncherBase.setLauncher(launcher);
        Field additionalIdsField = CcaBootstrap.class.getDeclaredField("additionalComponentIds");
        additionalIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Collection<Identifier> additionalIds = (Collection<Identifier>) additionalIdsField.get(CcaBootstrap.INSTANCE);
        additionalIds.addAll(List.of(
                Identifier.of("sparkwitch", "wraith_player"),
                Identifier.of("sparkwitch", "wraith_round"),
                Identifier.of("sparktraits", "wraith_player"),
                Identifier.of("sparktraits", "wraith_round")
        ));
    }

    @Test
    void canonicalizesExactlyTheSixLegacyRoleIds() {
        assertEquals(Identifier.of("sparkwitch", "wraith"), canonicalize("wraith"));
        assertEquals(Identifier.of("sparkwitch", "wind_spirit"), canonicalize("wind_spirit"));
        assertEquals(Identifier.of("sparkwitch", "guardian_angel"), canonicalize("guardian_angel"));
        assertEquals(Identifier.of("sparkwitch", "vendetta"), canonicalize("vendetta"));
        assertEquals(Identifier.of("sparkwitch", "saboteur"), canonicalize("saboteur"));
        assertEquals(Identifier.of("sparkwitch", "curser"), canonicalize("curser"));

        Identifier unrelated = Identifier.of("other", "curser");
        assertSame(unrelated, WraithLegacyRoleIds.canonicalize(unrelated));
    }

    @Test
    void migratesGameWorldRoleListsAndDisabledRolesWithoutOverwritingCanonicalData() {
        UUID legacyWraith = UUID.randomUUID();
        UUID canonicalWraith = UUID.randomUUID();
        NbtCompound tag = new NbtCompound();
        tag.put("sparktraits:wraith", uuidList(legacyWraith));
        tag.put("sparkwitch:wraith", uuidList(canonicalWraith));
        tag.put("sparktraits:curser", uuidList(legacyWraith));
        tag.put("other:wraith", uuidList(legacyWraith));
        tag.put("DisabledRoles", strings(
                "sparktraits:wraith",
                "sparktraits:curser",
                "other:sparktraits:curser",
                "sparktraits:curser_extra"
        ));

        WraithLegacyRoleIds.migrateGameWorldNbt(tag);

        assertEquals(canonicalWraith, NbtHelper.toUuid(tag.getList("sparkwitch:wraith", NbtElement.INT_ARRAY_TYPE).get(0)));
        assertTrue(tag.contains("sparktraits:wraith"));
        assertTrue(tag.contains("sparkwitch:curser"));
        assertTrue(tag.contains("sparktraits:curser"));
        assertTrue(tag.contains("other:wraith"));
        assertEquals(
                strings("sparkwitch:wraith", "sparkwitch:curser", "other:sparktraits:curser", "sparktraits:curser_extra"),
                tag.getList("DisabledRoles", NbtElement.STRING_TYPE)
        );
    }

    @Test
    void migratesOnlyExactRoleHistoryStrings() {
        NbtCompound exact = roleHistoryEntry("sparktraits:guardian_angel");
        NbtCompound unrelated = roleHistoryEntry("other:sparktraits:guardian_angel");
        NbtCompound partial = roleHistoryEntry("sparktraits:guardian_angel_extra");
        NbtCompound player = new NbtCompound();
        player.put("Entries", compounds(exact, unrelated, partial));
        NbtCompound tag = new NbtCompound();
        tag.put("History", compounds(player));

        WraithLegacyRoleIds.migrateRoleHistoryNbt(tag);

        NbtList entries = tag.getList("History", NbtElement.COMPOUND_TYPE)
                .getCompound(0)
                .getList("Entries", NbtElement.COMPOUND_TYPE);
        assertEquals("sparkwitch:guardian_angel", entries.getCompound(0).getString("RoleId"));
        assertEquals("other:sparktraits:guardian_angel", entries.getCompound(1).getString("RoleId"));
        assertEquals("sparktraits:guardian_angel_extra", entries.getCompound(2).getString("RoleId"));
    }

    @Test
    void canonicalOnlyContainerRoundTripsWithoutLegacyTags() {
        TestProvider source = new TestProvider(true);
        UUID consumed = UUID.randomUUID();
        source.player().activate(WraithState.Alignment.KILLER);
        source.player().recordTaskCompletion();
        source.player().setPromotionPending(true);
        source.round().beginRound(15);
        assertTrue(source.round().tryConsume(consumed));

        NbtCompound serialized = source.container.toTag(new NbtCompound(), null);

        assertComponentIds(serialized, true, false);
        TestProvider restored = new TestProvider(false);
        restored.container.fromTag(serialized.copy(), null);
        assertTrue(restored.player().isRestricted());
        assertEquals(1, restored.player().getCompletedTasks());
        assertEquals(WraithState.Alignment.KILLER, restored.player().getAlignment());
        assertTrue(restored.player().isPromotionPending());
        assertEquals(15, restored.round().getStartingPlayerCount());
        assertEquals(1, restored.round().getConsumedCount());
        assertTrue(restored.round().getConsumedPlayers().contains(consumed));
    }

    @Test
    void legacyOnlyContainerImportsOnceAndWritesCanonicalOnly() {
        TestProvider provider = new TestProvider(true);
        UUID consumed = UUID.randomUUID();

        provider.container.fromTag(legacyContainerTag(WraithState.Alignment.GOOD, 2, 15, consumed), null);

        assertTrue(provider.player().isRestricted());
        assertEquals(2, provider.player().getCompletedTasks());
        assertEquals(WraithState.Alignment.GOOD, provider.player().getAlignment());
        assertEquals(15, provider.round().getStartingPlayerCount());
        assertTrue(provider.round().getConsumedPlayers().contains(consumed));

        provider.container.fromTag(legacyContainerTag(WraithState.Alignment.KILLER, 9, 20, UUID.randomUUID()), null);
        assertEquals(WraithState.Alignment.GOOD, provider.player().getAlignment());
        assertEquals(2, provider.player().getCompletedTasks());
        assertEquals(15, provider.round().getStartingPlayerCount());

        NbtCompound serialized = provider.container.toTag(new NbtCompound(), null);
        assertComponentIds(serialized, true, false);
    }

    @Test
    void historicalCivilianAlignmentMigratesToGood() {
        TestProvider provider = new TestProvider(true);
        provider.container.fromTag(legacyContainerTag("CIVILIAN", 3, 15, UUID.randomUUID()), null);

        assertTrue(provider.player().isActive());
        assertTrue(provider.player().isPromotionPending());
        assertEquals(WraithState.Alignment.GOOD, provider.player().getAlignment());
    }

    @Test
    void activeRecordWithoutAlignmentUsesTheLegacyKillerDefault() {
        TestProvider provider = new TestProvider(true);
        provider.container.fromTag(legacyContainerTag((String) null, 3, 15, UUID.randomUUID()), null);

        assertTrue(provider.player().isActive());
        assertTrue(provider.player().isPromotionPending());
        assertEquals(WraithState.Alignment.KILLER, provider.player().getAlignment());
    }

    @Test
    void canonicalActiveRecordWithoutAlignmentUsesTheLegacyKillerDefault() {
        TestProvider provider = new TestProvider(true);
        NbtCompound components = new NbtCompound();
        NbtCompound player = new NbtCompound();
        player.putBoolean("WraithActive", true);
        player.putBoolean("WraithRestricted", true);
        player.putInt("WraithCompletedTasks", 3);
        player.putBoolean("WraithPromotionPending", true);
        components.put("sparkwitch:wraith_player", player);
        NbtCompound root = new NbtCompound();
        root.put(COMPONENTS, components);

        provider.container.fromTag(root, null);

        assertTrue(provider.player().isActive());
        assertTrue(provider.player().isPromotionPending());
        assertEquals(WraithState.Alignment.KILLER, provider.player().getAlignment());
    }

    @Test
    void canonicalWinsWhenBothTagsExistInEitherContainerOrder() {
        for (boolean canonicalFirst : new boolean[]{true, false}) {
            TestProvider provider = new TestProvider(canonicalFirst);
            provider.container.fromTag(bothContainerTag(), null);

            assertTrue(provider.player().isActive());
            assertFalse(provider.player().isRestricted());
            assertEquals(1, provider.player().getCompletedTasks());
            assertEquals(WraithState.Alignment.KILLER, provider.player().getAlignment());
            assertEquals(10, provider.round().getStartingPlayerCount());
            assertEquals(0, provider.round().getConsumedCount());
        }
    }

    @Test
    void noLegacyTagDoesNotCreateLegacyOutput() {
        TestProvider provider = new TestProvider(false);

        provider.container.fromTag(new NbtCompound(), null);
        assertFalse(provider.player().isActive());
        assertEquals(0, provider.round().getStartingPlayerCount());

        NbtCompound serialized = provider.container.toTag(new NbtCompound(), null);
        NbtCompound components = serialized.getCompound(COMPONENTS);
        assertFalse(components.contains("sparktraits:wraith_player"));
        assertFalse(components.contains("sparktraits:wraith_round"));
    }

    private static Identifier canonicalize(String path) {
        return WraithLegacyRoleIds.canonicalize(Identifier.of("sparktraits", path));
    }

    private static NbtCompound bothContainerTag() {
        NbtCompound components = legacyContainerTag(WraithState.Alignment.GOOD, 8, 20, UUID.randomUUID())
                .getCompound(COMPONENTS);
        NbtCompound player = new NbtCompound();
        player.putBoolean("WraithActive", true);
        player.putBoolean("WraithRestricted", false);
        player.putInt("WraithCompletedTasks", 1);
        player.putString("WraithAlignment", WraithState.Alignment.KILLER.name());
        components.put("sparkwitch:wraith_player", player);
        NbtCompound round = new NbtCompound();
        round.putInt("StartingPlayerCount", 10);
        round.put("ConsumedPlayers", new NbtList());
        components.put("sparkwitch:wraith_round", round);
        NbtCompound root = new NbtCompound();
        root.put(COMPONENTS, components);
        return root;
    }

    private static NbtCompound legacyContainerTag(
            WraithState.Alignment alignment,
            int completedTasks,
            int startingPlayers,
            UUID consumed
    ) {
        return legacyContainerTag(alignment == null ? null : alignment.name(), completedTasks, startingPlayers, consumed);
    }

    private static NbtCompound legacyContainerTag(
            String alignmentName,
            int completedTasks,
            int startingPlayers,
            UUID consumed
    ) {
        NbtCompound player = new NbtCompound();
        player.putBoolean("WraithActive", true);
        player.putBoolean("WraithRestricted", true);
        player.putInt("WraithCompletedTasks", completedTasks);
        if (alignmentName != null) {
            player.putString("WraithAlignment", alignmentName);
        }
        player.putBoolean("WraithPromotionPending", true);
        NbtCompound round = new NbtCompound();
        round.putInt("StartingPlayerCount", startingPlayers);
        round.put("ConsumedPlayers", strings(consumed.toString()));
        NbtCompound components = new NbtCompound();
        components.put("sparktraits:wraith_player", player);
        components.put("sparktraits:wraith_round", round);
        NbtCompound root = new NbtCompound();
        root.put(COMPONENTS, components);
        return root;
    }

    private static void assertComponentIds(NbtCompound root, boolean canonical, boolean legacy) {
        NbtCompound components = root.getCompound(COMPONENTS);
        assertEquals(canonical, components.contains("sparkwitch:wraith_player"));
        assertEquals(canonical, components.contains("sparkwitch:wraith_round"));
        assertEquals(legacy, components.contains("sparktraits:wraith_player"));
        assertEquals(legacy, components.contains("sparktraits:wraith_round"));
    }

    private static NbtCompound roleHistoryEntry(String roleId) {
        NbtCompound entry = new NbtCompound();
        entry.putString("RoleId", roleId);
        return entry;
    }

    private static NbtList uuidList(UUID... values) {
        NbtList list = new NbtList();
        for (UUID value : values) {
            list.add(NbtHelper.fromUuid(value));
        }
        return list;
    }

    private static NbtList strings(String... values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }

    private static NbtList compounds(NbtCompound... values) {
        NbtList list = new NbtList();
        for (NbtCompound value : values) {
            list.add(value);
        }
        return list;
    }

    private static byte[] classBytes(ClassLoader classLoader, String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    public static final class TestProvider implements ComponentProvider {
        private final ComponentContainer container;

        private TestProvider(boolean canonicalFirst) {
            ComponentContainer.Factory.Builder<TestProvider> builder = ComponentContainer.Factory.builder(TestProvider.class);
            if (canonicalFirst) {
                addCanonical(builder);
                addLegacy(builder);
            } else {
                addLegacy(builder);
                addCanonical(builder);
            }
            this.container = builder
                    .factoryNameSuffix("SparkWitchWraithMigrationTest" + FACTORY_IDS.incrementAndGet())
                    .build()
                    .createContainer(this);
        }

        private static void addCanonical(ComponentContainer.Factory.Builder<TestProvider> builder) {
            builder.component(WraithPlayerComponent.KEY, WraithPlayerComponent::new);
            builder.component(WraithRoundComponent.KEY, WraithRoundComponent::new);
        }

        private static void addLegacy(ComponentContainer.Factory.Builder<TestProvider> builder) {
            builder.component(LegacyWraithPlayerComponent.KEY, LegacyWraithPlayerComponent::new);
            builder.component(LegacyWraithRoundComponent.KEY, LegacyWraithRoundComponent::new);
        }

        private WraithPlayerComponent player() {
            return WraithPlayerComponent.KEY.get(this);
        }

        private WraithRoundComponent round() {
            return WraithRoundComponent.KEY.get(this);
        }

        @Override
        public ComponentContainer getComponentContainer() {
            return container;
        }
    }
}
