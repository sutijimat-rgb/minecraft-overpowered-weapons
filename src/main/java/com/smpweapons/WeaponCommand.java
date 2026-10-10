package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WeaponCommand implements TabExecutor {

    private final SmpWeaponsPlugin plugin;
    private final WeaponItems items;

    public WeaponCommand(SmpWeaponsPlugin plugin, WeaponItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <give|kit|reload> [mace|spear] [player]", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("SmpWeapons config reloaded.", NamedTextColor.GREEN));
            }
            case "give" -> give(sender, label, args);
            case "kit" -> kit(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void kit(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return;
        }
        KitItems.give(target);
        sender.sendMessage(Component.text("Gave the maxed diamond kit to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " give <mace|spear> [player]", NamedTextColor.YELLOW));
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return;
        }

        ItemStack item;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "mace" -> item = items.createMace();
            case "spear" -> {
                item = items.createSpear();
                if (item == null) {
                    sender.sendMessage(Component.text(
                            "Spear material '" + plugin.getConfig().getString("spear.material")
                                    + "' does not exist on this server. Check spear.material in config.yml.",
                            NamedTextColor.RED));
                    return;
                }
            }
            default -> {
                sender.sendMessage(Component.text("Weapon must be mace or spear.", NamedTextColor.RED));
                return;
            }
        }

        var leftover = target.getInventory().addItem(item);
        leftover.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        sender.sendMessage(Component.text("Gave " + args[1].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "kit", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("mace", "spear"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
