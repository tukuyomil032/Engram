package com.tukuyomil032.engram.command;

import com.tukuyomil032.engram.EngramPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DragonAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of("reload", "info");

    private final EngramPlugin plugin;

    public DragonAdminCommand(EngramPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /dragon <reload|info>");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Available: reload, info");
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : ROOT_SUBCOMMANDS) {
            if (candidate.startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private boolean handleReload(CommandSender sender) {
        try {
            plugin.reloadEngramState();
            sender.sendMessage(ChatColor.GREEN + "Engram configuration reloaded.");
            return true;
        } catch (IllegalStateException exception) {
            sender.sendMessage(ChatColor.RED + "Reload failed: " + exception.getMessage());
            plugin.getSLF4JLogger().error("Failed to reload Engram configuration.", exception);
            return true;
        }
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "=== Engram Info (stub) ===");
        sender.sendMessage(ChatColor.GRAY + "Learning window: " + plugin.getEngramConfig().getLearningWindowSize());
        sender.sendMessage(ChatColor.GRAY + "Loaded strategies: " + plugin.getStrategyCount());
        sender.sendMessage(ChatColor.GRAY + "Crucible available: " + plugin.isCrucibleAvailable());
        sender.sendMessage(ChatColor.GRAY + "Current strategy: not analyzed yet.");
        return true;
    }
}
