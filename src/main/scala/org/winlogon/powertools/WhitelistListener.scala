package org.winlogon.powertools

import org.bukkit.{Bukkit, OfflinePlayer}
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component

import scala.collection.mutable

case class WhitelistRequest(requester: Player, target: OfflinePlayer)

object WhitelistManager {
    val pendingRequests: mutable.Map[String, WhitelistRequest] = mutable.Map.empty
}

// Extension methods for CommandSender to send formatted messages.
object Extensions {
    extension (sender: CommandSender) {
        def sendSuccessMessage(msg: String): Unit = {
            sender.sendMessage(ChatFormatting.apply(s"<#1DB989>Success&7: $msg"))
        }
        def sendFailureMessage(msg: String): Unit = {
            sender.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: $msg"))
        }
    }
}

import Extensions.*

class WhitelistListener extends Listener {
    private val redColor   = "#CE1126"
    private val greenColor = "#1DB989"
    private val errorColor = "#F93822"

    @EventHandler
    def onPlayerJoin(event: PlayerJoinEvent): Unit = {
        val player = event.getPlayer
        if (player.hasPermission("whitelist.manage") && WhitelistManager.pendingRequests.nonEmpty) {
            player.sendSuccessMessage(
                s"Hello! You have ${WhitelistManager.pendingRequests.size} pending whitelist request(s). Use /whitelistrequest list to review them."
            )
        }
    }

    def handleRequest(player: Player, targetName: String): Unit = {
        val target = Bukkit.getOfflinePlayer(targetName)
        WhitelistManager.pendingRequests.put(player.getName, WhitelistRequest(player, target))
        Bukkit.getOnlinePlayers.forEach { p =>
            if (p.hasPermission("whitelist.manage")) {
                p.sendMessage(ChatFormatting.apply(
                    s"<$redColor>New whitelist request:&7 ${player.getName} is requesting ${target.getName} to join. Use /whitelistrequest list to view."
                ))
            }
        }
        player.sendSuccessMessage(s"Whitelist request for ${target.getName} sent.")
    }

    def listRequests(player: Player): List[Component] = {
        if (WhitelistManager.pendingRequests.isEmpty) {
            List(ChatFormatting.apply(s"<$errorColor>No pending whitelist requests."))
        } else {
            val header = ChatFormatting.apply(s"<$greenColor>Whitelist requests:")
            val requests = WhitelistManager.pendingRequests.values.map { req =>
                ChatFormatting.apply(
                    s"- ${req.requester.getName} is asking for ${req.target.getName} " +
                    s"<click:run_command:'/whitelistrequest accept ${req.target.getName}'><$greenColor>[Accept]</$greenColor></click> " +
                    s"<click:run_command:'/whitelistrequest refuse ${req.requester.getName}'><$redColor>[Refuse]</$redColor></click>"
                )
            }.toList
            header :: requests
        }
    }

    def acceptRequest(player: Player, targetName: String): Unit = {
        val requests = WhitelistManager.pendingRequests.values
            .filter(req => req.target.getName.equalsIgnoreCase(targetName))
            .toList

        requests match {
            case Nil =>
                player.sendFailureMessage(s"No whitelist requests found for $targetName")
            case nonEmpty =>
                nonEmpty.foreach { req =>
                    req.target.setWhitelisted(true)
                }
                WhitelistManager.pendingRequests --= nonEmpty.map(_.requester.getName)
                nonEmpty.foreach { req =>
                    Option(Bukkit.getPlayer(req.requester.getName)) match {
                        case Some(p) =>
                            p.sendMessage(ChatFormatting.apply(s"&7Your whitelist request for &3${req.target.getName}&7 has been accepted."))
                        case None => // Do nothing if the requester is offline.
                    }
                }
                player.sendSuccessMessage(s"Whitelisted $targetName and removed ${nonEmpty.size} request(s)")
        }
    }

    def refuseRequest(player: Player, requesterName: String): Unit = {
        WhitelistManager.pendingRequests.get(requesterName) match {
            case Some(req) =>
                WhitelistManager.pendingRequests.remove(requesterName)
                Option(Bukkit.getPlayer(req.requester.getName)) match {
                    case Some(p) =>
                        p.sendMessage(ChatFormatting.apply(s"&7Your whitelist request for &3${req.target.getName}&7 has been refused."))
                    case None => // Do nothing if the requester is offline.
                }
                player.sendSuccessMessage(s"Refused whitelist request for ${req.target.getName} from ${req.requester.getName}")
            case None =>
                player.sendFailureMessage(s"No whitelist request found for $requesterName")
        }
    }
}
