package com.tukuyomil032.engram;

import com.tukuyomil032.engram.command.DragonAdminCommand;
import com.tukuyomil032.engram.config.EngramConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class EngramPlugin extends JavaPlugin {
    private EngramConfig engramConfig;
    private YamlConfiguration strategiesConfig;
    private File strategiesFile;
    private boolean crucibleAvailable;

    @Override
    public void onEnable() {
        if (!ensureMythicMobsAvailable()) {
            return;
        }
        saveDefaultConfig();
        ensureStrategiesFile();

        reloadEngramState();
        this.crucibleAvailable = isCrucibleAvailable();

        registerDragonCommand();
        getSLF4JLogger().info("Engram enabled (Crucible available: {}).", crucibleAvailable);
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("Engram disabled.");
    }

    public void reloadEngramState() {
        reloadConfig();
        engramConfig = EngramConfig.fromConfiguration(getConfig());
        strategiesConfig = YamlConfiguration.loadConfiguration(strategiesFile);
    }

    public EngramConfig getEngramConfig() {
        return engramConfig;
    }

    public YamlConfiguration getStrategiesConfig() {
        return strategiesConfig;
    }

    public boolean isCrucibleAvailable() {
        Plugin crucible = Bukkit.getPluginManager().getPlugin("MythicCrucible");
        return crucible != null && crucible.isEnabled();
    }

    public int getStrategyCount() {
        if (strategiesConfig == null) {
            return 0;
        }
        var strategiesSection = strategiesConfig.getConfigurationSection("strategies");
        if (strategiesSection == null) {
            return 0;
        }
        return strategiesSection.getKeys(false).size();
    }

    private boolean ensureMythicMobsAvailable() {
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythic != null && mythic.isEnabled()) {
            return true;
        }
        getSLF4JLogger().error("MythicMobs not found — Engram disabling.");
        Bukkit.getPluginManager().disablePlugin(this);
        return false;
    }

    private void ensureStrategiesFile() {
        strategiesFile = new File(getDataFolder(), "strategies.yml");
        if (!strategiesFile.exists()) {
            saveResource("strategies.yml", false);
        }
        if (!strategiesFile.exists()) {
            throw new IllegalStateException("strategies.yml is missing in plugin data folder");
        }
    }

    private void registerDragonCommand() {
        PluginCommand dragonCommand = getCommand("dragon");
        if (dragonCommand == null) {
            throw new IllegalStateException("Command 'dragon' is not defined in plugin.yml");
        }
        DragonAdminCommand command = new DragonAdminCommand(this);
        dragonCommand.setExecutor(command);
        dragonCommand.setTabCompleter(command);
    }
}
