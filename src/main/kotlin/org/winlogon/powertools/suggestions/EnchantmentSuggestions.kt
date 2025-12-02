package org.winlogon.powertools.suggestions

import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.node.ExecutionContext

import org.bukkit.enchantments.Enchantment

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey

class EnchantmentSuggestions : SuggestionProvider<BukkitCommandActor> {
    override fun getSuggestions(context: ExecutionContext<BukkitCommandActor>): List<String> {

        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        return registry.stream()
            .map { it.key.key }
            .sorted()
            .toList()
    }
}

