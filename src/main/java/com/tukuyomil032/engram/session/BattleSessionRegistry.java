package com.tukuyomil032.engram.session;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleSessionRegistry {
    private final Map<UUID, BattleSession> sessionsByWorld = new ConcurrentHashMap<>();

    public BattleSession startSession(UUID worldUid, String strategyUsed, long startedAt, int expectedCrystals) {
        BattleSession session = new BattleSession(worldUid, strategyUsed, startedAt, expectedCrystals);
        BattleSession existing = sessionsByWorld.putIfAbsent(worldUid, session);
        return existing != null ? existing : session;
    }

    public BattleSession getSession(UUID worldUid) {
        return sessionsByWorld.get(worldUid);
    }

    public BattleSession takeSession(UUID worldUid) {
        return sessionsByWorld.remove(worldUid);
    }

    public void removeSession(UUID worldUid) {
        sessionsByWorld.remove(worldUid);
    }

    public Collection<BattleSession> allSessions() {
        return sessionsByWorld.values();
    }
}
