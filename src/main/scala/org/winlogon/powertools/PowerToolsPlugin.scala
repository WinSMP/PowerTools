package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.{IntegerArgument, PlayerArgument, GreedyStringArgument, StringArgument, EnchantmentArgument}
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

case class UnenchantConfig(basePrice: Double)
case class UnsafeEnchantConfig(enabled: Boolean)
case class HealConfig(removeEffects: Boolean, showWhoHealed: Boolean)
case class Configuration(
  heal: HealConfig,
  unenchant: UnenchantConfig,
  unsafeEnchants: UnsafeEnchantConfig
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

     val yamlConfig = getConfig()

     val removeEffects = yamlConfig.getBoolean("heal.remove-effects", true)
     val showWhoHealed = yamlConfig.getBoolean("heal.show-who-healed", false)
     val unenchantBasePrice = yamlConfig.getDouble("unenchant.base-price", 5.0)
     val unsafeEnchantsEnabled = yamlConfig.getBoolean("unsafe-enchants.enabled", true)

     Configuration(
       HealConfig(removeEffects, showWhoHealed),
       UnenchantConfig(unenchantBasePrice),
       UnsafeEnchantConfig(unsafeEnchantsEnabled)
     )
   }

  private def registerCommands(): Unit = {
    // Broadcast Command
    CommandAPICommand("broadcast")
      .withAliases("bc")
      .withPermission("powertools.broadcast")
      .withArguments(GreedyStringArgument("message"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val message = args.get("message").asInstanceOf[String]
        executeBroadcast(sender, message)
        1
      })
      .register()

    // Hat Command
    CommandAPICommand("hat")
      .withPermission("powertools.hat")
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeHat(player)
        1
      })
      .register()

    // Invsee Command
    CommandAPICommand("invsee")
      .withPermission("powertools.invsee")
      .withArguments(PlayerArgument("target"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[Player].getName
        executeInvsee(player, target)
        1
      })
      .register()

    // Smite Command
    CommandAPICommand("smite")
      .withPermission("powertools.smite")
      .withArguments(PlayerArgument("target"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[Player].getName
        executeSmite(sender, target)
        1
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
              1
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
              1
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
            val targetName = args.get("player").asInstanceOf[String]
            player.sendMessage(fmt(whitelistListener.handleRequest(player, targetName)))
            1
          })
      )
      .withSubcommand(
        CommandAPICommand("list")
          .withPermission("whitelist.manage")
          .executesPlayer((player: Player, args: CommandArguments) => {
            whitelistListener.listRequests(player).foreach(msg => player.sendMessage(fmt(msg)))
            1
          })
      )
      .withSubcommand(
        CommandAPICommand("accept")
          .withPermission("whitelist.manage")
          .withArguments(StringArgument("target"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val target = args.get("target").asInstanceOf[String]
            player.sendMessage(fmt(whitelistListener.acceptRequest(player, target)))
            1
          })
      )
      .withSubcommand(
        CommandAPICommand("refuse")
          .withPermission("whitelist.manage")
          .withArguments(StringArgument("requester"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val requester = args.get("requester").asInstanceOf[String]
            player.sendMessage(fmt(whitelistListener.refuseRequest(player, requester)))
            1
          })
      )
      .register()

    // Unenchant Command
    CommandAPICommand("splitenchants")
      .withPermission("powertools.splitenchants")
      .withAliases("split", "unenchant")
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeSplitUnenchant(player)
        1
      })
      .register()

    // Unsafe enchant command
    // TODO: add EnchantmentArgument instead of StringArgument for the enchantment
    CommandAPICommand("enchantunsafe")
      .withPermission("powertools.unsafe-enchants")
      .withAliases("ue", "uenchant")
      .withArguments(EnchantmentArgument("enchantment"), IntegerArgument("level"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeUnsafeEnchant(player, args)
        1
      })
      .register()

      // Heal command
      CommandAPICommand("heal")
        .withPermission("heal.admin")
        .withAliases("h")
        .withArguments(PlayerArgument("player").setOptional(true))
        .executes((sender: CommandSender, args: CommandArguments) => {
          val maybeTarget = Option(args.get("player").asInstanceOf[Player]).orElse {
            if (sender.isInstanceOf[Player])
              Some(sender.asInstanceOf[Player])
            else {
              sendError(sender, "You must specify a player when using this command from console.")
              None
            }
          }
          
          maybeTarget match {
            case Some(target) =>
              healPlayer(target, sender)
              1
            case None =>
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

    // Get the item in the player's main hand
    val item = player.getInventory.getItemInMainHand
    if (item == null || item.getType == Material.AIR) {
      sendError(player, "You must be holding an item to enchant.")
      return
    }

    item.addUnsafeEnchantment(enchantment, level)
    player.updateInventory()

    val serializer = PlainTextComponentSerializer.plainText()
    val displayName = serializer.serialize(enchantment.displayName(1))
      .split(" ")
      .dropRight(1)
      .mkString(" ")

    player.sendMessage(fmt(s"&8[&5UE&8] <gray>Applied <dark_aqua>$displayName</dark_aqua> at level <dark_green>$level</dark_green>."))
  }

  private def executeSplitUnenchant(player: Player): Unit = {
    val inventory = player.getInventory
    val itemInHand = inventory.getItemInMainHand

    if (itemInHand == null || itemInHand == Material.AIR) {
      sendError(player, "You must be holding an item.")
      return
    }

    val enchantments = itemInHand.getEnchantments
    if (enchantments.isEmpty) {
      sendError(player, "This item has no enchantments to split.")
      return
    }

    val basePrice = config.unenchant.basePrice
    val enchantCount = enchantments.size
    val cost = (basePrice * enchantCount).toInt

    if (player.getTotalExperience < cost) {
      sendError(player, s"You need at least $cost XP to split these enchantments.")
      return
    }

    player.giveExp(-cost)

    val enchantmentsToSplit = enchantments.keySet.toArray(new Array[Enchantment](enchantCount))
    val meta = itemInHand.getItemMeta

    enchantmentsToSplit.foreach { ench =>
      meta.removeEnchant(ench)
    }
    itemInHand.setItemMeta(meta)

    // for each enchantment, create an enchanted book.
    enchantmentsToSplit.foreach { ench =>
      val level = enchantments.get(ench)
      val book = ItemStack(Material.ENCHANTED_BOOK)
      val bookMeta = book.getItemMeta.asInstanceOf[EnchantmentStorageMeta]

      bookMeta.addStoredEnchant(ench, level, true)
      book.setItemMeta(bookMeta)

      // Try to add the book to the player's inventory; if full, drop at player's location.
      if (inventory.firstEmpty() == -1) {
        player.getWorld.dropItemNaturally(player.getLocation, book)
      } else {
        inventory.addItem(book)
      }
    }

    player.updateInventory()
    player.sendMessage(fmt(s"&7Successfully split &3${enchantCount} &7enchantment(s) for &2$cost XP."))
  }

  // Other command methods like executeBroadcast, executeHat, etc.
  private def executeBroadcast(sender: CommandSender, message: String): Unit = {
    val formattedMessage = fmt(s"<dark_gray>[<dark_aqua>Broadcast<dark_gray>]<reset> &7${message}")
    Bukkit.getOnlinePlayers.forEach(_.sendMessage(formattedMessage))
  }

  private def executeSudoCommand(sender: CommandSender, target: Player, command: String): Unit = {
    if (target == null || !target.isOnline) {
      sender.sendMessage(fmt("<#F93822>Error&7: Player not found or offline."))
      return
    }

    val scheduler = target.getScheduler
    scheduler.execute(this, () => {
      // TODO: check if player actually has permissions to run this command
      // and use a better way to do this in the future (Player#performCommand doesn't work)
      target.chat(s"/${command.trim()}")
    }, null, 0L)
  }

  private def executeSudoChat(sender: CommandSender, target: Player, message: String): Unit = {
    if (target == null || !target.isOnline) {
      sendError(sender, "Player not found or offline.")
      return
    }

    val players = Bukkit.getOnlinePlayers()
    val viewers = java.util.HashSet[Audience](players)
    val userMsg = fmt(message)
    val event = AsyncChatEvent(false, target, viewers, ChatRenderer.defaultRenderer(), userMsg, userMsg, null)

    val isEventCalled = event.callEvent()

    if (isEventCalled && !event.isCancelled) {
      val renderer = event.renderer()
      val senderComponent = event.getPlayer.displayName()
      val renderedMessage = renderer.render(event.getPlayer, senderComponent, event.message(), Audience.audience(players))
      
      event.viewers().forEach { audience =>
        audience.sendMessage(renderedMessage)
      }
    }
  }

  private def executeHat(player: Player): Unit = {
    val playerInventory = player.getInventory
    val itemInHand = playerInventory.getItemInMainHand
    val helmetItem = playerInventory.getHelmet

    playerInventory.setHelmet(itemInHand)
    if (helmetItem == null) {
      playerInventory.setItemInMainHand(null)
    } else {
      playerInventory.setItemInMainHand(helmetItem)
      player.sendMessage(fmt("&7Swapping items..."))
    }

    player.updateInventory()
    player.sendMessage(fmt("&7Your held item is now &3on your head!"))
  }

  private def executeInvsee(player: Player, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      sendError(player, "Player not found or offline.")
    } else {
      player.openInventory(target.getInventory)
    }
  }

  private def executeSmite(sender: CommandSender, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      sendError(sender, "Player not found or offline.")
      return
    }

    target.getWorld.strikeLightning(target.getLocation)
    sender.sendMessage(fmt(s"&7You have smitten &3${target.getName}!"))
    target.sendMessage(fmt("&7You have been smitten by <b>&3a mighty force!"))
  }

  private def healPlayer(player: Player, sender: CommandSender): Unit = {
    val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)
    val genericHealMessage = fmt(s"&7You have been &3healed.")
    if (maxHealth != null) {
      player.setHealth(maxHealth.getValue())
    }
    player.setFoodLevel(20)
    player.setSaturation(20f)
    
    if (config.heal.removeEffects) {
      player.getActivePotionEffects().forEach(effect => player.removePotionEffect(effect.getType()))
    }
    
    if (!player.equals(sender)) {
      sender.sendMessage(fmt(s"&3${player.getName()}&7 has been healed."))
      val messageString = if (config.heal.showWhoHealed) {
        fmt(s"&7You have been healed by &3${sender.getName()}.")
      } else {
        genericHealMessage
      }
      player.sendMessage(messageString)
    }

    sender.sendMessage(genericHealMessage)
  }

  /**
    * Format a string converting legacy colors to MiniMessage

    * @param s The string to format
    * @return The formatted string, as a Component
    */
  private def fmt(s: String): Component = ChatFormatting.apply(s)
  private def sendError(p: Player | CommandSender, err: String): Unit = p.sendMessage(fmt(s"<#F93822>Error&7: ${err}"))
}
