package pl.bracia.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BraciaItems extends JavaPlugin implements TabExecutor {

    private Items items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        getServer().getPluginManager().registerEvents(new ItemListener(this, items), this);

        var cmd = getCommand("braciaitems");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("braciaitems.admin")) {
            sender.sendMessage(Component.text("Brak uprawnien.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(Component.text("Config przeladowany.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            ItemStack item = switch (args[1].toLowerCase()) {
                case "grapler" -> items.grapler();
                case "brata" -> items.brata();
                default -> null;
            };
            if (item == null) {
                sender.sendMessage(Component.text("Nieznany item. Dostepne: grapler, brata", NamedTextColor.RED));
                return true;
            }

            Player target = null;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                sender.sendMessage(Component.text("Nie znaleziono gracza.", NamedTextColor.RED));
                return true;
            }

            final Player finalTarget = target;
            finalTarget.getInventory().addItem(item).values()
                    .forEach(left -> finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), left));
            sender.sendMessage(Component.text("Dano " + args[1].toLowerCase() + " graczowi " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Uzycie: /braciaitems give <grapler|brata> [gracz] | reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("grapler", "brata"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }
}
