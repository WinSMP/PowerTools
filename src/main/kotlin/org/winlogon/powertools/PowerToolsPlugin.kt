package org.winlogon.powertools

import revxrsal.commands.Lamp
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Dependency
import revxrsal.commands.annotation.Named
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.SuggestWith

import revxrsal.commands.bukkit.BukkitLamp
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.powertools.suggestions.EnchantmentSuggestions
import org.winlogon.asynccraftr.AsyncCraftr

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey

import kotlin.math.roundToInt

class PowerToolsPlugin : JavaPlugin() {
    private lateinit var config: Configuration
    private lateinit var absorbAnimal: AbsorbAnimal
    private lateinit var lamp: Lamp<BukkitCommandActor>
    private val romanNumeralRegex = """^(?=.)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$""".toRegex()

    override fun onEnable() {
        config = loadConfig()
        absorbAnimal = AbsorbAnimal(this)

        val sudoCommands = SudoCommands(this)
        lamp = BukkitLamp.builder(this).build()
        lamp.apply {
            register(this@PowerToolsPlugin)
            register(sudoCommands)
        }
    }

    private fun loadConfig(): Configuration {
        saveDefaultConfig()
        reloadConfig()
        val yamlConfig = getConfig()

        val healCfg = HealConfig(
            removeEffects = yamlConfig.getBoolean("heal.remove-effects", true),
            showWhoHealed = yamlConfig.getBoolean("heal.show-who-healed", false)
        )
        val unenchantCfg = UnenchantConfig(yamlConfig.getDouble("unenchant.base-price", 5.0))
        val unsafeCfg = UnsafeEnchantConfig(yamlConfig.getBoolean("unsafe-enchants.enabled", true))
        val transferCfg = TransferConfig(
            enabled = yamlConfig.getBoolean("transfer.enabled", true),
            servers = yamlConfig.getMapList("transfer.servers").map { configMap ->
                val map = HashMap<String, String>()
                configMap.forEach { (k, v) -> map[k.toString()] = v.toString() }
                map
            }
        )

        return Configuration(healCfg, unenchantCfg, unsafeCfg, transferCfg)
    }

    @Command("broadcast", "bc")
    fun broadcast(
        actor: BukkitCommandActor,
        @Named("message") message: String
    ) {
        val formattedMessage = "<dark_gray>[<dark_aqua>Broadcast<dark_gray>] <message>"
        val messageComponent = Placeholder.component("message", Component.text(message, NamedTextColor.GRAY))

        Bukkit.getOnlinePlayers().forEach { it.sendRichMessage(formattedMessage, messageComponent) }
        Bukkit.getConsoleSender().sendRichMessage(formattedMessage, messageComponent)
    }

    @Command("hat")
    fun hat(actor: BukkitCommandActor) {
        val player = actor.sender() as Player

        val inv = player.inventory
        val hand = inv.itemInMainHand
        val helmet = inv.helmet

        if (helmet != null) {
            inv.helmet = hand
            inv.setItemInMainHand(helmet)
            player.sendRichMessage("<gray>Swapping items...</gray>")
        } else {
            inv.helmet = hand
            inv.setItemInMainHand(null)
        }

        player.updateInventory()
        player.sendRichMessage("<gray>Your held item is now <dark_aqua>on your head</dark_aqua>!")
    }

    @Command("invsee")
    fun invsee(
        actor: BukkitCommandActor,
        @Named("target") target: Player
    ) {
        if (target.name == actor.name()) {
            ChatFormatting.sendError(actor.sender(), "you cannot invsee yourself")
            return
        }

        val player = actor.sender() as Player

        if (!target.isOnline) {
            ChatFormatting.sendError(player, "player not found or offline")
            return
        }

        val targetInv = target.inventory
        val inventorySize = maxOf(45, targetInv.size)

        val wrapper = Bukkit.createInventory(null, inventorySize)
        wrapper.contents = targetInv.contents

        player.openInventory(wrapper)

        // TODO: sync changes back into target’s inventory when closing widget
    }

    @Command("absorb")
    fun absorb(actor: BukkitCommandActor) {
        actor.sender().sendRichMessage("<gray>Not yet implemented</gray>")
    }

    @Command("smite")
    fun smite(
        actor: BukkitCommandActor,
        @Named("target") target: Player
    ) {
        val player = actor.sender()
        target.takeIf { it.isOnline }?.let { tgt ->
            try {
                tgt.world.strikeLightning(target.location)
                player.sendRichMessage("<gray>You have smitten <dark_aqua>${target.name}</dark_aqua>!</gray>")
                target.sendRichMessage("<gray>You have been smitten by <b><dark_aqua>a mighty force</b>!</gray>")
            } catch (e: Exception) {
                ChatFormatting.sendError(player, "failed to smite player - ${e.message}")
            }
        } ?: ChatFormatting.sendError(player, "player not found or offline")
    }

    @Command("sudo")
    class SudoCommands(private val plugin: PowerToolsPlugin) {
        @Subcommand("cmd")
        fun sudoCommand(
            actor: BukkitCommandActor,
            @Named("target") target: Player,
            @Named("command") command: String
        ) {
            if (!target.isOnline) {
                ChatFormatting.sendError(actor.sender(), "player not found or offline")
                return
            }
            AsyncCraftr.runEntityTask(plugin, target, Runnable { target.chat("/${command.trim()}") })
        }

        @Subcommand("echo")
        fun sudoChat(
            actor: BukkitCommandActor,
            @Named("target") target: Player,
            @Named("message") message: String
        ) {
            if (!target.isOnline) {
                ChatFormatting.sendError(actor.sender(), "player not found or offline")
                return
            }

            target.chat(message)
        }
    }

    @Command("split", "unenchant")
    fun split(actor: BukkitCommandActor) {
        val player = actor.sender() as Player

        val inventory = player.inventory
        val itemHand = inventory.itemInMainHand

        if (itemHand.type == Material.ENCHANTED_BOOK) {
            ChatFormatting.sendError(player, "the item can't be an enchanted book")
            return
        }

        if (itemHand.type == Material.AIR) {
            ChatFormatting.sendError(player, "you must be holding an item")
            return
        }

        val enchantments = itemHand.enchantments
        if (enchantments.isEmpty()) {
            ChatFormatting.sendError(player, "this item has no enchantments to split")
            return
        }

        val cost = (config.unenchant.basePrice * enchantments.size).roundToInt()
        if (player.totalExperience < cost) {
            ChatFormatting.sendError(player, "you need at least $cost XP to split these enchantments")
            return
        }
        player.giveExp(-cost)

        val meta = itemHand.itemMeta
        enchantments.keys.forEach(meta::removeEnchant)
        itemHand.itemMeta = meta
        itemHand.resetData(DataComponentTypes.REPAIR_COST)

        enchantments.forEach { (ench, level) ->
            val book = ItemStack(Material.ENCHANTED_BOOK)
            val bookMeta = book.itemMeta as EnchantmentStorageMeta
            bookMeta.addStoredEnchant(ench, level, true)
            book.itemMeta = bookMeta

            if (inventory.firstEmpty() == -1) {
                player.world.dropItemNaturally(player.location, book)
            } else {
                inventory.addItem(book)
            }
        }

        player.updateInventory()
        player.sendRichMessage(
            "<gray>Successfully split <enchantments> enchantment(s) for <cost> XP.</gray>",
            Placeholder.component("enchantments", Component.text(enchantments.size.toString(), NamedTextColor.DARK_AQUA)),
            Placeholder.component("cost", Component.text(cost.toString(), NamedTextColor.GREEN))
        )
    }

    @Command("fly")
    fun fly(actor: BukkitCommandActor) {
        val player = actor.sender() as Player
        val canFly = player.allowFlight
        val toggledFly = !canFly

        val statusMessage = if (toggledFly) "enabled" else "disabled"
        val color = if (toggledFly) "green" else "red"
        val playerName = "<dark_aqua>${player.name}</dark_aqua>"

        if (player.gameMode == GameMode.CREATIVE) {
            player.allowFlight = true
            ChatFormatting.sendError(player, "flying is always disabled in creative mode. Keeping fly enabled")
            return
        }
        player.allowFlight = toggledFly
        player.sendRichMessage("<gray>Fly <$color>$statusMessage</$color> for $playerName.</gray>")
    }

    @Command("ue", "unsafe-ench", "uenchant")
    fun enchantUnsafe(
        actor: BukkitCommandActor,
        @SuggestWith(EnchantmentSuggestions::class) @Named("enchantment") enchant: String,
        @Named("level") level: Int
    ) {
        // this should be precomputed somewhere
        val unsafeEnchantsPrefix = ChatFormatting.colorConverter.convertToComponent("&8[&5UE&8]")
        // context
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        val typedKey = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("minecraft:$enchant"))

        val enchantment = registry.get(typedKey) ?: run {
            ChatFormatting.sendError(actor.sender(), "invalid enchantment, or not found")
            return
        }

        val player = actor.sender() as Player

        if (!config.unsafeEnchants.enabled) {
            ChatFormatting.sendError(player, "unsafe enchantments are disabled in config")
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            ChatFormatting.sendError(player, "you must be holding an item to enchant")
            return
        }

        item.addUnsafeEnchantment(enchantment, level)
        player.updateInventory()

        val displayName = PlainTextComponentSerializer.plainText()
            .serialize(enchantment.displayName(1))

        val finalDisplayName = displayName.trim().split("\\s+".toRegex()).let { words ->
            if (words.isNotEmpty() && romanNumeralRegex.matches(words.last())) {
                words.dropLast(1).joinToString(" ")
            } else {
                displayName
            }
        }

        val finalDisplayNameComponent = Component.text(finalDisplayName, NamedTextColor.DARK_AQUA)
        val levelComponent = Component.text(level.toString(), NamedTextColor.DARK_GREEN)

        player.sendRichMessage(
            "<prefix> <gray>Applied <ench-display-name> at level <level>.",
            Placeholder.component("prefix", unsafeEnchantsPrefix),
            Placeholder.component("ench-display-name", finalDisplayNameComponent),
            Placeholder.component("level", levelComponent)
        )
    }

    @Command("heal", "h")
    fun heal(
        actor: BukkitCommandActor,
        @Optional @Named("player") target: Player?
    ) {
        val healTarget = target ?: (actor.sender() as? Player)
        healTarget?.let { healPlayer(it, actor.sender()) }
    }

    private fun healPlayer(player: Player, sender: CommandSender) {
        player.getAttribute(Attribute.MAX_HEALTH)?.let {
            player.health = it.value
        }
        player.foodLevel = 20
        player.saturation = 20f

        if (config.heal.removeEffects) {
            player.activePotionEffects.forEach { effect -> player.removePotionEffect(effect.type) }
        }

        val healedMessage = "<gray>You have been <dark_aqua>healed</dark_aqua>.</gray>"

        if (player != sender) {
            sender.sendRichMessage(
                "<gray><player> has been healed.</gray>",
                Placeholder.component("player", Component.text(player.name))
            )

            val healMsg = if (config.heal.showWhoHealed) {
                "<gray>You have been healed by <staff>.</gray>"
            } else {
                healedMessage
            }

            player.sendRichMessage(healMsg, Placeholder.component("staff", Component.text(sender.name)))
        } else {
            sender.sendRichMessage(healedMessage)
        }
    }
}

// Configuration classes
data class UnenchantConfig(val basePrice: Double)
data class UnsafeEnchantConfig(val enabled: Boolean)
data class HealConfig(val removeEffects: Boolean, val showWhoHealed: Boolean)
data class TransferConfig(val enabled: Boolean, val servers: List<Map<String, String>>)
data class Configuration(
    val heal: HealConfig,
    val unenchant: UnenchantConfig,
    val unsafeEnchants: UnsafeEnchantConfig,
    val transferConfig: TransferConfig
)
