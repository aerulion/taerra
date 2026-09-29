package net.aerulion.taerra.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public final class TaerraLoader implements PluginLoader {

  private static final @NotNull String LIBRARIES_RESOURCE = "taerra-libraries.properties";

  @Override
  public void classloader(final @NotNull PluginClasspathBuilder classpathBuilder) {
    final @NotNull Properties libraries = new Properties();
    try (final @Nullable InputStream input =
            getClass().getClassLoader().getResourceAsStream(LIBRARIES_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException(LIBRARIES_RESOURCE + " is missing from the plugin jar");
      }
      libraries.load(input);
    } catch (final @NotNull IOException e) {
      throw new UncheckedIOException(e);
    }

    final @NotNull MavenLibraryResolver resolver = new MavenLibraryResolver();
    for (final @NotNull String coordinates : libraries.getProperty("libraries", "").split(",")) {
      if (!coordinates.isBlank()) {
        resolver.addDependency(new Dependency(new DefaultArtifact(coordinates.strip()), null));
      }
    }
    resolver.addRepository(
        new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
            .build());

    classpathBuilder.addLibrary(resolver);
  }
}
