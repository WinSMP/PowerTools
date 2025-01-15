import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.permissions.Permission;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("invsee").setExecutor(new InvseeCommand());
        getCommand("invsee").setPermission("myplugin.invsee");
    }
}

public class InvseeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Check if the player has provided a target player's name
        if (args.length < 1) {
            sender.sendMessage("Please specify a player's name.");
            return true;
        }

        // Get the target player
        Player target = Bukkit.getPlayer(args[0]);

        // Check if the target player is online
        if (target == null) {
            sender.sendMessage("Player not found or offline.");
            return true;
        }

        // Open the target player's inventory
        ((Player) sender).openInventory(target.getInventory());

        return true;
    }
}

