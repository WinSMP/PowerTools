package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.SpawnEggMeta
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin
import org.winlogon.powertools.ChatFormatting.sentenceCase
import org.winlogon.powertools.ChatFormatting.sendLegacyMessage

const val MAX_ENTITY_RANGE = 5

class AbsorbAnimal(private val plugin: Plugin) {

    private companion object {

        private val plainSerializer = PlainTextComponentSerializer.plainText()
        private val EGG_MAP: Map<EntityType, Material> = Material.entries
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
        val targetEntity = player.getTargetEntity(MAX_ENTITY_RANGE) ?: run {
            ChatFormatting.sendError(player, "You must be looking at a pet within $MAX_ENTITY_RANGE blocks.")
            return
        }

        if (targetEntity is Tameable && targetEntity.isTamed && targetEntity.owner?.uniqueId == player.uniqueId) {
            absorbPet(player, targetEntity)
        } else {
            ChatFormatting.sendError(player, "You must be looking at a tamed pet that you own.")
        }
    }

    private fun absorbPet(player: Player, pet: Tameable) {
        val petType = pet.type
        val typeLabel = petType.name.lowercase().replace('_', ' ')
        val petName = pet.customName()

        val spawnEggMaterial = getEggMaterialFor(pet) ?: run {
            ChatFormatting.sendError(player, "invalid pet type")
            return
        }

        // a snapshot is the pet's complete save, without data that the egg would have no business storing.
        // it's null only for entities that are not persistent (a tamed pet is always persisted)
        val snapshot = pet.createSnapshot() ?: run {
            ChatFormatting.sendError(player, "failed to read pet data")
            return
        }

        val egg = ItemStack(spawnEggMaterial)

        // vanilla reads only the entity_data component when spawning from a spawn egg, and that is used to
        // restore the tamed state on use
        if (!egg.editMeta(SpawnEggMeta::class.java) { it.setSpawnedEntity(snapshot) }) {
            ChatFormatting.sendError(player, "failed to store pet data")
            return
        }

        egg.editMeta { meta ->
            // the item's own name is copied onto whatever spawns from it, so an unnamed pet must
            // get an unlabelled egg or it would come back named "Absorbed <type>"
            val mobType = typeLabel.sentenceCase(appendPeriod = false)
            if (petName != null) {
                val customName = plainSerializer.serialize(petName)
                // TODO: replace with a gradient from #94587b to #b66c97
                meta.displayName(fmt("<color:#b66c97>$mobType ($customName)</color>"))
            } else {
                meta.displayName(fmt("<color:#b66c97>Packed $mobType</color>"))
            }
            meta.lore(listOf(
                // fmt("<gray>Absorbed $typeLabel</gray>"),
                fmt("<gray>Right click to spawn</gray>"),
                fmt("<gray>Pet name: <dark_gray>${plainSerializer.serialize(petName ?: pet.name())}</dark_gray></gray>"),
                fmt("<gray>Pet health: <dark_gray>${pet.health}</dark_gray></gray>"),
            ))
        }

        // the pet is only removed once the egg exists and there is room to hand it over
        if (player.inventory.firstEmpty() == -1) {
            ChatFormatting.sendError(player, "your inventory is full")
            return
        }

        pet.remove()
        player.inventory.addItem(egg)
        player.sendLegacyMessage("<dark_aqua>Successfully</dark_aqua> <gray>absorbed your pet!")
    }

    /// Formats a string to a Component, converting MiniMessage and legacy codes
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
            val hit = player.rayTraceEntities(MAX_ENTITY_RANGE)?.hitEntity ?: return

            if (hit == event.rightClicked) {
                AbsorbAnimal(plugin).absorbPetOf(player)
                HandlerList.unregisterAll(this)
            }
        }
    }
}
