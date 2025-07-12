package org.winlogon.powertools
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component

import scala.jdk.CollectionConverters._

class AbsorbAnimal(private val plugin: PowerToolsPlugin) {
    private def executeAbsorb(player: Player): Unit = {
        import org.bukkit.entity.Tameable
        import org.bukkit.NamespacedKey
        import org.bukkit.persistence.PersistentDataType
    
        val targetEntity = player.getTargetEntity(5) // 5 block range
        if (targetEntity == null) {
            sendError(player, "You must be looking at a pet within 5 blocks.")
            return
        }
    
        targetEntity match {
            case tameable: Tameable if tameable.isTamed && tameable.getOwner != null && tameable.getOwner.getUniqueId == player.getUniqueId =>
                absorbPet(player, tameable)
            case _ =>
                sendError(player, "You must be looking at a tamed pet that you own.")
        }
    }
    
    private def absorbPet(player: Player, pet: Tameable): Unit = {
        // create placeholder item - will be spawn egg later
        val item = ItemStack(Material.STICK)
        val meta = item.getItemMeta()
        
        val entityType = pet.getType
        val displayName = fmt(s"<dark_aqua>Absorbed ${entityType.name().toLowerCase.replace('_', ' ')}")
        meta.displayName(displayName)
        
        val lore = List(
            fmt("<gray>Right click to spawn</gray>"),
            fmt(s"<gray>Pet ID: <dark_gray>${pet.getUniqueId}</dark_gray></gray>")
        ).asJava
        meta.lore(lore)
        
        // store nbt data
        val pdc = meta.getPersistentDataContainer()
        val entityTypeKey = NamespacedKey(plugin, "absorbed_entity_type")
        pdc.set(entityTypeKey, PersistentDataType.STRING, entityType.name())
        
        val nbtKey = NamespacedKey(plugin, "absorbed_nbt")
        // TODO: (de)serialize NBT data as PDC
        pdc.set(nbtKey, PersistentDataType.STRING, "idk")
        
        item.setItemMeta(meta)
        
        // remove pet from world
        pet.remove()
        
        // give item to player
        player.getInventory.addItem(item)
        player.sendMessage(fmt(s"<dark_green>Successfully absorbed your pet!"))
    }
    
    def fmt(s: String): Component = {
        Component.text("")
    }
    
    def sendError(p: Player, s: String): Unit = {
        
    }
}

