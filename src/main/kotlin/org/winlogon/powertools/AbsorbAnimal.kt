package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Material
import org.bukkit.entity.Entity
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
import org.winlogon.powertools.ChatFormatting.toComponent
import java.util.UUID

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
    
    // one pending window per player, so running the command twice replaces the first window
    // instead of stacking listeners that would all react to the same click
    private val awaitingClick = HashMap<UUID, PendingWindow>()
    private var lastWindow = 0L

    /**
     * Opens the short window in which the player's next click on a pet packs that pet.
     *
     * The returned id identifies the window, so that its expiry only ever closes itself.
     */
    fun awaitClick(player: Player): Long {
        cancel(player)

        val window = PendingWindow(++lastWindow, ClickListener(this, player, lastWindow))
        awaitingClick[player.uniqueId] = window
        plugin.server.pluginManager.registerEvents(window.listener, plugin)
        return window.id
    }

    /**
     * Closes the player's pending window, returning whether one was actually open. A window id
     * narrows this to that window, leaving a newer one alone when an old one has already lapsed.
     */
    fun cancel(player: Player, windowId: Long? = null): Boolean {
        val window = awaitingClick[player.uniqueId] ?: return false
        if (windowId != null && window.id != windowId) {
            return false
        }

        awaitingClick.remove(player.uniqueId)
        HandlerList.unregisterAll(window.listener)
        return true
    }

    /**
     * Packs the clicked entity, returning whether it was packed.
     */
    fun absorbPetOf(player: Player, target: Entity): Boolean {
        if (target !is Tameable || !target.isTamed || target.owner?.uniqueId != player.uniqueId) {
            ChatFormatting.sendError(player, "that is not a tamed pet that you own")
            return false
        }

        // the click itself is range checked by the server, this only bounds anything looser
        if (target.world != player.world ||
            target.location.distanceSquared(player.location) > MAX_ENTITY_RANGE * MAX_ENTITY_RANGE) {
            ChatFormatting.sendError(player, "that pet is too far away")
            return false
        }

        absorbPet(player, target)
        return true
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
        // it's null only for entities that are not persistent, and a tamed pet is always persisted
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
            val mobType = typeLabel.sentenceCase(appendPeriod = false)
            val label = if (petName != null) {
                "$mobType (${plainSerializer.serialize(petName)})"
            } else {
                "Packed $mobType"
            }
            meta.itemName(fmt("<gradient:#94587b:#b66c97><label></gradient>", Placeholder.unparsed("label", label)))
            meta.lore(listOf(
                // fmt("<gray>Absorbed $typeLabel</gray>"),
                fmt("<gray>Right click to spawn</gray>"),
                fmt(
                    "<gray>Pet name: <dark_gray><name></dark_gray></gray>",
                    Placeholder.unparsed("name", plainSerializer.serialize(petName ?: pet.name()))
                ),
                fmt("<gray>Pet health: <dark_gray>${pet.health / 2}</dark_gray> HP</gray>"),
            ))
        }

        // the pet is only removed once the egg exists and there is room to hand it over
        if (player.inventory.firstEmpty() == -1) {
            ChatFormatting.sendError(player, "your inventory is full")
            return
        }

        pet.remove()
        player.inventory.addItem(egg)
        player.sendLegacyMessage("<dark_aqua>Successfully</dark_aqua> <gray>packed your pet!")
    }

    /// Formats a string to a Component, converting MiniMessage and legacy codes
    private fun fmt(s: String, placeholder: TagResolver = TagResolver.empty()): Component {
        return s.toComponent(placeholder)
    }

    private fun getEggMaterialFor(pet: Tameable): Material? {
        return EGG_MAP[pet.type]
    }
}

class ClickListener(private val absorber: AbsorbAnimal, private val player: Player, private val windowId: Long) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onClick(event: PlayerInteractAtEntityEvent) {
        if (event.player !== player || event.hand != EquipmentSlot.HAND) return

        // the click ends the window whether or not it packs anything
        absorber.cancel(player, windowId)

        if (absorber.absorbPetOf(player, event.rightClicked)) {
            // the pet is about to be removed, so the interaction it would normally perform
            // (opening its inventory, leading it, shearing it) must not also happen
            event.isCancelled = true
        }
    }
}

/**
 * A player's open window: the id that expires it and the listener that serves it.
 */
private class PendingWindow(val id: Long, val listener: ClickListener)
