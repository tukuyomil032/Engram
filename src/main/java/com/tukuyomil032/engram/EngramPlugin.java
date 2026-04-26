package com.tukuyomil032.engram;

import com.tukuyomil032.engram.command.DragonAdminCommand;
import com.tukuyomil032.engram.config.EngramConfig;
import com.tukuyomil032.engram.data.SQLiteDataStore;
import com.tukuyomil032.engram.listener.BattleTracker;
import com.tukuyomil032.engram.listener.DragonDeathListener;
import com.tukuyomil032.engram.listener.DragonSpawnListener;
import com.tukuyomil032.engram.session.BattleSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class EngramPlugin extends JavaPlugin {
    private EngramConfig engramConfig;
    private YamlConfiguration strategiesConfig;
    private File strategiesFile;
    private boolean crucibleAvailable;
    private SQLiteDataStore dataStore;
    private BattleSessionRegistry sessionRegistry;
    private BattleTracker battleTracker;

    @Override
    public void onEnable() {
        if (!ensureMythicMobsAvailable()) {
            return;
        }
        saveDefaultConfig();
        ensureStrategiesFile();

        sessionRegistry = new BattleSessionRegistry();
        dataStore = new SQLiteDataStore();
        try {
            dataStore.initialize(getDataFolder());
        } catch (SQLException exception) {
            getSLF4JLogger().error("Failed to initialize SQLite datastore.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        reloadEngramState();
        this.crucibleAvailable = isCrucibleAvailable();

        registerDragonCommand();
        registerListeners();
        getSLF4JLogger().info("Engram enabled (Crucible available: {}).", crucibleAvailable);
    }

    @Override
    public void onDisable() {
        if (battleTracker != null) {
            battleTracker.stopAltitudeSampling();
        }
        if (dataStore != null) {
            try {
                dataStore.close();
            } catch (SQLException exception) {
                getSLF4JLogger().error("Failed to close SQLite datastore cleanly.", exception);
            }
        }
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

    public BattleSessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public SQLiteDataStore getDataStore() {
        return dataStore;
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

    private void registerListeners() {
        battleTracker = new BattleTracker(this, sessionRegistry);
        battleTracker.startAltitudeSampling();

        Bukkit.getPluginManager().registerEvents(new DragonSpawnListener(sessionRegistry), this);
        Bukkit.getPluginManager().registerEvents(battleTracker, this);
        Bukkit.getPluginManager().registerEvents(new DragonDeathListener(this, sessionRegistry, dataStore), this);
    }
}
