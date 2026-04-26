# AGENTS.md
> AI Agent & Developer Guide for **Engram**
> Read this before writing any code.

---

## Table of Contents
1. [Project Summary](#1-project-summary)
2. [Non-Negotiable Rules](#2-non-negotiable-rules)
3. [Implementation Phases](#3-implementation-phases)
4. [Code Style & Conventions](#4-code-style--conventions)
5. [Architecture Decisions & Rationale](#5-architecture-decisions--rationale)
6. [Working with MythicMobs API](#6-working-with-mythicmobs-api)
7. [SQLite Patterns](#7-sqlite-patterns)
8. [Testing Guidance](#8-testing-guidance)
9. [Common Mistakes to Avoid](#9-common-mistakes-to-avoid)
10. [File & Config Conventions](#10-file--config-conventions)

---

## 1. Project Summary

Engram is a **Paper plugin** that makes the Ender Dragon learn from server-wide battle history.

- Language: **Java 21**
- Framework: **Paper** (latest) + **MythicMobs Free** + **MythicCrucible**
- DB: **SQLite** (bundled via shadow JAR)
- Full spec: see `REQUIREMENTS.md`

When in doubt, **re-read REQUIREMENTS.md before writing code**.

---

## 2. Non-Negotiable Rules

These apply at all times. No exceptions.

### Never block the main thread with I/O
All SQLite reads and writes **must** run on an async thread.
If you need to call a Bukkit API after the async work, schedule back to main:
```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    BattleRecord record = dataStore.saveBattle(session.finalize());
    // No Bukkit API here
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Bukkit API calls are safe here
        player.sendMessage("Battle saved.");
    });
});
```

### Never use internal MythicMobs classes
Only use the public API surface:
- `io.lumine.mythic.bukkit.MythicBukkit`
- `io.lumine.mythic.bukkit.BukkitAPIHelper`
- `io.lumine.mythic.api.*`

Any class under `io.lumine.mythic.core.*` is internal and will break without warning.

### Never string-concatenate SQL
Always use `PreparedStatement`. No exceptions.

### Never call `Entity.remove()` on the same tick as `EntitySpawnEvent`
Schedule removal for at least the next tick.

### All configurable values must come from config.yml
No magic numbers in business logic. Every threshold, ratio, and count must be readable from `EngramConfig`.

---

## 3. Implementation Phases

Implement in this order. Do not jump ahead.

### Phase 1 — Foundation
- [ ] Gradle project setup with Paper, MythicMobs, sqlite-jdbc dependencies
- [ ] `EngramPlugin` main class (onEnable / onDisable lifecycle)
- [ ] `EngramConfig` typed wrapper around `config.yml`
- [ ] SQLite connection + schema migration (`battle_records`, `player_contributions` tables)
- [ ] `/dragon reload` and `/dragon info` (stub) commands

**Acceptance:** Server starts without errors, DB file created, `/dragon reload` works.

### Phase 2 — Battle Tracking
- [ ] `BattleSession` in-memory model
- [ ] `BattleSessionRegistry` (world UUID → session map)
- [ ] `DragonSpawnListener` — detects vanilla dragon, starts session, triggers swap
- [ ] `BattleTracker` — records damage events, crystal events, altitude sampling
- [ ] `DragonDeathListener` — finalizes session, saves `BattleRecord` async

**Acceptance:** A complete fight cycle produces a row in `battle_records` and correct rows in `player_contributions`.

### Phase 3 — Swap Animation
- [ ] `SwapAnimator` — BukkitRunnable-based tick sequence
- [ ] Preset animation (lightning, particles, sound, title, chat)
- [ ] Crucible skill dispatch hook (`awakening-skill` config key)
- [ ] MM dragon spawned at correct location after animation completes

**Acceptance:** Vanilla dragon disappears, animation plays, MM dragon appears at same location.

### Phase 4 — Analysis & Strategy
- [ ] `WeightCalculator` — player equalization (Phase B)
- [ ] `StrategyAnalyzer` — ratio scoring → `StrategyScore`
- [ ] `strategies.yml` loading and parsing
- [ ] `StrategyProfile` — maps triggers to skill names
- [ ] Strategy applied to MM dragon on spawn

**Acceptance:** After 3+ recorded fights, `/dragon info` shows a non-default strategy. Correct MM skills fire at configured triggers.

### Phase 5 — Admin Commands
- [ ] `/dragon reset [world]`
- [ ] `/dragon export [world]`
- [ ] `/dragon import <file>`
- [ ] `/dragon spawn [world]`
- [ ] Tab completion for all subcommands

**Acceptance:** All commands work. Export produces valid JSON. Import round-trips cleanly.

### Phase 6 — Individual Blending (B complete)
- [ ] Per-player fight count tracked in `player_contributions`
- [ ] Individual blending logic in `StrategyAnalyzer`
- [ ] `individual-blend-threshold` and `individual-blend-ratio` config keys wired up

**Acceptance:** A player with ≥ threshold fights sees their personal data blended into the strategy.

### Phase 7 — Time Decay (C, future)
> Implement only after Phase 6 is stable and confirmed working.
- [ ] `WeightCalculator` extended with exponential decay based on `fought_at`
- [ ] Decay curve constants exposed in `config.yml`

---

## 4. Code Style & Conventions

### Java Version Features
Use Java 21 features where they improve clarity:
```java
// Records for immutable data models
record BattleRecord(
    UUID worldUid,
    long foughtAt,
    long durationMs,
    int playerCount,
    boolean victory,
    Long crystalTimeMs,       // nullable
    StrategyType strategyUsed,
    List<PlayerContribution> players
) {}

// Pattern matching
if (entity instanceof EnderDragon dragon) { ... }

// Switch expressions
WeaponType type = switch (cause) {
    case PROJECTILE -> WeaponType.BOW;
    case ENTITY_EXPLOSION -> WeaponType.EXPLOSION;
    default -> entity instanceof Player p && isMeleeWeapon(p) ? WeaponType.MELEE : WeaponType.OTHER;
};
```

### Nullability
- Use `Optional<T>` for values that may be absent (e.g., `Optional<MythicMob>`)
- Never return `null` from public methods — use `Optional` or throw
- `@Nullable` / `@NotNull` annotations on all public method signatures

### Logging
Use Paper's component-based logging, not `System.out.println`:
```java
// GOOD
plugin.getSLF4JLogger().info("Dragon strategy for world {}: {}", worldName, strategy);
plugin.getSLF4JLogger().warn("No battle records found — using default strategy");

// NG
System.out.println("strategy: " + strategy);
```

### Event Handlers
- Use `@EventHandler(priority = EventPriority.MONITOR)` for read-only listeners
- Use `@EventHandler(priority = EventPriority.HIGH)` when you need to act before other plugins
- Always check `event.isCancelled()` in MONITOR-priority handlers before reading state

---

## 5. Architecture Decisions & Rationale

### Why SQLite and not YAML/JSON at runtime?
YAML/JSON require loading the entire file into memory and rewriting it on every save. With potentially thousands of battle records, this becomes slow and fragile. SQLite gives indexed queries, atomic writes, and no full-file rewrites.

### Why player equalization before global pooling?
Without it, a single server regular who has fought 200 times would dominate the "global" strategy even if 50 casual players collectively show different behavior. Equalization makes the dragon adapt to the *breadth* of the playerbase, not just the most active players.

### Why is the swap animation driven by a BukkitRunnable and not a Crucible skill alone?
The vanilla dragon removal and MM dragon spawn require Java code. Crucible skills handle visuals only. The `SwapAnimator` sequences both: Java logic (remove/spawn) interleaved with Crucible (visuals), coordinated by a single timer task.

### Why is `strategies.yml` separate from `config.yml`?
They serve different audiences. `config.yml` is tuned by server admins (numbers, thresholds). `strategies.yml` is authored by server builders who know MythicMobs skill names and want to add new strategy patterns. Keeping them separate reduces risk of breaking one while editing the other.

### Why is there no Velocity/proxy support?
Out of scope for initial release. The plugin is designed per-server. Proxy support would require a separate data-sync layer and is deferred.

---

## 6. Working with MythicMobs API

### Checking if MM is loaded
```java
// In onEnable — fail fast if MM is missing
Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
if (mm == null || !mm.isEnabled()) {
    getSLF4JLogger().error("MythicMobs not found — Engram disabling.");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}
```

### Soft-depend on Crucible
```yaml
# plugin.yml
depend:
  - MythicMobs
softdepend:
  - MythicCrucible
```

```java
// Check Crucible at use-site, not at startup
private boolean isCrucibleAvailable() {
    Plugin crucible = Bukkit.getPluginManager().getPlugin("MythicCrucible");
    return crucible != null && crucible.isEnabled();
}
```

### Getting an ActiveMob reference after spawn

```java
ActiveMob spawnedMob = MythicBukkit.inst().getMobManager()
    .getMythicMob("EngramDragon")
    .map(mob -> mob.spawn(BukkitAdapter.adapt(location), 1))
    .orElseThrow(() -> new IllegalStateException("EngramDragon mob type not found in MM config"));

session.setActiveMob(spawnedMob);
```

### castSkill — always check return value

```java
boolean fired = MythicBukkit.inst().getAPIHelper().castSkill(
    dragonEntity, skillName, dragonEntity, dragonEntity.getLocation(),
    List.of(), List.of(), 1.0f
);
if (!fired) {
    plugin.getSLF4JLogger().warn("Skill '{}' failed to cast — check MM skill definition", skillName);
}
```

---

## 7. SQLite Patterns

### Connection Initialization

```java
public void initialize(File dataFolder) throws SQLException {
    File dbFile = new File(dataFolder, "engram.db");
    connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    try (Statement s = connection.createStatement()) {
        s.execute("PRAGMA journal_mode=WAL;");
        s.execute("PRAGMA foreign_keys=ON;");
        s.execute("PRAGMA synchronous=NORMAL;");
    }
    runMigrations();
}
```

### Migration Pattern

Use a simple version table — do not rely on `IF NOT EXISTS` alone for evolving schemas:

```java
private void runMigrations() throws SQLException {
    try (Statement s = connection.createStatement()) {
        s.execute("""
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY
            )
        """);
    }
    int current = getCurrentVersion();
    if (current < 1) applyMigration1();
    if (current < 2) applyMigration2();
    // ...
}

private int getCurrentVersion() throws SQLException {
    try (ResultSet rs = connection.createStatement().executeQuery(
        "SELECT COALESCE(MAX(version), 0) FROM schema_version"
    )) {
        return rs.getInt(1);
    }
}
```

### Batch Inserts for PlayerContributions

```java
// GOOD: insert all contributions in one transaction
connection.setAutoCommit(false);
try {
    PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO player_contributions (battle_id, player_uuid, bow_damage, ...) VALUES (?, ?, ?, ...)"
    );
    for (PlayerContribution contrib : record.players()) {
        ps.setLong(1, battleId);
        ps.setString(2, contrib.playerUuid().toString());
        ps.setDouble(3, contrib.bowDamage());
        // ...
        ps.addBatch();
    }
    ps.executeBatch();
    connection.commit();
} catch (SQLException e) {
    connection.rollback();
    throw e;
} finally {
    connection.setAutoCommit(true);
}
```

### Closing Resources

Always use try-with-resources:
```java
try (PreparedStatement ps = connection.prepareStatement("SELECT ...")) {
    // use ps
}
// ps is automatically closed — no explicit ps.close() needed
```

---

## 8. Testing Guidance

### Unit-Testable Components
These classes have no Bukkit dependency and can be tested with plain JUnit:
- `StrategyAnalyzer` — given `List<BattleRecord>`, assert `StrategyScore`
- `WeightCalculator` — assert equalization and future decay math
- `TriggerParser` — assert `strategies.yml` parsing produces correct `PhaseTrigger` objects
- Import/export JSON serialization

### Integration Testing
Use a local Paper server with the plugin loaded. Test the full fight cycle:
1. Spawn vanilla dragon → verify MM dragon appears after animation
2. Complete a fight → verify `battle_records` row created
3. Run `/dragon info` → verify strategy output
4. Run `/dragon reset` → verify table cleared
5. Run `/dragon export` then `/dragon import` → verify round-trip

### Mock Battle Records for Analysis Testing
```java
List<BattleRecord> mockRecords = List.of(
    new BattleRecord(worldUid, now - 1000, 180_000, 2, true, 40_000L, ANTI_ARCHER,
        List.of(
            new PlayerContribution(uuid1, 400.0, 50.0, 0.0, 70.0, 5),
            new PlayerContribution(uuid2, 300.0, 80.0, 0.0, 65.0, 3)
        )
    )
    // add more records...
);

StrategyScore score = analyzer.analyze(mockRecords, worldUid, emptyMap());
assertEquals(StrategyType.ANTI_ARCHER, score.winner());
assertTrue(score.confidence() > 0.5);
```

---

## 9. Common Mistakes to Avoid

| Mistake | Why It Breaks | Fix |
|---|---|---|
| Calling `dataStore.save()` on main thread | TPS drops, server freezes on slow disk | Always async |
| Using `MM internal classes` | Breaks silently on MM update | Use `BukkitAPIHelper` only |
| Removing vanilla dragon in same tick as spawn | Corrupted dragon fight state | Schedule +1 tick minimum |
| Firing HP triggers repeatedly | Skill spam, unplayable fight | Use `firedTriggers` Set |
| Sampling `PlayerMoveEvent` every event | Memory/CPU flood | Sample on timer, 20-tick interval |
| `getString("world")` instead of `getWorldName()` | World name vs UID confusion | Always store/compare by UUID |
| Null `ActiveMob` after spawn | `castSkill` NPE crash | Check Optional, log if absent |
| Hardcoding MM skill names in Java | Untunable without recompile | Read from `strategies.yml` |
| Missing `@EventHandler` on listener methods | Events silently not received | All listener methods need the annotation |
| Not registering listeners in `onEnable` | Same as above | `Bukkit.getPluginManager().registerEvents(listener, this)` |

---

## 10. File & Config Conventions

### Plugin Data Folder Layout

```
plugins/Engram/
├── config.yml           # Main config (learning params, animation, dragon name)
├── strategies.yml       # Strategy → skill mappings and triggers
├── engram.db            # SQLite database (auto-created)
└── exports/
    └── 2025-04-01_export.json  # Generated by /dragon export
```

### config.yml Edit Contract
- Changing `config.yml` values takes effect after `/dragon reload`
- Changing `strategies.yml` (skill names, triggers) also requires `/dragon reload`
- DB schema changes require server restart (migrations run in `onEnable`)

### plugin.yml Required Fields

```yaml
name: Engram
version: '1.0.0'
main: com.example.engram.EngramPlugin
api-version: '1.21'
authors: [ yourname ]
description: Adaptive Ender Dragon that learns from player behavior
depend:
  - MythicMobs
softdepend:
  - MythicCrucible
commands:
  dragon:
    description: Engram admin commands
    usage: /dragon <subcommand>
    permission: engram.admin
permissions:
  engram.admin:
    description: Access to all /dragon commands
    default: op
```

## Shared Rules

### Commit Message Conventions

Use English commit message prefixes:

- `feat:` - New features
- `fix:` - Bug fixes
- `ref:` - Refactoring
- `docs:` - Documentation changes
- `chore:` - Maintenance tasks

Examples:

```bash
git commit -m "feat: add system logs scanner" -m "Scan /var/log and ~/Library/Logs for old log files" -m "Adds new scanner category with configurable age threshold"

git commit -m "fix: optimize command timeout on Intel Macs" -m "Add 30s timeout to DNS flush task" -m "Prevents indefinite hanging on certain hardware"

git commit -m "ref: extract scanner directory tracking" -m "Add scannedDirectories property to base scanner" -m "Enables post-scan directory summary display"
```

### Phase-Based Development Workflow

After each implementation/editing prompt is completed, confirm whether there are unfinished points in the current phase, or if the phase is complete, whether there are tasks in the next phase.

If there are remaining tasks, ask the user in **selection mode** where to continue next using `ask_user` tool (not plain text questions). Repeat this after every implementation step until all phases are complete and the project is release-ready.

After that question, generate a commit command with a message format appropriate to the implementation and ask the user in selection mode whether to execute it.

**Commit Timing Rules:**

- Do NOT ask for commit command generation for every tiny change within the same phase
- However, if a change inside the same phase is large, this rule can be treated as an exception
- Even if the user chooses not to run the commit command, continue implementation if there is still work in the current phase or next phases
- Keep iterating with question → implementation → question until completion

The "question" here refers to selection-style planning questions using `ask_user` tool, not plain text questions.

**Commit Command Format:**

```bash
git commit -m "message" -m "message" -m "message"
```

Use multiple `-m` flags for detailed commit messages with:

1. First message: Brief summary of changes
2. Second message: Detailed description
3. Third message: Technical notes or impacts

### Git Operations

Use VS Code terminal commands directly for git operations. Do not use MCP for git operations.

**Standard Git Workflow:**

```bash
# Stage all changes
git add .

# Commit with multi-line message
git commit -m "feat: add new scanner" -m "Detailed description" -m "Technical notes"

# Push to main branch
git push origin main

# Force push (only after reset)
git push -f -u origin main
```

**After Reset:**
If a reset command such as `git reset --soft HEAD^` is performed and the user informs you, force push is required:

```bash
git push -f -u origin main
```