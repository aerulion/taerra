package net.aerulion.taerra.testing;

import com.google.common.util.concurrent.MoreExecutors;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.bukkit.craftbukkit.CraftRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public final class RegistryBootstrap implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(final @NotNull LauncherSession session) {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    final @NotNull ResourceManager resources =
        new MultiPackResourceManager(
            PackType.SERVER_DATA,
            List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
    final @NotNull LayeredRegistryAccess<RegistryLayer> layers =
        RegistryLayer.createRegistryAccess();
    final @NotNull List<Registry.PendingTags<?>> tags =
        TagLoader.loadTagsForExistingRegistries(
            resources,
            layers.getLayer(RegistryLayer.STATIC),
            ReloadableRegistrarEvent.Cause.INITIAL);
    CraftRegistry.setMinecraftRegistry(
        layers
            .replaceFrom(
                RegistryLayer.WORLD,
                RegistryDataLoader.load(
                        resources,
                        TagLoader.buildUpdatedLookups(
                            layers.getAccessForLoading(RegistryLayer.WORLD), tags),
                        RegistryDataLoader.WORLD_REGISTRIES,
                        MoreExecutors.directExecutor())
                    .join())
            .compositeAccess());
  }
}
