package de.clientapi.clientsCustomWeapons.command;

import de.clientapi.clientsCustomWeapons.ClientsCustomWeapons;
import de.clientapi.clientsCustomWeapons.config.WeaponRegistry;
import de.clientapi.clientsCustomWeapons.config.WeaponSpec;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CcwCommand implements CommandExecutor, TabCompleter {

    private final ClientsCustomWeapons plugin;
    private final WeaponRegistry registry;

    public CcwCommand(ClientsCustomWeapons plugin, WeaponRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("ccw.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /ccw give <weaponId> [player] | /ccw reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                registry.reload();
                sender.sendMessage(ChatColor.GREEN + "weapons.yml reloaded.");
                return true;
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /ccw give <weaponId> [player]");
                    return true;
                }
                var spec = registry.get(args[1]);
                if (spec.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Unknown weapon id: " + args[1]);
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player.");
                        return true;
                    }
                    target = p;
                }
                var stack = registry.buildItem(spec.get());
                var left = target.getInventory().addItem(stack);
                if (!left.isEmpty()) target.getWorld().dropItemNaturally(target.getLocation(), stack);
                sender.sendMessage(ChatColor.GREEN + "Gave " + spec.get().id() + " to " + target.getName());
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        var out = new ArrayList<String>();
        if (args.length == 1) { out.add("give"); out.add("reload"); }
        else if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            out.addAll(registry.all().stream().map(WeaponSpec::id).collect(Collectors.toList()));
        } else if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            out.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return out;
    }
}
