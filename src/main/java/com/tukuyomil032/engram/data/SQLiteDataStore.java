package com.tukuyomil032.engram.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

public final class SQLiteDataStore {
    private static final String DB_FILE_NAME = "engram.db";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private Connection connection;

    public synchronized void initialize(File dataFolder) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("Could not create plugin data directory: " + dataFolder.getAbsolutePath());
        }

        File dbFile = new File(dataFolder, DB_FILE_NAME);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute("PRAGMA foreign_keys=ON;");
            statement.execute("PRAGMA synchronous=NORMAL;");
        }
        runMigrations();
    }

    public synchronized long saveBattle(BattleWriteModel battle) throws SQLException {
        ensureOpenConnection();
        connection.setAutoCommit(false);
        try {
            long battleId = insertBattleRecord(battle);
            insertPlayerContributions(battleId, battle.players());
            connection.commit();
            return battleId;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized long saveBattleRecord(BattleRecord battleRecord) throws SQLException {
        List<PlayerContributionWriteModel> players = battleRecord.players().stream()
            .map(player -> new PlayerContributionWriteModel(
                player.playerUuid().toString(),
                player.bowDamage(),
                player.meleeDamage(),
                player.explosionDamage(),
                player.avgAltitude(),
                player.fightCount()
            ))
            .toList();

        return saveBattle(new BattleWriteModel(
            battleRecord.worldUid().toString(),
            battleRecord.foughtAt(),
            battleRecord.durationMs(),
            battleRecord.playerCount(),
            battleRecord.victory(),
            battleRecord.crystalTimeMs(),
            battleRecord.strategyUsed(),
            players
        ));
    }

    public synchronized void close() throws SQLException {
        if (connection == null) {
            return;
        }
        connection.close();
        connection = null;
    }

    private void ensureOpenConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("SQLiteDataStore is not initialized");
        }
    }

    private void runMigrations() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY
                )
                """);
        }

        if (getCurrentSchemaVersion() < 1) {
            applySchemaV1();
        }

        if (getCurrentSchemaVersion() != CURRENT_SCHEMA_VERSION) {
            throw new SQLException("Unexpected schema version state");
        }
    }

    private int getCurrentSchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            if (!rs.next()) {
                return 0;
            }
            return rs.getInt(1);
        }
    }

    private void applySchemaV1() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS battle_records (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    world_uid       TEXT    NOT NULL,
                    fought_at       INTEGER NOT NULL,
                    duration_ms     INTEGER NOT NULL,
                    player_count    INTEGER NOT NULL,
                    victory         INTEGER NOT NULL,
                    crystal_time_ms INTEGER,
                    strategy_used   TEXT    NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS player_contributions (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    battle_id         INTEGER NOT NULL REFERENCES battle_records(id) ON DELETE CASCADE,
                    player_uuid       TEXT    NOT NULL,
                    bow_damage        REAL    NOT NULL DEFAULT 0,
                    melee_damage      REAL    NOT NULL DEFAULT 0,
                    explosion_damage  REAL    NOT NULL DEFAULT 0,
                    avg_altitude      REAL    NOT NULL DEFAULT 0,
                    fight_count       INTEGER NOT NULL DEFAULT 1
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_battle_world_time
                ON battle_records(world_uid, fought_at DESC)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_contribution_player
                ON player_contributions(player_uuid)
                """);
            statement.execute("INSERT INTO schema_version(version) VALUES (1)");
        }
    }

    private long insertBattleRecord(BattleWriteModel battle) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO battle_records (
                world_uid,
                fought_at,
                duration_ms,
                player_count,
                victory,
                crystal_time_ms,
                strategy_used
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, battle.worldUid());
            ps.setLong(2, battle.foughtAt());
            ps.setLong(3, battle.durationMs());
            ps.setInt(4, battle.playerCount());
            ps.setInt(5, battle.victory() ? 1 : 0);
            if (battle.crystalTimeMs() == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setLong(6, battle.crystalTimeMs());
            }
            ps.setString(7, battle.strategyUsed());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Could not fetch generated battle id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertPlayerContributions(long battleId, List<PlayerContributionWriteModel> players) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO player_contributions (
                battle_id,
                player_uuid,
                bow_damage,
                melee_damage,
                explosion_damage,
                avg_altitude,
                fight_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
            for (PlayerContributionWriteModel player : players) {
                ps.setLong(1, battleId);
                ps.setString(2, player.playerUuid());
                ps.setDouble(3, player.bowDamage());
                ps.setDouble(4, player.meleeDamage());
                ps.setDouble(5, player.explosionDamage());
                ps.setDouble(6, player.avgAltitude());
                ps.setInt(7, player.fightCount());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public record BattleWriteModel(
        String worldUid,
        long foughtAt,
        long durationMs,
        int playerCount,
        boolean victory,
        Long crystalTimeMs,
        String strategyUsed,
        List<PlayerContributionWriteModel> players
    ) {
    }

    public record PlayerContributionWriteModel(
        String playerUuid,
        double bowDamage,
        double meleeDamage,
        double explosionDamage,
        double avgAltitude,
        int fightCount
    ) {
    }
}
