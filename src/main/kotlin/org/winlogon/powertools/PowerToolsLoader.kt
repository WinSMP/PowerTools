package org.winlogon.powertools

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class PowerToolsLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        val resolvers = mapOf(
            "central" to MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR,
            "winlogon-code" to "https://maven.winlogon.org/releases"
        )

        resolvers.forEach { (name, url) ->
            resolver.addRepository(
                RemoteRepository.Builder(name, "default", url).build()
            )
        }

        val lampVersion = "4.0.0-rc.12"
        val dependencies = mapOf(
            "org.winlogon:retrohue" to "0.1.1",
            "org.winlogon:asynccraftr" to "0.1.1",
            "io.github.revxrsal:lamp.common" to lampVersion,
            "io.github.revxrsal:lamp.bukkit" to lampVersion,
            "io.github.revxrsal:lamp.brigadier" to lampVersion
        )

        dependencies.forEach { (artifactId, version) ->
            resolver.addDependency(
                Dependency(
                    DefaultArtifact("$artifactId:$version"),
                    null
                )
            )
        }

        classpathBuilder.addLibrary(resolver)
    }
}
