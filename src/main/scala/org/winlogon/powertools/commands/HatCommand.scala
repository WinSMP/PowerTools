package org.winlogon.powertools.commands

import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.winlogon.powertools.ChatFormatting

class HatCommand extends CommandExecutor {
  override def onCommand(
    sender: CommandSender, command: Command, label: String, args: Array[String]
  ): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: This command can only be used by players."))
      return true
    }
    val player = sender.asInstanceOf[Player]
    val playerInventory = player.getInventory()

    val itemInHand = playerInventory.getItemInMainHand()
    val helmetItem = playerInventory.getHelmet()

    val isHelmetEmpty = helmetItem == null

    playerInventory.setHelmet(itemInHand)
    if (isHelmetEmpty) {
      playerInventory.setItemInMainHand(null)
    } else {
      playerInventory.setItemInHand(helmetItem)
      player.sendMessage(ChatFormatting.apply("&7Swapping items..."))
    }

    player.updateInventory()
    player.sendMessage(ChatFormatting.apply("&7Your held item is now &3on your head!"))
    return true
  }
}
