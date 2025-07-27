package org.winlogon.powertools

import de.tr7zw.changeme.nbtapi.NBT
import de.tr7zw.changeme.nbtapi.NBTEntity
import de.tr7zw.changeme.nbtapi.NBTItem
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SpawnEggMeta
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin

import java.util.function.Function

class AbsorbAnimal(private val plugin: Plugin) {
    private companion object {
        private val plainSerializer = PlainTextComponentSerializer.plainText()
        private val EGG_MAP: Map<EntityType, Material> = Material.values()
            .asSequence()
            .filter { it.name.endsWith("_SPAWN_EGG") }
            .mapNotNull { mat ->
                // remove the suffix to get the entity name
                val entityName = mat.name.removeSuffix("_SPAWN_EGG")

                // try to match it to EntityType
                runCatching { EntityType.valueOf(entityName) }
                    .getOrNull()
                    ?.let { entityType -> entityType to mat }
            }
            .toMap()
    }

    fun absorbPetOf(player: Player) {
        // 5 block range
        val targetEntity = player.getTargetEntity(5) ?: run {
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
        val compound: ReadWriteNBT = NBT.get(pet, Function<ReadableNBT, ReadWriteNBT> { nbt ->
            val copy = NBT.parseNBT(nbt.toString())
            listOf("UUID", "Pos", "Dimension", "id").forEach(copy::removeKey)
            copy
        }) ?: run {
            ChatFormatting.sendError(player, "failed to read pet data")
            return
        }
    
        val spawnEggMaterial = getEggMaterialFor(pet) ?: run {
            ChatFormatting.sendError(player, "invalid pet type")
            return
        }
    
        val baseEgg = ItemStack(spawnEggMaterial)
        val finalEggStack: ItemStack = NBT.modify(baseEgg, Function<ReadWriteItemNBT, ItemStack> { itemNbt ->
            val entityTag = itemNbt.getOrCreateCompound("EntityTag")
            entityTag.mergeCompound(compound)
            baseEgg
        })
    
        val meta = finalEggStack.itemMeta ?: return
        val entityType = pet.type
        meta.displayName(fmt("<dark_aqua>Absorbed ${entityType.name.lowercase().replace('_',' ')}"))
        meta.lore(listOf(
            fmt("<gray>Right click to spawn</gray>"),
            fmt("<gray>Pet name: <dark_gray>${plainSerializer.serialize(pet.name())}</dark_gray></gray>"),
            fmt("<gray>Pet level: <dark_gray>${pet.health}</dark_gray></gray>"),
        ))
        meta.persistentDataContainer.apply {
            set(NamespacedKey(plugin, "absorbed_entity_type"), PersistentDataType.STRING, entityType.name)
            set(NamespacedKey(plugin, "absorbed_nbt"), PersistentDataType.STRING, compound.toString())
        }
        finalEggStack.itemMeta = meta
    
        pet.remove()
        player.inventory.addItem(finalEggStack)
        player.sendMessage(fmt("&7Successfully <gray>absorbed your pet!"))
    }

    private fun fmt(s: String): Component {
        return ChatFormatting.colorConverter.convertToComponent(s, '&')
    }

    private fun getEggMaterialFor(pet: Tameable): Material? {
        return EGG_MAP[pet.type]
    }
}

class ClickListener(private val plugin: Plugin, private val player: Player) : Listener {
    @EventHandler
    fun onClick(event: PlayerInteractAtEntityEvent) {
        if (event.player == player && event.hand == EquipmentSlot.HAND) {
            player.rayTraceEntities(5)?.hitEntity.let {
                if (it == event.rightClicked) {
                    AbsorbAnimal(plugin).absorbPetOf(player)
                    HandlerList.unregisterAll(this@ClickListener)
                }
            }
        }
    }
  }
