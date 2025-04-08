package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.{
    EnchantmentArgument, GreedyStringArgument,
    IntegerArgument, PlayerArgument, StringArgument
}
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor}

import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Material}

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}

case class UnenchantConfig(basePrice: Double)
case class UnsafeEnchantConfig(enabled: Boolean)
case class HealConfig(removeEffects: Boolean, showWhoHealed: Boolean)
case class Configuration(
    heal: HealConfig,
    unenchant: UnenchantConfig,
    unsafeEnchants: UnsafeEnchantConfig,
)

class PowerToolsPlugin extends JavaPlugin {
    private val whitelistListener = WhitelistListener()
    private var config: Configuration = _

    override def onEnable(): Unit = {
        config = loadConfig()
        registerCommands()
        getServer.getPluginManager.registerEvents(whitelistListener, this)
    }

    private def loadConfig(): Configuration = {
        saveDefaultConfig()
        reloadConfig()
        val yamlConfig = getConfig

        val healCfg = HealConfig(
            removeEffects = yamlConfig.getBoolean("heal.remove-effects", true),
            showWhoHealed  = yamlConfig.getBoolean("heal.show-who-healed", false)
        )
        val unenchantCfg = UnenchantConfig(yamlConfig.getDouble("unenchant.base-price", 5.0))
        val unsafeCfg = UnsafeEnchantConfig(yamlConfig.getBoolean("unsafe-enchants.enabled", true))

        Configuration(healCfg, unenchantCfg, unsafeCfg)
    }

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

        // Hat Command
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
                    sendError(player, "You cannot invsee yourself.")
                }

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

        // Whitelist Request Command
        CommandAPICommand("whitelistrequest")
            .withPermission("powertools.whitelist")
            .withAliases("wlreq", "wlrequest")
            .withSubcommand(
                CommandAPICommand("request")
                    .withArguments(StringArgument("player"))
                    .executesPlayer((player: Player, args: CommandArguments) => {
                        val target = args.get("player").asInstanceOf[String]
                        whitelistListener.handleRequest(player, target)
                        successStatus
                    })
            )
            .withSubcommand(
                CommandAPICommand("list")
                    .withPermission("whitelist.manage")
                    .executesPlayer((player: Player, _: CommandArguments) => {
                        whitelistListener.listRequests(player).foreach(player.sendMessage)
                        successStatus
                    })
            )
            .withSubcommand(
                CommandAPICommand("accept")
                    .withPermission("whitelist.manage")
                    .withArguments(StringArgument("target"))
                    .executesPlayer((player: Player, args: CommandArguments) => {
                        val target = args.get("target").asInstanceOf[String]
                        whitelistListener.acceptRequest(player, target)
                        successStatus
                    })
            )
            .withSubcommand(
                CommandAPICommand("refuse")
                    .withPermission("whitelist.manage")
                    .withArguments(StringArgument("requester"))
                    .executesPlayer((player: Player, args: CommandArguments) => {
                        val requester = args.get("requester").asInstanceOf[String]
                        whitelistListener.refuseRequest(player, requester)
                        successStatus
                    })
            )
            .register()

        // Unenchant Command
        CommandAPICommand("splitenchants")
            .withPermission("powertools.splitenchants")
            .withAliases("split", "unenchant")
            .executesPlayer((player: Player, _: CommandArguments) => {
                executeSplitUnenchant(player)
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
                maybeTarget match {
                    case Some(target) =>
                        healPlayer(target, sender)
                        successStatus
                    case None =>
                        // Error already sent in case of console
                        0
                }
            })
            .register()
    }

    private def executeUnsafeEnchant(player: Player, args: CommandArguments): Unit = {
        if (!config.unsafeEnchants.enabled) {
            sendError(player, "Unsafe enchantments are disabled in config.")
            return
        }
        val enchantment = args.get("enchantment").asInstanceOf[Enchantment]
        val level = args.get("level").asInstanceOf[Integer].intValue()

        Option(player.getInventory.getItemInMainHand)
            .filter(item => item.getType != Material.AIR)
            .fold(sendError(player, "You must be holding an item to enchant.")) { item =>
                item.addUnsafeEnchantment(enchantment, level)
                player.updateInventory()
                val displayName = PlainTextComponentSerializer.plainText()
                    .serialize(enchantment.displayName(1))
                    .split(" ").dropRight(1).mkString(" ")

                val prefix = "&8[&5UE&8]"
                player.sendMessage(fmt(
                    s"$prefix <gray>Applied <dark_aqua>$displayName</dark_aqua> at level <dark_green>$level</dark_green>."
                ))
            }
    }

    private def executeSplitUnenchant(player: Player): Unit = {
        val inventory = player.getInventory
        Option(inventory.getItemInMainHand)
            .filterNot(item => item == null || item.getType == Material.AIR)
            .fold(sendError(player, "You must be holding an item.")) { itemInHand =>
                val enchantments = itemInHand.getEnchantments
                if (enchantments.isEmpty) {
                    sendError(player, "This item has no enchantments to split.")
                    return
                }
                val cost = (config.unenchant.basePrice * enchantments.size).toInt
                if (player.getTotalExperience < cost) {
                    sendError(player, s"You need at least $cost XP to split these enchantments.")
                    return
                }
                player.giveExp(-cost)

                // Remove all enchantments from the item meta.
                val meta = itemInHand.getItemMeta
                enchantments.keySet.asScala.foreach(meta.removeEnchant)
                itemInHand.setItemMeta(meta)

                // Create an enchanted book for each enchantment.
                enchantments.asScala.foreach { case (ench, level) =>
                    val book = new ItemStack(Material.ENCHANTED_BOOK)
                    val bookMeta = book.getItemMeta.asInstanceOf[EnchantmentStorageMeta]
                    bookMeta.addStoredEnchant(ench, level, true)
                    book.setItemMeta(bookMeta)
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
        val formattedMessage = fmt(s"<dark_gray>[<dark_aqua>Broadcast<dark_gray>]<reset> &7$message")
        Bukkit.getOnlinePlayers.forEach(_.sendMessage(formattedMessage))
    }

    private def executeSudoCommand(sender: CommandSender, target: Player, command: String): Unit = {
        if (target == null || !target.isOnline) {
            sender.sendMessage(fmt("<#F93822>Error&7: Player not found or offline."))
            return
        }
        // Use the scheduler to perform the command asynchronously.
        target.getScheduler.execute(this, () => target.chat(s"/${command.trim()}"), null, 0L)
    }

    private def executeSudoChat(sender: CommandSender, target: Player, message: String): Unit = {
        if (target == null || !target.isOnline) {
            sendError(sender, "Player not found or offline.")
            return
        }
        val players = Bukkit.getOnlinePlayers()
        val viewers = new java.util.HashSet[Audience](players)
        val userMsg = fmt(message)

        // `AsyncChatEvent`s called by plugins must be synchronous
        val event = AsyncChatEvent(false, target, viewers, ChatRenderer.defaultRenderer(), userMsg, userMsg, null)

        // If the event is not cancelled, send the message
        if (event.callEvent() && !event.isCancelled) {
            val renderer = event.renderer()
            val senderComponent = event.getPlayer.displayName()
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
                player.sendMessage(fmt("&7Swapping items..."))
            case None =>
                inv.setHelmet(hand)
                inv.setItemInMainHand(null)
        }
        player.updateInventory()
        player.sendMessage(fmt("&7Your held item is now &3on your head!"))
    }

    private def executeInvsee(player: Player, targetName: String): Unit = {
        Option(Bukkit.getPlayer(targetName))
            .filter(_.isOnline)
            .fold(sendError(player, "Player not found or offline."))(target => player.openInventory(target.getInventory))
    }

    private def executeSmite(sender: CommandSender, targetName: String): Unit = {
        Option(Bukkit.getPlayer(targetName))
            .filter(_.isOnline) // if the player is online and is not null
            // or else if he is, sendError, or else
            .fold(sendError(sender, "Player not found or offline.")) { target =>
                val result = Try {
                    target.getWorld.strikeLightning(target.getLocation)
                }

                result match {
                    case Success(_) =>
                        sender.sendMessage(fmt(s"&7You have smitten &3${target.getName}!"))
                        target.sendMessage(fmt("&7You have been smitten by <b>&3a mighty force!"))
                    case Failure(_) =>
                        sendError(sender, "Failed to smite player.")
                }
            }
    }

    private def healPlayer(player: Player, sender: CommandSender): Unit = {
        Option(player.getAttribute(Attribute.MAX_HEALTH)).foreach(max => player.setHealth(max.getValue()))
        player.setFoodLevel(20)
        player.setSaturation(20f)
        if (config.heal.removeEffects) {
            player.getActivePotionEffects.forEach(effect => player.removePotionEffect(effect.getType))
        }
        if (player != sender) {
            sender.sendMessage(fmt(s"&3${player.getName()}&7 has been healed."))
            val healMsg = if (config.heal.showWhoHealed)
                fmt(s"&7You have been healed by &3${sender.getName()}.")
            else fmt(s"&7You have been healed.")
            player.sendMessage(healMsg)
        }
        sender.sendMessage(fmt(s"&7You have been healed."))
    }

    /** Format a string converting legacy colors to MiniMessage */
    private def fmt(s: String): Component = ChatFormatting.apply(s)

    /** Send an error message to the player */
    private def sendError(target: Player | CommandSender, err: String): Unit =
        target.sendMessage(fmt(s"<#F93822>Error&7: $err"))
}
