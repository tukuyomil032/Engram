package com.tukuyomil032.engram.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDataStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeCreatesSchemaAndIndexes() throws Exception {
        SQLiteDataStore store = new SQLiteDataStore();
        store.initialize(tempDir.toFile());
        store.close();

        Path dbPath = tempDir.resolve("engram.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertTrue(hasTable(connection, "schema_version"));
            assertTrue(hasTable(connection, "battle_records"));
            assertTrue(hasTable(connection, "player_contributions"));
            assertTrue(hasIndex(connection, "idx_battle_world_time"));
            assertTrue(hasIndex(connection, "idx_contribution_player"));
        }
    }

    @Test
    void saveBattlePersistsRecordAndContributions() throws Exception {
        SQLiteDataStore store = new SQLiteDataStore();
        store.initialize(tempDir.toFile());

        SQLiteDataStore.BattleWriteModel battle = new SQLiteDataStore.BattleWriteModel(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            180_000L,
            2,
            true,
            42_000L,
            "ANTI_ARCHER",
            List.of(
                new SQLiteDataStore.PlayerContributionWriteModel(
                    UUID.randomUUID().toString(),
                    120.0,
                    40.0,
                    0.0,
                    68.4,
                    3
                ),
                new SQLiteDataStore.PlayerContributionWriteModel(
                    UUID.randomUUID().toString(),
                    30.0,
                    95.0,
                    0.0,
                    41.2,
                    1
                )
            )
        );

        store.saveBattle(battle);
        store.close();

        Path dbPath = tempDir.resolve("engram.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(1, scalarInt(connection, "SELECT COUNT(*) FROM battle_records"));
            assertEquals(2, scalarInt(connection, "SELECT COUNT(*) FROM player_contributions"));
        }
    }

    private static boolean hasTable(Connection connection, String tableName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?)"
        )) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static boolean hasIndex(Connection connection, String indexName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?)"
        )) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static int scalarInt(Connection connection, String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("Expected at least one row");
            }
            return rs.getInt(1);
        }
    }
}
