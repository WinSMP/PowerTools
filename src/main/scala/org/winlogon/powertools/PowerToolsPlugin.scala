package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.{IntegerArgument, PlayerArgument, GreedyStringArgument, StringArgument}
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor}
import org.bukkit.command.CommandSender
import org.bukkit.{Bukkit, Material}
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import net.kyori.adventure.audience.Audience
import io.papermc.paper.event.player.AsyncChatEvent

class PowerToolsPlugin extends JavaPlugin {
  private val whitelistListener = new WhitelistListener()

  override def onEnable(): Unit = {
    registerCommands()
    getServer.getPluginManager.registerEvents(whitelistListener, this)
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
      .withAliases(Seq("split", "unenchant"): _*)
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeSplitUnenchant(player)
        1
      })
      .register()

    // Unsafe enchant command
    new CommandAPICommand("unsafeenchants")
      .withPermission("powertools.unsafe-enchants")
      .withArguments(new StringArgument("enchantment"), new IntegerArgument("level"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeUnsafeEnchant(player, args)
        1
      })
      .register()
  }

  private def executeUnsafeEnchant(player: Player, args: CommandArguments): Unit = {
    if (!getConfig.getBoolean("unsafe-enchants.enabled", true)) {
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

    if (itemInHand == null) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You must be holding an item."))
      return
    }

    // Get current enchantments on the item
    val enchantments = itemInHand.getEnchantments
    if (enchantments.isEmpty) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: This item has no enchantments to split."))
      return
    }

    // Calculate cost (base price from config multiplied by number of enchantments)
    val basePrice = getConfig.getDouble("unenchant.basePrice", 5.0)
    val enchantCount = enchantments.size
    // For simplicity, we assume cost is an integer value.
    val cost = (basePrice * enchantCount).toInt

    if (player.getTotalExperience < cost) {
      player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: You need at least $cost XP to split these enchantments."))
      return
    }

    // Deduct the XP cost.
    player.giveExp(-cost)

    // Remove each enchantment from the held item and create a corresponding book.
    // Note: We must work on a copy of the enchantments because we’ll be removing them.
    val enchantmentsToSplit = enchantments.keySet.toArray(new Array[Enchantment](enchantCount))
    val meta = itemInHand.getItemMeta
    // Remove all enchantments from the item.
    enchantmentsToSplit.foreach { ench =>
      meta.removeEnchant(ench)
    }
    itemInHand.setItemMeta(meta)

    // For each enchantment, create an enchanted book.
    enchantmentsToSplit.foreach { ench =>
      val level = enchantments.get(ench)
      val book = new ItemStack(Material.ENCHANTED_BOOK)
      val bookMeta = book.getItemMeta.asInstanceOf[EnchantmentStorageMeta]
      // The third parameter "true" allows unsafe enchantments if needed.
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
    val targetPlayer = target
    if (targetPlayer == null || !targetPlayer.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
    } else {
      targetPlayer.getScheduler().execute(this, () => {
        // why does this fail when user doesn't have * permissions
        // when running non-minecraft commands but commandapi-registered commands
        val result = targetPlayer.performCommand(command)
        if (!result) {
          sender.sendMessage("command execution failed")
        }
      }, null, 0L)
    }
  }

  private def executeSudoChat(sender: CommandSender, target: Player, message: String): Unit = {
    val targetPlayer = target
    if (targetPlayer == null || !targetPlayer.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
    } else {
      val viewers = new java.util.HashSet[Audience](Bukkit.getOnlinePlayers())
      val event = new AsyncChatEvent(true, targetPlayer, viewers, null, ChatFormatting.apply(message), null, null)
      event.callEvent()
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
}
