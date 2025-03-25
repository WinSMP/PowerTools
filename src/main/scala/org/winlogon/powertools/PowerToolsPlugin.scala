package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.{IntegerArgument, PlayerArgument, GreedyStringArgument, StringArgument}
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor}
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Material}
import net.kyori.adventure.audience.Audience
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.text.Component

case class UnenchantConfig(basePrice: Double)
case class UnsafeEnchantConfig(enabled: Boolean)
case class HealConfig(removeEffects: Boolean, showWhoHealed: Boolean)
case class Configuration(
  heal: HealConfig,
  unenchant: UnenchantConfig,
  unsafeEnchants: UnsafeEnchantConfig
)

class PowerToolsPlugin extends JavaPlugin {
  private val whitelistListener = new WhitelistListener()
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
    new CommandAPICommand("broadcast")
      .withAliases("bc")
      .withPermission("powertools.broadcast")
      .withArguments(new GreedyStringArgument("message"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val message = args.get("message").asInstanceOf[String]
        executeBroadcast(sender, message)
        1
      })
      .register()

    // Hat Command
    new CommandAPICommand("hat")
      .withPermission("powertools.hat")
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeHat(player)
        1
      })
      .register()

    // Invsee Command
    new CommandAPICommand("invsee")
      .withPermission("powertools.invsee")
      .withArguments(new PlayerArgument("target"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[Player].getName
        executeInvsee(player, target)
        1
      })
      .register()

    // Smite Command
    new CommandAPICommand("smite")
      .withPermission("powertools.smite")
      .withArguments(new PlayerArgument("target"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[Player].getName
        executeSmite(sender, target)
        1
      })
      .register()

    // Sudo command
    new CommandAPICommand("sudo")
      .withAliases("doas")
      .withPermission("powertools.wheel")
      .withArguments(new StringArgument("target"))
      .withSubcommand(
          new CommandAPICommand("command")
            .withAliases("cmd")
            .withArguments(new PlayerArgument("target"))
            .withArguments(new GreedyStringArgument("command"))
            .executes((sender: CommandSender, args: CommandArguments) => {
              val target = args.get("target").asInstanceOf[Player]
              val command = args.get("command").asInstanceOf[String]
              executeSudoCommand(sender, target, command)
              1
            })
        )
      .withSubcommand(
          new CommandAPICommand("chat")
            .withArguments(new PlayerArgument("target"))
            .withArguments(new GreedyStringArgument("message"))
            .executes((sender: CommandSender, args: CommandArguments) => {
              val target = args.get("target").asInstanceOf[Player]
              val message = args.get("message").asInstanceOf[String]
              executeSudoChat(sender, target, message)
              1
            })
        )
      .register()

    // Whitelist Request Command
    new CommandAPICommand("whitelistrequest")
      .withPermission("powertools.whitelist")
      .withAliases("wlreq", "wlrequest")
      .withSubcommand(
        new CommandAPICommand("request")
          .withArguments(new StringArgument("player"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val targetName = args.get("player").asInstanceOf[String]
            player.sendMessage(ChatFormatting.apply(whitelistListener.handleRequest(player, targetName)))
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("list")
          .executesPlayer((player: Player, args: CommandArguments) => {
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to view whitelist requests."))
            } else {
              whitelistListener.listRequests(player).foreach(msg => player.sendMessage(ChatFormatting.apply(msg)))
            }
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("accept")
          .withArguments(new StringArgument("target"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val target = args.get("target").asInstanceOf[String]
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
            } else {
              player.sendMessage(ChatFormatting.apply(whitelistListener.acceptRequest(player, target)))
            }
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("refuse")
          .withArguments(new StringArgument("requester"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val requester = args.get("requester").asInstanceOf[String]
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
            } else {
              player.sendMessage(ChatFormatting.apply(whitelistListener.refuseRequest(player, requester)))
            }
            1
          })
      )
      .register()

    // Unenchant Command
    new CommandAPICommand("splitunenchants")
      .withPermission("powertools.splitunenchants")
      .withAliases("split", "unenchant")
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeSplitUnenchant(player)
        1
      })
      .register()

    // Unsafe enchant command
    new CommandAPICommand("unsafeenchants")
      .withPermission("powertools.unsafe-enchants")
      .withAliases("ue")
      .withArguments(new StringArgument("enchantment"), new IntegerArgument("level"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeUnsafeEnchant(player, args)
        1
      })
      .register()


    new CommandAPICommand("heal")
      .withAliases("h")
      .withPermission("heal.admin")
      .withArguments(new PlayerArgument("player").setOptional(true))
      .executes((player: Player, args: CommandArguments) -> {
        var target = args.get("player").asInstanceOf[Player];

        if (target == null) {
          if (sender instanceof Player) {
            target = sender.asInstanceOf[Player];
          } else {
            sender.sendMessage("§cYou must specify a player when using this command from console.");
            return;
          }
        }

        healPlayer(target, sender);
        1
      })
      .register();
  }

  private def executeUnsafeEnchant(player: Player, args: CommandArguments): Unit = {
    if (!config.unsafeEnchants.enabled) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Unsafe enchantments are disabled in config."))
      return
    }
    
    val enchantName = args.get("enchantment").asInstanceOf[String]
    val level = args.get("level").asInstanceOf[Integer].intValue()
    
    // Get the item in the player's main hand.
    val item = player.getInventory.getItemInMainHand
    if (item == null || item.getType == Material.AIR) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You must be holding an item to enchant."))
      return
    }
    
    // Try to fetch the Enchantment object using the provided name.
    val enchantment = Enchantment.getByName(enchantName.toUpperCase)
    if (enchantment == null) {
      player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: Invalid enchantment '$enchantName'."))
      return
    }
    
    // Apply the enchantment unsafely.
    item.addUnsafeEnchantment(enchantment, level)
    player.updateInventory()
    player.sendMessage(ChatFormatting.apply(s"&7Applied unsafe enchantment &3$enchantName &7at level &3$level &7to your item."))
  }

  private def executeSplitUnenchant(player: Player): Unit = {
    val inventory = player.getInventory
    val itemInHand = inventory.getItemInMainHand

    if (itemInHand == null || itemInHand == Material.AIR) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You must be holding an item."))
      return
    }

    val enchantments = itemInHand.getEnchantments
    if (enchantments.isEmpty) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: This item has no enchantments to split."))
      return
    }

    val basePrice = config.unenchant.basePrice
    val enchantCount = enchantments.size
    val cost = (basePrice * enchantCount).toInt

    if (player.getTotalExperience < cost) {
      player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: You need at least $cost XP to split these enchantments."))
      return
    }

    player.giveExp(-cost)

    // Remove each enchantment from the held item and create a corresponding book.
    // Note: We must work on a copy of the enchantments because we’ll be removing them.
    val enchantmentsToSplit = enchantments.keySet.toArray(new Array[Enchantment](enchantCount))
    val meta = itemInHand.getItemMeta

    enchantmentsToSplit.foreach { ench =>
      meta.removeEnchant(ench)
    }
    itemInHand.setItemMeta(meta)

    // for each enchantment, create an enchanted book.
    enchantmentsToSplit.foreach { ench =>
      val level = enchantments.get(ench)
      val book = new ItemStack(Material.ENCHANTED_BOOK)
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
    player.sendMessage(ChatFormatting.apply(s"&7Successfully split ${enchantCount} enchantment(s) for $cost XP."))
  }

  // Other command methods like executeBroadcast, executeHat, etc.
  private def executeBroadcast(sender: CommandSender, message: String): Unit = {
    val formattedMessage = ChatFormatting.apply(s"<dark_gray>[<dark_aqua>Broadcast<dark_gray>]<reset> &7${message}")
    Bukkit.getOnlinePlayers.forEach(_.sendMessage(formattedMessage))
  }

  private def executeSudoCommand(sender: CommandSender, target: Player, command: String): Unit = {
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
      return
    }

    val scheduler = target.getScheduler
    scheduler.execute(this, () => {
      // TODO: check if player actually has permissions to run this command
      // and use a better way to do this in the future (Player#performCommand doesn't work)
      target.chat(s"/${command}")
    }, null, 0L)
  }

  private def executeSudoChat(sender: CommandSender, target: Player, message: String): Unit = {
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
      return
    }

    val players = Bukkit.getOnlinePlayers()
    val viewers = new java.util.HashSet[Audience](players)
    val userMsg = ChatFormatting.apply(message)
    val event = new AsyncChatEvent(false, target, viewers, ChatRenderer.defaultRenderer(), userMsg, userMsg, null)

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
      player.sendMessage(ChatFormatting.apply("&7Swapping items..."))
    }

    player.updateInventory()
    player.sendMessage(ChatFormatting.apply("&7Your held item is now &3on your head!"))
  }

  private def executeInvsee(player: Player, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
    } else {
      player.openInventory(target.getInventory)
    }
  }

  private def executeSmite(sender: CommandSender, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error: &7Player not found or offline."))
      return
    }

    target.getWorld.strikeLightning(target.getLocation)
    sender.sendMessage(ChatFormatting.apply(s"&7You have smitten &3${target.getName}!"))
    target.sendMessage(ChatFormatting.apply("&7You have been smitten by <bold>&3a mighty force!"))
  }

  private def healPlayer(player: Player, sender: CommandSender): Unit = {
    val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)
    val genericHealMessage = s"§7You have been §3healed."
    if (maxHealth != null) {
      player.setHealth(maxHealth.getValue())
    }
    player.setFoodLevel(20)
    player.setSaturation(20f)
    
    if (config.heal.removeEffects) {
      player.getActivePotionEffects().forEach(player.removePotionEffect(_.getType()))
    }
    
    if (player.equals(sender)) {
        sender.sendMessage(genericHealMessage)
    } else {
      sender.sendMessage(s"§7${player.getName()}§3 has been healed.")
      val messageString = if (config.heal.showWhoHealed) {
        s"§7You have been healed by §3${sender.getName()}."
      } else {
        genericHealMessage
      }
      player.sendMessage(messageString)
    }
  }
}
