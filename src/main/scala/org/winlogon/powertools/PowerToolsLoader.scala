package org.winlogon.powertools

import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class PowerToolsLoader extends PluginLoader {
    override def classloader(classpathBuilder: PluginClasspathBuilder): Unit = {
        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "central",
                "default",
                "https://repo.maven.apache.org/maven2/"
            ).build()
        )

        resolver.addRepository(
            RemoteRepository.Builder(
                "winlogon-code",
                "default",
                "https://maven.winlogon.org/releases"
            ).build()
        )

        resolver.addDependency(
            Dependency(
                DefaultArtifact("org.winlogon:retrohue:0.1.1"),
                null
            )
        )

        classpathBuilder.addLibrary(resolver)
    }
}
