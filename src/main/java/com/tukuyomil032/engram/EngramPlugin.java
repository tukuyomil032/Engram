package com.tukuyomil032.engram;

import com.tukuyomil032.engram.command.DragonAdminCommand;
import com.tukuyomil032.engram.config.EngramConfig;
import com.tukuyomil032.engram.data.SQLiteDataStore;
import com.tukuyomil032.engram.animation.SwapAnimator;
import com.tukuyomil032.engram.listener.BattleTracker;
import com.tukuyomil032.engram.listener.DragonDeathListener;
import com.tukuyomil032.engram.listener.DragonSpawnListener;
import com.tukuyomil032.engram.session.BattleSessionRegistry;
import com.tukuyomil032.engram.strategy.StrategyConfigParser;
import com.tukuyomil032.engram.strategy.StrategyProfile;
import com.tukuyomil032.engram.strategy.StrategyType;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

public final class EngramPlugin extends JavaPlugin {
    private EngramConfig engramConfig;
    private YamlConfiguration strategiesConfig;
    private Map<StrategyType, StrategyProfile> strategyProfiles;
    private File strategiesFile;
    private boolean crucibleAvailable;
    private SQLiteDataStore dataStore;
    private BattleSessionRegistry sessionRegistry;
    private BattleTracker battleTracker;
    private SwapAnimator swapAnimator;
    private final StrategyConfigParser strategyConfigParser = new StrategyConfigParser();

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

        try {
            reloadEngramState();
        } catch (IllegalStateException exception) {
            getSLF4JLogger().error("Failed to load Engram configuration.", exception);
            try {
                dataStore.close();
            } catch (SQLException closeException) {
                getSLF4JLogger().error("Failed to close SQLite datastore after startup failure.", closeException);
            }
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
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
        EngramConfig reloadedConfig = EngramConfig.fromConfiguration(getConfig());
        YamlConfiguration reloadedStrategies = YamlConfiguration.loadConfiguration(strategiesFile);
        Map<StrategyType, StrategyProfile> reloadedProfiles;
        try {
            reloadedProfiles = strategyConfigParser.parse(reloadedStrategies);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid strategies.yml: " + exception.getMessage(), exception);
        }
        engramConfig = reloadedConfig;
        strategiesConfig = reloadedStrategies;
        strategyProfiles = reloadedProfiles;
    }

    public EngramConfig getEngramConfig() {
        return engramConfig;
    }

    public YamlConfiguration getStrategiesConfig() {
        return strategiesConfig;
    }

    public Map<StrategyType, StrategyProfile> getStrategyProfiles() {
        return strategyProfiles;
    }

    public boolean isCrucibleAvailable() {
        Plugin crucible = Bukkit.getPluginManager().getPlugin("MythicCrucible");
        return crucible != null && crucible.isEnabled();
    }

    public int getStrategyCount() {
        if (strategyProfiles == null) {
            return 0;
        }
        return strategyProfiles.size();
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
        swapAnimator = new SwapAnimator(this);
        battleTracker = new BattleTracker(this, sessionRegistry);
        battleTracker.startAltitudeSampling();

        Bukkit.getPluginManager().registerEvents(new DragonSpawnListener(sessionRegistry, swapAnimator, battleTracker), this);
        Bukkit.getPluginManager().registerEvents(battleTracker, this);
        Bukkit.getPluginManager().registerEvents(new DragonDeathListener(this, sessionRegistry, dataStore, battleTracker), this);
    }
}
