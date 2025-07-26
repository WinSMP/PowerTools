package org.winlogon.powertools

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

class AbsorbAnimal(private val plugin: PowerToolsPlugin) {

    fun executeAbsorb(player: Player) {
        val targetEntity = player.getTargetEntity(5) // 5 block range
        if (targetEntity == null) {
            ChatFormatting.sendError(player, "You must be looking at a pet within 5 blocks.")
            return
        }

        if (targetEntity is Tameable && targetEntity.isTamed && 
            targetEntity.owner?.uniqueId == player.uniqueId) {
            absorbPet(player, targetEntity)
        } else {
            ChatFormatting.sendError(player, "You must be looking at a tamed pet that you own.")
        }
    }

    private fun absorbPet(player: Player, pet: Tameable) {
        val item = ItemStack(Material.STICK)
        val meta = item.itemMeta
        
        val entityType = pet.type
        val displayName = fmt("<dark_aqua>Absorbed ${entityType.name.lowercase().replace('_', ' ')}")
        meta.displayName(displayName)
        
        val lore = listOf(
            fmt("<gray>Right click to spawn</gray>"),
            fmt("<gray>Pet ID: <dark_gray>${pet.uniqueId}</dark_gray></gray>")
        )
        meta.lore(lore)
        
        val pdc = meta.persistentDataContainer
        val entityTypeKey = NamespacedKey(plugin, "absorbed_entity_type")
        pdc.set(entityTypeKey, PersistentDataType.STRING, entityType.name)
        
        val nbtKey = NamespacedKey(plugin, "absorbed_nbt")
        pdc.set(nbtKey, PersistentDataType.STRING, "idk")
        
        item.itemMeta = meta
        
        pet.remove()
        player.inventory.addItem(item)
        player.sendMessage(fmt("<dark_green>Successfully absorbed your pet!"))
    }
    
    private fun fmt(s: String): Component {
        return plugin.fmt(s)
    }
}
