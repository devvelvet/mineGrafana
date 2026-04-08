package dev.velvet.minegrafana.paper;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Paper PluginLoader — provides runtime dependencies via Maven.
 * <p>
 * Paper's plugin classloader is parent-first, meaning Paper's bundled libraries
 * (Jackson 2.13, etc.) are loaded BEFORE our shadow JAR classes.
 * <p>
 * The libraryLoader (MavenLibraryResolver) is checked BEFORE the parent classloader,
 * so dependencies declared here override Paper's bundled versions.
 * <p>
 * Written in Java because Kotlin runtime is not available at loader stage.
 */
public class MineGrafanaLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", "https://repo.maven.apache.org/maven2/"
        ).build());

        // Kotlin — not provided by Paper's isolated classloader
        resolver.addDependency(new Dependency(
                new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.3.0"), null));
        resolver.addDependency(new Dependency(
                new DefaultArtifact("org.jetbrains.kotlin:kotlin-reflect:2.3.0"), null));

        // Jackson is relocated in the shadow JAR (not provided here).

        classpathBuilder.addLibrary(resolver);
    }
}
