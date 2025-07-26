package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.{
    EnchantmentArgument, GreedyStringArgument,
    IntegerArgument, PlayerArgument, StringArgument,
    ArgumentSuggestions
}
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor}

import org.bukkit.attribute.Attribute
import org.bukkit.command.{CommandSender, ConsoleCommandSender}
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Material}

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.event.player.AsyncChatEvent

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import scala.jdk.CollectionConverters.*
import scala.util.{Try, Success, Failure}
import scala.util.boundary
import scala.util.chaining.scalaUtilChainingOps
import org.bukkit.event.inventory.InventoryType

case class UnenchantConfig(basePrice: Double)
case class UnsafeEnchantConfig(enabled: Boolean)
case class HealConfig(removeEffects: Boolean, showWhoHealed: Boolean)
case class TransferConfig(enabled: Boolean, servers: List[java.util.HashMap[String, String]])
case class Configuration(
    heal: HealConfig,
    unenchant: UnenchantConfig,
    unsafeEnchants: UnsafeEnchantConfig,
    transferConfig: TransferConfig
)

class PowerToolsPlugin extends JavaPlugin {
    private var config: Configuration = _

    override def onEnable(): Unit = {
        config = loadConfig()
        registerCommands()
    }

    private def loadConfig(): Configuration = {
        saveDefaultConfig()
        reloadConfig()
        val yamlConfig = getConfig

        val healCfg = HealConfig(
            removeEffects = yamlConfig.getBoolean("heal.remove-effects", true),
            showWhoHealed = yamlConfig.getBoolean("heal.show-who-healed", false)
        )
        val unenchantCfg = UnenchantConfig(yamlConfig.getDouble("unenchant.base-price", 5.0))
        val unsafeCfg = UnsafeEnchantConfig(yamlConfig.getBoolean("unsafe-enchants.enabled", true))
        val transferCfg = TransferConfig(
            enabled = yamlConfig.getBoolean("transfer.enabled", true),
            servers = yamlConfig.getMapList("transfer.servers").asScala.map { configMap =>
                val map = new java.util.HashMap[String, String]()
                configMap.asScala.foreach { case (k, v) =>
                    map.put(k.toString, v.toString)
                }
                map
            }.toList
        )

        Configuration(healCfg, unenchantCfg, unsafeCfg, transferCfg)
    }

    // TODO: add user-facing /transfer command which is configurable
    private def registerCommands(): Unit = {
        // Helper to always return 1 as status.
        val successStatus: Int = 1

        // Broadcast Command
        CommandAPICommand("broadcast")
            .withAliases("bc")
            .withPermission("powertools.broadcast")
            .withArguments(GreedyStringArgument("message"))
            .executes((sender: CommandSender, args: CommandArguments) => {
                val message = args.get("message").asInstanceOf[String]
                executeBroadcast(sender, message)
                successStatus
            })
            .register()

        // Hat command
        CommandAPICommand("hat")
            .withPermission("powertools.hat")
            .executesPlayer((player: Player, _: CommandArguments) => {
                executeHat(player)
                successStatus
            })
            .register()

        // Invsee Command
        CommandAPICommand("invsee")
            .withPermission("powertools.invsee")
            .withArguments(PlayerArgument("target"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                val targetName = Option(args.get("target").asInstanceOf[Player]).map(_.getName)
                    .getOrElse("unknown")

                if (!targetName.equalsIgnoreCase(player.getName)) {
                    executeInvsee(player, targetName)
                } else {
                    sendError(player, "you cannot invsee yourself")
                }

                successStatus
            })
            .register()

        CommandAPICommand("absorb")
            .withPermission("powertools.absorb")
            .executesPlayer((player: Player, _: CommandArguments) => {
                // TODO: absorb player and check if they have required item in inv
                successStatus
            })
            .register()

        // Smite Command
        CommandAPICommand("smite")
            .withPermission("powertools.smite")
            .withArguments(PlayerArgument("target"))
            .executes((sender: CommandSender, args: CommandArguments) => {
                val player = args.get("target").asInstanceOf[Player]
                val targetName = Option(player).map(_.getName).getOrElse("unknown")
                executeSmite(sender, targetName)
                successStatus
            })
            .register()

        // Sudo command
        CommandAPICommand("sudo")
            .withAliases("doas")
            .withPermission("powertools.wheel")
            .withArguments(StringArgument("target"))
            .withSubcommand(
                CommandAPICommand("command")
                    .withAliases("cmd")
                    .withArguments(PlayerArgument("target"))
                    .withArguments(GreedyStringArgument("command"))
                    .executes((sender: CommandSender, args: CommandArguments) => {
                        val target = args.get("target").asInstanceOf[Player]
                        val command = args.get("command").asInstanceOf[String]
                        executeSudoCommand(sender, target, command)
                        successStatus
                    })
            )
            .withSubcommand(
                CommandAPICommand("chat")
                    .withArguments(PlayerArgument("target"))
                    .withArguments(GreedyStringArgument("message"))
                    .executes((sender: CommandSender, args: CommandArguments) => {
                        val target = args.get("target").asInstanceOf[Player]
                        val message = args.get("message").asInstanceOf[String]
                        executeSudoChat(sender, target, message)
                        successStatus
                    })
            )
            .register()

        CommandAPICommand("split")
            .withPermission("powertools.splitenchants")
            .withAliases("unenchant")
            .executesPlayer((player: Player, _: CommandArguments) => {
                executeSplitUnenchant(player)
                successStatus
            })
            .register()

        CommandAPICommand("fly")
            .withPermission("powertools.fly")
            .executesPlayer((player: Player, _: CommandArguments) => {
                val canFly = player.getAllowFlight()
                val toggledFly = !canFly

                val statusMessage = if (toggledFly) "enabled" else "disabled"
                val color = if (toggledFly) "dark_aqua" else "red"

                val playerName = s"<dark_green>${player.getName()}</dark_green>"
                player.setAllowFlight(toggledFly)
                player.sendRichMessage(s"<gray>Fly <$color>$statusMessage</$color> for $playerName.</gray>")
                successStatus
            })
            .register()

        // Unsafe enchant command
        CommandAPICommand("enchantunsafe")
            .withPermission("powertools.unsafe-enchants")
            .withAliases("ue", "uenchant")
            .withArguments(EnchantmentArgument("enchantment"), IntegerArgument("level"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                executeUnsafeEnchant(player, args)
                successStatus
            })
            .register()

        // Heal command
        CommandAPICommand("heal")
            .withPermission("heal.admin")
            .withAliases("h")
            .withArguments(PlayerArgument("player").setOptional(true))
            .executes((sender: CommandSender, args: CommandArguments) => {
                val maybeTarget = Option(args.get("player").asInstanceOf[Player])
                    .orElse(Option(sender).collect { case p: Player => p })
                maybeTarget.foreach(target => healPlayer(target, sender))
                successStatus
            })
            .register()

        registerTransferCommandsIfEnabled(config.transferConfig.enabled)
    }
    private def executeUnsafeEnchant(player: Player, args: CommandArguments): Unit = {
        if (!config.unsafeEnchants.enabled) {
            sendError(player, "unsafe enchantments are disabled in config")
            return
        }
        val enchantment = args.get("enchantment").asInstanceOf[Enchantment]
        val level = args.get("level").asInstanceOf[Integer].intValue()

        Option(player.getInventory.getItemInMainHand)
            .filter(item => item.getType != Material.AIR)
            .fold(sendError(player, "you must be holding an item to enchant")) { item =>
                item.addUnsafeEnchantment(enchantment, level)
                player.updateInventory()

                val displayName = PlainTextComponentSerializer.plainText()
                    .serialize(enchantment.displayName(1))

                val romanNumeralRegex = """^(?=.)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$""".r

                val finalDisplayName = {
                    val trimmed = displayName.trim
                    val words = trimmed.split("\\s+")
                    if (words.nonEmpty && romanNumeralRegex.pattern.matcher(words.last).matches()) {
                          words.dropRight(1).mkString(" ")
                    } else {
                        displayName
                    }
                }

                val prefix = "&8[&5UE&8]"
                player.sendMessage(fmt(
                    s"$prefix <gray>Applied <dark_aqua>$finalDisplayName</dark_aqua> at level <dark_green>$level</dark_green>."
                ))
            }
    }

    private def executeSplitUnenchant(player: Player): Unit = {
        val inventory = player.getInventory
        val itemHand = Option(inventory.getItemInMainHand)

        itemHand
            .filter(_.getType == Material.ENCHANTED_BOOK)
            .foreach { _ =>
                sendError(player, "the item can't be an enchanted book")
                return
            }

        itemHand
            .filterNot(item => item == null || item.getType == Material.AIR)
            .fold(sendError(player, "you must be holding an item")) { itemInHand =>
                val enchantments = itemInHand.getEnchantments
                if (enchantments.isEmpty) {
                    sendError(player, "this item has no enchantments to split")
                    return
                }
                val cost = (config.unenchant.basePrice * enchantments.size).toInt
                if (player.getTotalExperience < cost) {
                    sendError(player, s"you need at least $cost XP to split these enchantments")
                    return
                }
                player.giveExp(-cost)

                // Remove all enchantments from the item meta.
                val meta = itemInHand.getItemMeta
                enchantments.keySet.asScala.foreach(meta.removeEnchant)
                itemInHand.setItemMeta(meta)
                itemInHand.resetData(DataComponentTypes.REPAIR_COST)

                // Create an enchanted book for each enchantment.
                enchantments.asScala.foreach { case (ench, level) =>
                    val book = new ItemStack(Material.ENCHANTED_BOOK)
                    val bookMeta = book.getItemMeta.asInstanceOf[EnchantmentStorageMeta]
                    bookMeta.addStoredEnchant(ench, level, true)
                    book.setItemMeta(bookMeta)
                    // If the inventory is full, drop it at the player's position
                    if (inventory.firstEmpty() == -1)
                        player.getWorld.dropItemNaturally(player.getLocation, book)
                    else
                        inventory.addItem(book)
                }
                player.updateInventory()
                player.sendMessage(fmt(s"&7Successfully split &3${enchantments.size} &7enchantment(s) for &2$cost XP."))
            }
    }

    private def executeBroadcast(sender: CommandSender, message: String): Unit = {
        val formattedMessage = "<dark_gray>[<dark_aqua>Broadcast<dark_gray>] <message>"
        val messageComponent = Placeholder.component("message", Component.text(message, NamedTextColor.GRAY))

        Bukkit.getOnlinePlayers.forEach(_.sendRichMessage(formattedMessage, messageComponent))
        // Send message to console if the command was sent from console for user feedback
        if (sender.isInstanceOf[ConsoleCommandSender]) {
            sender.sendRichMessage(formattedMessage, messageComponent)
        }
    }

    private def executeSudoCommand(sender: CommandSender, target: Player, command: String): Unit = {
        if (target == null || !target.isOnline) {
            sendError(sender, "player not found or offline")
            return
        }
        // Use the scheduler to perform the command asynchronously.
        target.getScheduler.execute(this, () => target.chat(s"/${command.trim()}"), null, 0L)
    }

    private def executeSudoChat(sender: CommandSender, target: Player, message: String): Unit = {
        if (target == null || !target.isOnline) {
            sendError(sender, "player not found or offline")
            return
        }
        val players = Bukkit.getOnlinePlayers()

        val consoleSenderAudience = Audience.audience(Bukkit.getConsoleSender())
        val viewers = new java.util.HashSet[Audience](players)
        viewers.add(consoleSenderAudience)

        val userMsg = fmt(message)

        // `AsyncChatEvent`s called by plugins must be synchronous
        val event = AsyncChatEvent(false, target, viewers, ChatRenderer.defaultRenderer(), userMsg, userMsg, null)

        // If the event is not cancelled, send the message
        if (event.callEvent() && !event.isCancelled) {
            val renderer = event.renderer()
            val senderComponent = event.getPlayer.displayName()
            // Create an audience from the players and craft a new rendered message
            val renderedMessage = renderer.render(event.getPlayer, senderComponent, event.message(), Audience.audience(players))
            event.viewers().forEach(_.sendMessage(renderedMessage))
        }
    }

    private def executeHat(player: Player): Unit = {
        val inv = player.getInventory
        val hand = inv.getItemInMainHand
        val helmet = Option(inv.getHelmet)
        helmet match {
            case Some(h) =>
                inv.setHelmet(hand)
                inv.setItemInMainHand(h)
                player.sendRichMessage("<gray>Swapping items...</gray>")
            case None =>
                inv.setHelmet(hand)
                inv.setItemInMainHand(null)
        }
        player.updateInventory()
        player.sendRichMessage("<gray>Your held item is now <dark_aqua>on your head</dark_aqua>!")
    }

    private def executeInvsee(player: Player, targetName: String): Unit = {
        Option(Bukkit.getPlayer(targetName))
            .filter(_.isOnline)
            .fold(sendError(player, "player not found or offline"))(target => {
                var inventorySize = 45
                val targetInventory = target.getInventory

                if (targetInventory.getSize() > inventorySize) {
                    inventorySize = targetInventory.getSize()
                }

                // FIXME: you originally could open a player's inventory normally but for some
                // reason, between 1.21.5 and 1.21.6 you can't do it anymore.
                val wrapperInventory = Bukkit.createInventory(null, inventorySize)
                wrapperInventory.setContents(targetInventory.getContents)
                player.openInventory(wrapperInventory)

                // TODO: since you can't open a player inventory as before, the changes need
                // to be written back to the target's inventory
            })
    }

    private def executeSmite(sender: CommandSender, targetName: String): Unit = {
        Option(Bukkit.getPlayer(targetName))
            .filter(_.isOnline)
            .fold(sendError(sender, "player not found or offline")) { target =>
                val result = Try {
                    target.getWorld.strikeLightning(target.getLocation)
                }

                result match {
                    case Success(_) =>
                        sender.sendMessage(fmt(s"&7You have smitten &3${target.getName}!"))
                        target.sendMessage(fmt("&7You have been smitten by <b>&3a mighty force&7!"))
                    case Failure(_) =>
                        sendError(sender, "failed to smite player")
                }
            }
    }

    private def healPlayer(player: Player, sender: CommandSender): Unit = {
        Option(player.getAttribute(Attribute.MAX_HEALTH))
            .foreach(max => player.setHealth(max.getValue()))
        player.setFoodLevel(20)
        player.setSaturation(20f)

        if (config.heal.removeEffects) {
            player.getActivePotionEffects.forEach(effect => player.removePotionEffect(effect.getType))
        }

        val healedMessage = "<gray>You have been <dark_aqua>healed</dark_aqua>.</gray>"

        if (player != sender) {
            sender.sendRichMessage(
              "<gray><player> has been healed.</gray>",
              Placeholder.component("player", Component.text(player.getName(), NamedTextColor.DARK_AQUA))
            )

            val senderComponent = Component.text(sender.getName(), NamedTextColor.DARK_AQUA)

            val healMsg = if (config.heal.showWhoHealed) {
                s"<gray>You have been healed by <staff>.</gray>"
            } else {
                healedMessage
            }

            player.sendRichMessage(healMsg, Placeholder.component("staff", senderComponent))
        } else {
          sender.sendRichMessage(healedMessage)
        }
    }

    // TODO: I'm not totally sure I remember what this is here for, is it for aliasing a
    // server IP? If yes then it should have another name.
    private def registerTransferCommandsIfEnabled(transferConfigEnabled: Boolean): Unit = {
        if (!transferConfigEnabled) {
            return
        }

        CommandAPICommand("transfer")
            .withPermission("powertools.transfer")
            .withArguments(new StringArgument("server").replaceSuggestions(ArgumentSuggestions.strings(info => {
                config.transferConfig.servers.filter { server =>
                    val allowPlayers = server.getOrDefault("allowPlayers", "false").toBoolean
                    allowPlayers || info.sender().hasPermission("powertools.transfer.all")
                }.map(_.get("name")).toArray
            })))
            .executesPlayer((player: Player, args: CommandArguments) => {
                val serverName = args.get("server").asInstanceOf[String]

                config.transferConfig.servers
                    .find(_.get("name") == serverName)
                    .fold(sendError(player, "server not found"))( server => {
                        val allowPlayers = server.getOrDefault("allowPlayers", "false").toBoolean

                        if (!(allowPlayers && player.hasPermission("powertools.transfer.all"))) {
                            sendError(player, "you don't have permission to transfer to this server")
                            return
                        }

                        try {
                            val host = server.get("host")
                            val port = server.get("port").toInt
                            player.transfer(host, port)
                        } catch {
                            case _: NumberFormatException =>
                                sendError(player, "invalid port for server! Please contact a server admin")
                            case ex: Exception =>
                                sendError(player, s"transfer failed: ${ex.getMessage}")
                        }
                    })
            })
            .register()
    }

    /** Format a string converting legacy colors to MiniMessage */
    private def fmt(s: String): Component = ChatFormatting.translateLegacyCodes(s)

    /** Send an error message to the player */
    private def sendError(target: Player | CommandSender, err: String): Unit =
        target.sendMessage(fmt(s"<#F93822>Error&7: ${sentenceCase(err)}"))

    private def sentenceCase(input: String): String = {
        input
            .trim
            .pipe { s =>
              if (s.isEmpty) s
              else s.head.toUpper + s.tail
            }
            .pipe { s =>
              if (s.endsWith(".")) s else s + "."
            }
    }
}
