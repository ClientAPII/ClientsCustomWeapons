package de.clientapi.clientsCustomWeapons.command;

import de.clientapi.clientsCustomWeapons.ClientsCustomWeapons;
import de.clientapi.clientsCustomWeapons.config.WeaponRegistry;
import de.clientapi.clientsCustomWeapons.config.WeaponSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CcwCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private static final Component PREFIX = MM.deserialize(
            "<dark_gray><b>[<gradient:#4F679B:#4745D9>CCW</gradient>]</b> <gray>» </gray>"
    );

    private final ClientsCustomWeapons plugin;
    private final WeaponRegistry registry;

    public CcwCommand(ClientsCustomWeapons plugin, WeaponRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("ccw.admin")) {
            send(sender, Component.text("Keine Berechtigung.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            send(sender, Component.text("Verwendung: /ccw give <weaponId> [player] | /ccw reload", NamedTextColor.GRAY));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                registry.reload();
                send(sender, Component.text("weapons.yml neu geladen.", NamedTextColor.GREEN));
                return true;
            }

            case "give" -> {
                if (args.length < 2) {
                    send(sender, Component.text("Verwendung: /ccw give <weaponId> [player]", NamedTextColor.GRAY));
                    return true;
                }

                var specOpt = registry.get(args[1]);
                if (specOpt.isEmpty()) {
                    send(sender, Component.text("Unbekannte Waffen-ID: ", NamedTextColor.RED)
                            .append(Component.text(args[1], NamedTextColor.WHITE)));
                    return true;
                }

                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        send(sender, Component.text("Spieler nicht gefunden: ", NamedTextColor.RED)
                                .append(Component.text(args[2], NamedTextColor.WHITE)));
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player p)) {
                        send(sender, Component.text("Konsole muss einen Spieler angeben.", NamedTextColor.RED));
                        return true;
                    }
                    target = p;
                }

                var spec = specOpt.get();
                var stack = registry.buildItem(spec);
                var leftovers = target.getInventory().addItem(stack);
                if (!leftovers.isEmpty()) target.getWorld().dropItemNaturally(target.getLocation(), stack);

                Component weaponNameColored = AMP.deserialize(spec.name());
                send(sender,
                        Component.text("Waffe ", NamedTextColor.GRAY)
                                .append(weaponNameColored)
                                .append(Component.text(" an ", NamedTextColor.GRAY))
                                .append(Component.text(target.getName(), NamedTextColor.GRAY))
                                .append(Component.text(" gegeben.", NamedTextColor.GRAY))
                );
                return true;
            }

            default -> {
                send(sender, Component.text("Unbekannter Unterbefehl.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private void send(CommandSender to, Component messageGrayOrColored) {
        Component content = messageGrayOrColored.colorIfAbsent(NamedTextColor.GRAY);
        to.sendMessage(PREFIX.append(content));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        var out = new ArrayList<String>();
        if (args.length == 1) {
            out.add("give");
            out.add("reload");
        } else if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            out.addAll(registry.all().stream().map(WeaponSpec::id).collect(Collectors.toList()));
        } else if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            out.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return out;
    }
}
