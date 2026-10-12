package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.entity.Villager
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

/// A villager is nobody's to begin with, so it is gated separately from packing your own animal
const val PACK_VILLAGER_PERMISSION = "powertools.command.pack.villager"

/**
 * Packs a clicked entity into a spawn egg that puts it back the way it was.
 *
 * Two kinds of entity are worth carrying around like this: a pet the player owns, and a villager,
 * whose trades and profession are worth more than its company.
 */
class AbsorbEntity(private val plugin: Plugin) {

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
    private val awaitingClick = HashMap<UUID, PendingWindow>()
    private var lastWindow = 0L

    /**
     * Opens the short window in which the player's next click on a pet or villager packs it.
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
    fun absorbEntityOf(player: Player, target: Entity): Boolean {
        val refusal = refusalFor(player, target)
        if (refusal != null) {
            ChatFormatting.sendError(player, refusal)
            return false
        }

        // the click itself is range checked by the server, this only bounds anything looser
        if (target.world != player.world ||
            target.location.distanceSquared(player.location) > MAX_ENTITY_RANGE * MAX_ENTITY_RANGE) {
            ChatFormatting.sendError(player, "that is too far away")
            return false
        }

        absorb(player, target)
        return true
    }

    /**
     * Why [player] may not pack [target], or null when they may.
     *
     * A pet has to be tamed and owned. A villager belongs to nobody, so it is restricted by
     * permission instead.
     *
     * Internal rather than private, so that the decision can be tested without packing anything.
     */
    internal fun refusalFor(player: Player, target: Entity): String? = when (target) {
        is Villager ->
            if (player.hasPermission(PACK_VILLAGER_PERMISSION)) null
            else "you do not have permission to pack villagers"
        is Tameable ->
            if (target.isTamed && target.owner?.uniqueId == player.uniqueId) null
            else "that is not a tamed pet that you own"
        else -> "you can only pack your own tamed pets or a villager"
    }

    private fun absorb(player: Player, entity: Entity) {
        val spawnEggMaterial = getEggMaterialFor(entity) ?: run {
            ChatFormatting.sendError(player, "invalid entity type")
            return
        }

        // a snapshot is the entity's complete save, without data that the egg would have no business storing.
        // it's null only for entities that are not persistent, and anything worth packing is always persisted
        val snapshot = entity.createSnapshot() ?: run {
            ChatFormatting.sendError(player, "failed to read entity data")
            return
        }

        val egg = ItemStack.of(spawnEggMaterial)

        // vanilla reads only the entity_data component when spawning from a spawn egg, and that is what
        // restores the entity on use: a pet's owner, name and equipment, a villager's trades and reputation
        if (!egg.editMeta(SpawnEggMeta::class.java) { it.setSpawnedEntity(snapshot) }) {
            ChatFormatting.sendError(player, "failed to store entity data")
            return
        }

        val typeName = entity.type.name.lowercase().replace('_', ' ')
        val typeLabel = typeName.sentenceCase(appendPeriod = false)

        egg.editMeta { meta ->
            val entityName = entity.customName()?.let(plainSerializer::serialize)
            val label = if (entityName != null) "$typeLabel ($entityName)" else "Packed $typeLabel"
            meta.itemName(fmt("<gradient:#94587b:#b66c97><label></gradient>", Placeholder.unparsed("label", label)))
            meta.lore(loreFor(entity))
        }

        // the entity is only removed once the egg exists and there is room to hand it over
        if (player.inventory.firstEmpty() == -1) {
            ChatFormatting.sendError(player, "your inventory is full")
            return
        }

        entity.remove()
        player.inventory.addItem(egg)
        player.sendLegacyMessage("<dark_aqua>Successfully</dark_aqua> <gray>packed your $typeName!")
    }

    /**
     * The lore of a packed entity: what the egg does, and what it holds.
     *
     * A villager gets facts worth reading instead of a health bar, since what it carries is a
     * profession and a set of trades rather than a companion.
     *
     * Internal rather than private, so that the lore can be read without packing anything.
     */
    internal fun loreFor(entity: Entity): List<Component> {
        val hint = fmt("<color:#a9859a>Right click to spawn</color>")

        return when (entity) {
            is Tameable -> listOf(
                hint,
                loreLine("Name", plainSerializer.serialize(entity.customName() ?: entity.name())),
                loreLine("Health", "${entity.health / 2} HP"),
            )
            is Villager -> villagerLore(hint, entity)
            else -> listOf(hint)
        }
    }

    private fun villagerLore(hint: Component, villager: Villager): List<Component> = buildList {
        add(hint)
        add(loreLine("Villager name", villager.customName()?.let(plainSerializer::serialize) ?: "Unnamed"))
        add(loreLine("Health", "${villager.health / 2} HP"))
        add(loreLine("Profession", professionLabel(villager.profession)))
        add(loreLine("Biome", keyLabel(villager.villagerType)))
        add(loreLine("Trade level", villager.villagerLevel.toString()))
        add(loreLine("Trades left", villager.recipeCount.toString()))
    }

    /**
     * One "Label: value" lore line, with the value kept as literal text.
     *
     * A value can be a user-provided value, so it must never be read as markup.
     */
    private fun loreLine(label: String, value: String): Component {
        return fmt("<color:#c18aae>$label:</color> <color:#e2d0dc><value></color>", Placeholder.unparsed("value", value))
    }

    /// A registry value such as `farmer` or `plains`, as it reads on an item
    private fun keyLabel(keyed: Keyed): String {
        return keyed.key().value().replace('_', ' ').sentenceCase(appendPeriod = false)
    }

    private fun professionLabel(profession: Keyed): String {
        // NONE is what the registry calls a villager with no profession yet, which players call
        // unemployed. It is compared by key, because a profession is not always the registry value itself
        val hasNoProfession = profession.key() == Villager.Profession.NONE.key()
        return if (hasNoProfession) "Unemployed" else keyLabel(profession)
    }

    /// Formats a string to a Component, converting MiniMessage and legacy codes
    private fun fmt(s: String, placeholder: TagResolver = TagResolver.empty()): Component {
        return s.toComponent(placeholder)
    }

    private fun getEggMaterialFor(entity: Entity): Material? {
        return EGG_MAP[entity.type]
    }
}

class ClickListener(private val absorber: AbsorbEntity, private val player: Player, private val windowId: Long) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onClick(event: PlayerInteractAtEntityEvent) {
        if (event.player !== player || event.hand != EquipmentSlot.HAND) return

        // the click ends the window whether it packs anything
        absorber.cancel(player, windowId)

        if (absorber.absorbEntityOf(player, event.rightClicked)) {
            // the target is about to be removed, so the interaction it would normally perform
            // (opening a pet's inventory, leading it, shearing it, opening a villager's trades)
            // must not also happen
            event.isCancelled = true
        }
    }
}

/**
 * A player's open window: the id that expires it and the listener that serves it.
 */
private class PendingWindow(val id: Long, val listener: ClickListener)
