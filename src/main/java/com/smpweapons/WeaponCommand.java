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
    private final WeaponRegistry registry;
    private final WeaponLimiter limiter;
    private final SiftItems sift;
    private final RiftManager rifts;
    private final SiftTrader trader;
    private final TierItems tiers;
    private final HeartManager hearts;
    private final SmpListener smp;
    private final SiftMobs mobs;

    public WeaponCommand(SmpWeaponsPlugin plugin, WeaponItems items, WeaponRegistry registry, WeaponLimiter limiter,
                         SiftItems sift, RiftManager rifts, SiftTrader trader, TierItems tiers,
                         HeartManager hearts, SmpListener smp, SiftMobs mobs) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
        this.limiter = limiter;
        this.sift = sift;
        this.rifts = rifts;
        this.trader = trader;
        this.tiers = tiers;
        this.hearts = hearts;
        this.smp = smp;
        this.mobs = mobs;
    }

    private void giveAll(Player target, java.util.List<ItemStack> list) {
        for (ItemStack item : list) {
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
    }

    private Player playerArg(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) sender.sendMessage(Component.text("Player not found: " + args[index], NamedTextColor.RED));
            return p;
        }
        if (sender instanceof Player self) return self;
        sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
        return null;
    }

    private void extra(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "sift" -> {
                if (!(sender instanceof Player p)) return;
                org.bukkit.World siftWorld = SiftWorld.get(plugin);
                if (siftWorld == null) {
                    sender.sendMessage(Component.text("The Sift world is turned off.", NamedTextColor.RED));
                    return;
                }
                org.bukkit.World dest = p.getWorld().equals(siftWorld) ? Bukkit.getWorlds().get(0) : siftWorld;
                org.bukkit.Location spot = new org.bukkit.Location(dest, 0.5, 0, 0.5);
                spot.setY(dest.getHighestBlockYAt(spot) + 1);
                p.teleport(spot);
            }
            case "rift" -> {
                if (!(sender instanceof Player p)) return;
                org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new org.bukkit.util.Vector(1, 0, 0);
                rifts.open(p, p.getLocation().add(dir.normalize().multiply(3)));
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) return;
                String which = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (!mobs.spawn(which, p.getLocation())) {
                    sender.sendMessage(Component.text("Usage: /smpweapons spawn <blub|sifter|sniffer|echo_golem|twisted_warden>", NamedTextColor.YELLOW));
                }
            }
            case "villager" -> {
                if (!(sender instanceof Player p)) return;
                trader.spawn(p.getLocation());
                sender.sendMessage(Component.text("Sift Trader spawned.", NamedTextColor.GREEN));
            }
            case "start" -> {
                Player t = playerArg(sender, args, 1);
                if (t == null) return;
                smp.giveStarter(t);
                sender.sendMessage(Component.text("Gave the starter spear and mace to " + t.getName() + ".", NamedTextColor.GREEN));
            }
            case "tier" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /smpweapons tier <mace|spear> <kills> [player]", NamedTextColor.YELLOW));
                    return;
                }
                Player t = playerArg(sender, args, 3);
                if (t == null) return;
                int kills;
                try {
                    kills = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Kills must be a number.", NamedTextColor.RED));
                    return;
                }
                ItemStack item = tiers.create(args[1].toLowerCase(Locale.ROOT), kills);
                if (item == null) {
                    sender.sendMessage(Component.text("Could not make that weapon.", NamedTextColor.RED));
                    return;
                }
                giveAll(t, java.util.List.of(item));
            }
            case "hearts" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /smpweapons hearts <player> <amount>", NamedTextColor.YELLOW));
                    return;
                }
                Player t = playerArg(sender, args, 1);
                if (t == null) return;
                try {
                    hearts.set(t, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(t.getName() + " now has " + hearts.get(t) + " hearts.", NamedTextColor.GREEN));
            }
            case "withdraw" -> {
                if (!(sender instanceof Player p)) return;
                if (hearts.get(p) <= 1) {
                    sender.sendMessage(Component.text("You can't withdraw your last heart.", NamedTextColor.RED));
                    return;
                }
                hearts.set(p, hearts.get(p) - 1);
                giveAll(p, java.util.List.of(hearts.heartItem(1)));
                sender.sendMessage(Component.text("You withdrew a heart.", NamedTextColor.GREEN));
            }
            case "revive" -> {
                Player t = playerArg(sender, args, 1);
                if (t == null) {
                    if (args.length > 1) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + args[1]);
                        sender.sendMessage(Component.text(args[1] + " was pardoned. They get hearts back when they rejoin and you run revive again.", NamedTextColor.YELLOW));
                    }
                    return;
                }
                hearts.set(t, plugin.getConfig().getInt("smp.revive-hearts", 5));
                t.setGameMode(org.bukkit.GameMode.SURVIVAL);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + t.getName());
                sender.sendMessage(Component.text("Revived " + t.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <give|kit|reload> [mace|spear] [player]", NamedTextColor.YELLOW));
            return true;
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        if (!subName.equals("withdraw") && !sender.hasPermission("smpweapons.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        switch (subName) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("SmpWeapons config reloaded.", NamedTextColor.GREEN));
            }
            case "give" -> give(sender, label, args);
            case "kit" -> kit(sender, args);
            case "reset" -> reset(sender, label, args);
            default -> extra(sender, subName, args);
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

    private void reset(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("mace") || args[1].equalsIgnoreCase("spear"))) {
            sender.sendMessage(Component.text("Usage: /" + label + " reset <mace|spear>", NamedTextColor.YELLOW));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        registry.setValidId(type, null);
        limiter.scanNow();
        sender.sendMessage(Component.text("The " + type + " was reset. Copies held by online players were removed, "
                + "and a new one can be given now.", NamedTextColor.GREEN));
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " give <mace|spear|sword|siftite|ichor|riftkey> [player]", NamedTextColor.YELLOW));
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
            case "siftite" -> {
                giveAll(target, sift.siftiteSet());
                sender.sendMessage(Component.text("Gave the Siftite set to " + target.getName() + ".", NamedTextColor.GREEN));
                return;
            }
            case "ichor" -> {
                giveAll(target, java.util.List.of(sift.ichorBottle(8), sift.ichorBucket(), sift.ichorSnowball(16)));
                sender.sendMessage(Component.text("Gave Ichor items to " + target.getName() + ".", NamedTextColor.GREEN));
                return;
            }
            case "riftkey" -> {
                giveAll(target, java.util.List.of(sift.riftKey()));
                sender.sendMessage(Component.text("Gave a Rift Key to " + target.getName() + ".", NamedTextColor.GREEN));
                return;
            }
            case "sword" -> {
                // The sword is not limited: give it straight away.
                ItemStack sword = items.createSword();
                var left = target.getInventory().addItem(sword);
                left.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
                sender.sendMessage(Component.text("Gave the shit sword to " + target.getName() + ".", NamedTextColor.GREEN));
                return;
            }
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
                sender.sendMessage(Component.text("Weapon must be mace, spear, sword, siftite, ichor or riftkey.", NamedTextColor.RED));
                return;
            }
        }

        // Only one mace and one spear may exist on the whole server.
        String type = args[1].toLowerCase(Locale.ROOT);
        if (registry.validId(type) != null) {
            sender.sendMessage(Component.text("A " + type + " already exists on this server. If it was lost or destroyed, "
                    + "run /" + label + " reset " + type + " first.", NamedTextColor.RED));
            return;
        }
        String uniqueId = java.util.UUID.randomUUID().toString();
        item = items.withId(item, uniqueId);
        registry.setValidId(type, uniqueId);

        var leftover = target.getInventory().addItem(item);
        leftover.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        String applied = item.getEnchantments().entrySet().stream()
                .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        sender.sendMessage(Component.text("Gave " + args[1].toLowerCase(Locale.ROOT) + " to " + target.getName()
                + ". Enchants: " + (applied.isEmpty() ? "none" : applied), NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "kit", "reset", "reload", "sift", "rift", "villager", "spawn", "start", "tier", "hearts", "withdraw", "revive"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            out.addAll(List.of("mace", "spear"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            out.addAll(List.of("blub", "sifter", "sniffer", "echo_golem", "twisted_warden"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tier")) {
            out.addAll(List.of("mace", "spear"));
        } else if (args.length == 2 && List.of("start", "hearts", "revive").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("mace", "spear", "sword", "siftite", "ichor", "riftkey"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
