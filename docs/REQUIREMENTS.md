# REQUIREMENTS.md
> Adaptive Ender Dragon Plugin — working title: **Engram**

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [System Architecture](#3-system-architecture)
4. [Data Collection](#4-data-collection)
5. [Data Storage — SQLite Schema](#5-data-storage--sqlite-schema)
6. [Data Formats — JSON Reference](#6-data-formats--json-reference)
7. [Analysis & Scoring Logic](#7-analysis--scoring-logic)
8. [Strategy Pattern Definitions](#8-strategy-pattern-definitions)
9. [MythicMobs / MythicCrucible Integration](#9-mythicmobs--mythiccrucible-integration)
10. [Swap Animation System](#10-swap-animation-system)
11. [Skill Trigger System](#11-skill-trigger-system)
12. [Configuration — config.yml Reference](#12-configuration--configyml-reference)
13. [Commands](#13-commands)
14. [NG Patterns — What Not to Do](#14-ng-patterns--what-not-to-do)

---

## 1. Project Overview

**Engram** is a Paper plugin that replaces the vanilla Ender Dragon with a MythicMobs-based boss that **learns from server-wide player behavior** across fights.

### Core Loop

```
Player fights dragon
    → Plugin records battle data (weapons, crystal timing, movement)
    → Data stored in SQLite per-world
    → On next spawn: analyzer scores all recent battles
    → Dragon spawns with a strategy set tuned against the server's playstyle
    → MythicMobs/Crucible skills are dispatched dynamically based on the score
```

### Goals
- Every dragon fight feels different and progressively harder to "cheese"
- Players feel the dragon "knows" them without explicit AI
- Server admins can tune every parameter without touching Java code
- MythicMobs skill YAMLs are the single source of truth for dragon behavior

---

## 2. Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Server | Paper (latest, tracked) | Must use Paper API, not Spigot-only APIs |
| Boss Framework | MythicMobs Free + MythicCrucible | MM for mob/skill core; Crucible for custom items and advanced mechanics |
| Database | SQLite via JDBC (`org.xerial:sqlite-jdbc`) | Single `.db` file, no external server needed |
| Build | Gradle (Kotlin DSL) | Shadow JAR for bundling sqlite-jdbc |
| Language | Java 21 | Use records, sealed classes, pattern matching where appropriate |
| Config | YAML via Paper's built-in config API | `config.yml` hot-reloadable via `/dragon reload` |

### Gradle dependency (sqlite-jdbc)
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.6.1") // MythicMobs Free
    compileOnly("io.lumine:MythicCrucible:2.0.0") // Crucible
}

tasks.shadowJar {
    relocate("org.sqlite", "com.example.engram.lib.sqlite")
}
```

> **Why relocate sqlite?** Without relocation, if another plugin bundles a different sqlite-jdbc version, classloader conflicts will cause cryptic crashes at runtime.

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────┐
│                  Paper Server                   │
│                                                 │
│  ┌─────────────┐     ┌─────────────────────┐   │
│  │  Listeners  │────▶│   BattleSession     │   │
│  │  (Events)   │     │   (in-memory state) │   │
│  └─────────────┘     └────────┬────────────┘   │
│                               │ on dragon death  │
│                               ▼                  │
│                      ┌────────────────┐          │
│                      │ SQLiteDataStore │          │
│                      │  (battle_logs) │          │
│                      └────────┬───────┘          │
│                               │ on next spawn     │
│                               ▼                  │
│                      ┌────────────────┐          │
│                      │StrategyAnalyzer│          │
│                      │ scores battles │          │
│                      └────────┬───────┘          │
│                               │                  │
│                               ▼                  │
│                      ┌────────────────┐          │
│                      │SkillDispatcher │          │
│                      │ castSkill() ──▶│──▶ MM    │
│                      └────────────────┘          │
└─────────────────────────────────────────────────┘
```

### Package Layout

```
src/main/java/com/tukuyomil032/engram/
├── EngramPlugin.java               # Main plugin class
├── config/
│   └── EngramConfig.java           # Typed config wrapper
├── session/
│   ├── BattleSession.java          # Tracks one ongoing fight (per world)
│   └── BattleSessionRegistry.java  # World UUID → BattleSession map
├── data/
│   ├── BattleRecord.java           # Immutable record of one completed fight
│   ├── PlayerContribution.java     # One player's stats within a fight
│   └── SQLiteDataStore.java        # All DB read/write operations
├── analysis/
│   ├── StrategyAnalyzer.java       # Produces StrategyScore from BattleRecords
│   ├── StrategyScore.java          # Result: which strategy wins + confidence
│   └── WeightCalculator.java       # Player equalization (B) + time decay (C, later)
├── strategy/
│   ├── StrategyType.java           # Enum: ANTI_ARCHER, ANTI_MELEE, ANTI_CRYSTAL, ANTI_SPEEDRUN
│   └── StrategyProfile.java        # Maps StrategyType → MM skill names per phase
├── listener/
│   ├── DragonSpawnListener.java    # Intercepts vanilla dragon spawn
│   ├── BattleTracker.java          # Records damage/crystal/movement events
│   └── DragonDeathListener.java    # Finalizes and saves BattleRecord
├── animation/
│   └── SwapAnimator.java           # Vanilla dragon removal + MM spawn sequence
├── skill/
│   └── SkillDispatcher.java        # Wraps MythicMobs BukkitAPIHelper.castSkill()
└── command/
    └── DragonAdminCommand.java     # /dragon subcommands
```

---

## 4. Data Collection

The following data points are recorded **per fight** via Paper events.

### Events to Listen To

| Paper Event | Data Extracted |
|---|---|
| `EntityDamageByEntityEvent` | Damage amount, weapon type (bow/sword/axe/explosion), attacker UUID |
| `PlayerInteractAtEntityEvent` | Crystal interaction (pre-destruction click) |
| `EntityDeathEvent` where entity is `END_CRYSTAL` | Crystal destroyed, timestamp, destroyer UUID |
| `PlayerMoveEvent` (sampled every 20 ticks) | Player Y-coordinate → average altitude |
| `EnderDragonChangePhaseEvent` | Phase transitions, timestamps |
| `EntityDeathEvent` where entity is Ender Dragon | Fight end, total duration |

### Weapon Classification

```java
// GOOD: classify by item material + enchantments, not entity type
WeaponType classify(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player player)) return WeaponType.OTHER;
    ItemStack item = player.getInventory().getItemInMainHand();
    Material mat = item.getType();

    if (e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
        return WeaponType.BOW; // covers bow and crossbow
    }
    if (mat.name().endsWith("_SWORD") || mat.name().endsWith("_AXE")) {
        return WeaponType.MELEE;
    }
    if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
        return WeaponType.EXPLOSION;
    }
    return WeaponType.OTHER;
}
```

### Crystal Timing

```java
// Record absolute timestamps (System.currentTimeMillis()) for each crystal
// so we can compute time-to-all-crystals-destroyed later
record CrystalEvent(UUID destroyerUuid, long timestampMs, int crystalIndex) {}
```

### Altitude Sampling

```java
// Sample every 20 ticks (1 second). Do NOT listen to PlayerMoveEvent every move.
// PlayerMoveEvent fires ~20x per second per player — sampling is mandatory.
// Store sum + count per player, compute average on fight end.
scheduler.runTaskTimer(plugin, () -> {
    session.sampleAltitudes();
}, 0L, 20L);
```

---

## 5. Data Storage — SQLite Schema

### Table: `battle_records`

```sql
CREATE TABLE IF NOT EXISTS battle_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    world_uid       TEXT    NOT NULL,          -- World UUID as string
    fought_at       INTEGER NOT NULL,          -- Unix epoch ms
    duration_ms     INTEGER NOT NULL,
    player_count    INTEGER NOT NULL,
    victory         INTEGER NOT NULL,          -- 1 = players won, 0 = dragon won/reset
    crystal_time_ms INTEGER,                   -- ms until all crystals destroyed (NULL if not all destroyed)
    strategy_used   TEXT    NOT NULL           -- StrategyType name applied this fight
);
```

### Table: `player_contributions`

```sql
CREATE TABLE IF NOT EXISTS player_contributions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    battle_id       INTEGER NOT NULL REFERENCES battle_records(id) ON DELETE CASCADE,
    player_uuid     TEXT    NOT NULL,
    bow_damage      REAL    NOT NULL DEFAULT 0,
    melee_damage    REAL    NOT NULL DEFAULT 0,
    explosion_damage REAL   NOT NULL DEFAULT 0,
    avg_altitude    REAL    NOT NULL DEFAULT 0,
    fight_count     INTEGER NOT NULL DEFAULT 1  -- lifetime fights for this player (denormalized for weight calc)
);
```

### Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_battle_world_time
    ON battle_records(world_uid, fought_at DESC);

CREATE INDEX IF NOT EXISTS idx_contribution_player
    ON player_contributions(player_uuid);
```

### Connection Management

```java
// GOOD: Use a single persistent connection with WAL mode for better concurrency
Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
try (Statement stmt = connection.createStatement()) {
    stmt.execute("PRAGMA journal_mode=WAL;");
    stmt.execute("PRAGMA foreign_keys=ON;");
}

// GOOD: Use PreparedStatements — never string-concatenate SQL
PreparedStatement ps = connection.prepareStatement(
    "INSERT INTO battle_records (world_uid, fought_at, duration_ms, ...) VALUES (?, ?, ?, ...)"
);
ps.setString(1, worldUid);
ps.setLong(2, foughtAt);
// ...
```

---

## 6. Data Formats — JSON Reference

JSON is used for **import/export only** (`/dragon export`, `/dragon import`). The database is SQLite at runtime.

### Export Format: `engram-export.json`

```json
{
  "version": 1,
  "exported_at": "2025-04-01T12:00:00Z",
  "worlds": [
    {
      "world_uid": "a1b2c3d4-e5f6-...",
      "world_name": "world_the_end",
      "battles": [
        {
          "fought_at": 1743504000000,
          "duration_ms": 187400,
          "player_count": 3,
          "victory": true,
          "crystal_time_ms": 42000,
          "strategy_used": "ANTI_ARCHER",
          "players": [
            {
              "uuid": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "bow_damage": 312.5,
              "melee_damage": 0.0,
              "explosion_damage": 0.0,
              "avg_altitude": 68.3
            },
            {
              "uuid": "ffffffff-0000-1111-2222-333333333333",
              "bow_damage": 89.0,
              "melee_damage": 201.0,
              "explosion_damage": 0.0,
              "avg_altitude": 12.1
            }
          ]
        }
      ]
    }
  ]
}
```

### Admin Info Response (displayed in chat / used internally)

```json
{
  "world_uid": "a1b2c3d4-...",
  "battles_analyzed": 47,
  "current_strategy": "ANTI_ARCHER",
  "strategy_confidence": 0.73,
  "global_stats": {
    "bow_damage_ratio": 0.61,
    "melee_damage_ratio": 0.28,
    "explosion_damage_ratio": 0.11,
    "avg_crystal_time_ms": 38500,
    "avg_fight_duration_ms": 210000,
    "avg_altitude": 52.4
  },
  "top_players_by_fights": [
    { "uuid": "aaaaaaaa-...", "fight_count": 14 },
    { "uuid": "ffffffff-...", "fight_count": 9 }
  ]
}
```

---

## 7. Analysis & Scoring Logic

### Step 1 — Fetch Recent Battles

```java
// Fetch up to configuredWindowSize recent battles for the world
List<BattleRecord> records = dataStore.getRecentBattles(worldUid, config.getLearningWindowSize());
```

### Step 2 — Player Equalization (Weight B)

Each player's contributions are **averaged** before being merged into the global pool. This prevents one prolific player from dominating the dataset.

```java
// GOOD: average per player first, then average across players
Map<UUID, PlayerStats> perPlayerAvg = computePerPlayerAverages(records);

// Each player contributes exactly 1 "vote" to the pool regardless of fight count
GlobalStats global = average(perPlayerAvg.values());
```

```java
// NG: summing raw damage across all battles — highly active players dominate
// ❌ double totalBow = records.stream()
//     .flatMap(r -> r.players().stream())
//     .mapToDouble(PlayerContribution::bowDamage).sum();
```

### Step 3 — Compute Ratios

```java
double total = global.bowDamage() + global.meleeDamage() + global.explosionDamage();
double bowRatio    = total > 0 ? global.bowDamage()       / total : 0;
double meleeRatio  = total > 0 ? global.meleeDamage()     / total : 0;
double expl Ratio  = total > 0 ? global.explosionDamage() / total : 0;

boolean fastCrystal = global.avgCrystalTimeMs() > 0
    && global.avgCrystalTimeMs() < config.getFastCrystalThresholdMs();
boolean speedrun    = global.avgFightDurationMs() < config.getSpeedrunThresholdMs();
```

### Step 4 — Score Each Strategy

```java
Map<StrategyType, Double> scores = new EnumMap<>(StrategyType.class);
scores.put(ANTI_ARCHER,   bowRatio * 2.0);
scores.put(ANTI_MELEE,    meleeRatio * 2.0);
scores.put(ANTI_CRYSTAL,  fastCrystal ? 1.5 : 0.0);
scores.put(ANTI_SPEEDRUN, speedrun    ? 1.5 : 0.0);

StrategyType winner = Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
double confidence = scores.get(winner) / scores.values().stream().mapToDouble(Double::doubleValue).sum();
```

### Step 5 — Individual Blending (if player fought ≥ threshold times)

```java
// Only blend individual data if the player has fought >= config threshold
if (playerFightCount >= config.getIndividualBlendThreshold()) {
    PlayerStats individual = dataStore.getPlayerLifetimeAvg(playerUuid, worldUid);
    double blendRatio = config.getIndividualBlendRatio(); // e.g. 0.3
    global = blend(global, individual, blendRatio);
}
```

---

## 8. Strategy Pattern Definitions

Strategies are defined in `strategies.yml` (inside the plugin's data folder). Java code only reads strategy names — all tuning is in YAML.

### `strategies.yml` Example

```yaml
strategies:
  ANTI_ARCHER:
    description: "Forces dragon to stay low, making bow shots harder"
    phases:
      - trigger: "hp_percent:75"
        skills:
          - "DragonAntiArcher_Phase1"
      - trigger: "hp_percent:50"
        skills:
          - "DragonAntiArcher_Phase2"
          - "DragonEndermanSwarm"
      - trigger: "hp_percent:25"
        skills:
          - "DragonAntiArcher_Phase3"

  ANTI_MELEE:
    description: "Increases breath frequency and knockback to deny close range"
    phases:
      - trigger: "hp_percent:75"
        skills:
          - "DragonAntiMelee_Phase1"
      - trigger: "timer_seconds:30"
        skills:
          - "DragonBreathBarrage"
      - trigger: "damage_chance:0.15"
        skills:
          - "DragonKnockbackPulse"

  ANTI_CRYSTAL:
    description: "Spawns guards near crystals and heals when crystals are active"
    phases:
      - trigger: "hp_percent:75"
        skills:
          - "DragonCrystalGuard"
      - trigger: "hp_percent:50"
        skills:
          - "DragonCrystalGuard"
          - "DragonCrystalHeal"

  ANTI_SPEEDRUN:
    description: "Adds self-heal phases and increases damage output"
    phases:
      - trigger: "hp_percent:75"
        skills:
          - "DragonSpeedrunPhase1"
      - trigger: "hp_percent:50"
        skills:
          - "DragonSelfHeal"
      - trigger: "hp_percent:25"
        skills:
          - "DragonEnrage"
```

### Trigger Types

| Trigger Syntax | Description |
|---|---|
| `hp_percent:75` | Fires once when HP drops below 75% |
| `timer_seconds:30` | Fires 30 seconds after dragon spawns |
| `damage_chance:0.15` | Fires with 15% probability on each damage taken |
| `interval_seconds:20` | Fires every 20 seconds |
| `mm_skill:SkillName` | Delegates entirely to a MythicMobs skill trigger (advanced) |

---

## 9. MythicMobs / MythicCrucible Integration

### Spawning the MM Dragon

```java
MythicMobs mm = MythicMobs.inst();
Optional<MythicMob> mobType = mm.getMobManager().getMythicMob("EngramDragon");

mobType.ifPresent(mob -> {
    ActiveMob active = mob.spawn(BukkitAdapter.adapt(spawnLocation), 1);
    // Store ActiveMob reference in BattleSession for later castSkill calls
    session.setActiveMob(active);
});
```

### Dispatching Skills at Runtime

```java
// GOOD: use BukkitAPIHelper — it is the stable public API
BukkitAPIHelper api = MythicMobs.inst().getAPIHelper();
api.castSkill(
    session.getDragonEntity(),   // the living dragon entity
    skillName,                   // e.g. "DragonAntiArcher_Phase1"
    session.getDragonEntity(),   // trigger entity (self)
    session.getDragonEntity().getLocation(),
    List.of(),                   // entity targets (empty = skill decides)
    List.of(),                   // location targets
    1.0f                         // power
);
```

```java
// NG: do NOT use internal MythicMobs classes directly
// ❌ SkillTrigger trigger = new SkillTrigger(...);
// ❌ mob.getSkillHandler().castSkill(...) using non-API internals
// These will break on every MM update without warning.
```

### MythicMobs Dragon Definition (`EngramDragon.yml`)

```yaml
EngramDragon:
  Type: ENDER_DRAGON
  Display: '&5&lEngram'
  Health: 200
  Damage: 10
  Options:
    PreventOtherDrops: true
    PreventSunburn: false
  # No Skills block here — all skills are dispatched programmatically by Engram
  # Add only passive/ambient skills here if needed
  AIGoalSelectors:
  - clear
  AITargetSelectors:
  - clear
  - players
```

### MythicCrucible Usage

Crucible is used for:
- Custom item drops after dragon death (special rewards based on fight count)
- Advanced particle/effect skills used in the swap animation
- Custom dragon "aura" mechanics that vanilla MM cannot express

```java
// Check Crucible availability defensively — it's optional at plugin load
boolean crucibleEnabled = Bukkit.getPluginManager().getPlugin("MythicCrucible") != null;
```

---

## 10. Swap Animation System

### Flow

```
EntitySpawnEvent fires (vanilla ENDER_DRAGON)
    → cancel() is NOT called immediately (dragon must exist briefly)
    → vanilla dragon set invisible + invulnerable
    → SwapAnimator.start(vanillaDragon, world) called async-safe via BukkitScheduler
        → tick 0:   lightning strike + particle burst + chat message
        → tick 10:  screen title "???" fade in
        → tick 30:  screen title clears, dragon roar sound
        → tick 50:  vanilla dragon.remove()
        → tick 51:  MM dragon spawns at same location
        → tick 52:  SkillDispatcher applies chosen strategy
```

### SwapAnimator — Key Points

```java
// GOOD: schedule all visual effects synchronously on the main thread
// Particle / sound calls are not thread-safe
new BukkitRunnable() {
    int tick = 0;
    @Override
    public void run() {
        switch (tick) {
            case 0  -> playLightning(location, world);
            case 10 -> sendTitle(world, "???");
            case 30 -> playDragonRoar(location, world);
            case 50 -> { vanillaDragon.remove(); this.cancel(); spawnMM(); }
        }
        tick++;
    }
}.runTaskTimer(plugin, 0L, 1L);
```

```java
// NG: do not remove the vanilla dragon on the same tick as the SpawnEvent
// ❌ @EventHandler
// ❌ public void onSpawn(EntitySpawnEvent e) {
// ❌     if (e.getEntity() instanceof EnderDragon) {
// ❌         e.getEntity().remove(); // removes before entity is fully registered → corrupted state
// ❌     }
// ❌ }
```

### Preset Animation Skills (Crucible-backed)

The swap animation also fires a named Crucible skill (`EngramAwakening`) from the spawn location. This means server admins can **fully customize the visual** by editing the Crucible skill YAML without touching Java.

```yaml
# MythicCrucible skill: EngramAwakening
EngramAwakening:
  Skills:
  - effect:lightning @origin
  - particles{...} @origin
  - sound{sound=ENTITY_ENDER_DRAGON_GROWL} @origin
  - message{msg="&5The dragon remembers..."} @world
```

---

## 11. Skill Trigger System

The `SkillDispatcher` monitors the active dragon and evaluates triggers every tick (or on events).

### Trigger Evaluation

```java
// HP percent triggers — checked on EntityDamageEvent
@EventHandler
public void onDragonDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof EnderDragon dragon)) return;
    BattleSession session = registry.getSession(dragon.getWorld());
    if (session == null) return;

    double hpPercent = dragon.getHealth() / dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 100;
    session.getProfile().evaluateHpTriggers(hpPercent, (skillName) -> {
        dispatcher.cast(dragon, skillName);
    });
}
```

```java
// Timer triggers — evaluated on a repeating task
scheduler.runTaskTimer(plugin, () -> {
    for (BattleSession session : registry.allSessions()) {
        long elapsed = (System.currentTimeMillis() - session.getStartMs()) / 1000;
        session.getProfile().evaluateTimerTriggers(elapsed, skillName -> {
            dispatcher.cast(session.getDragonEntity(), skillName);
        });
    }
}, 0L, 20L); // check every second
```

### Fired-Once Guard

HP percent triggers must only fire **once** per threshold crossing.

```java
// GOOD: track which thresholds have already fired
private final Set<String> firedTriggers = new HashSet<>();

void evaluateHpTriggers(double hpPercent, Consumer<String> fire) {
    for (PhaseTrigger trigger : hpTriggers) {
        String key = "hp:" + trigger.threshold();
        if (hpPercent <= trigger.threshold() && firedTriggers.add(key)) {
            trigger.skills().forEach(fire);
        }
    }
}
```

---

## 12. Configuration — config.yml Reference

```yaml
# config.yml

learning:
  # Number of recent battles to include in global analysis (per world)
  window-size: 100

  # Players who have fought this many times or more get individual blending
  individual-blend-threshold: 3

  # Weight of individual data when blending (0.0 = global only, 1.0 = individual only)
  individual-blend-ratio: 0.3

thresholds:
  # Crystal time below this (ms) is classified as fast crystal clear
  fast-crystal-ms: 45000

  # Fight duration below this (ms) is classified as a speedrun
  speedrun-ms: 120000

dragon:
  # MythicMobs mob name to spawn as the boss
  mythicmob-name: "EngramDragon"

  # Crucible skill name for the swap animation (leave blank to use built-in)
  awakening-skill: "EngramAwakening"

  # Built-in animation duration in ticks (only used if awakening-skill is blank)
  animation-ticks: 60

animation:
  preset-enabled: true
  lightning: true
  particles: true
  sound: true
  chat-message: "&5The dragon remembers..."
  title: "???"
```

---

## 13. Commands

All commands require `engram.admin` permission unless noted.

| Command | Description |
|---|---|
| `/dragon info [world]` | Shows current strategy, confidence, and analyzed battle count |
| `/dragon reset [world]` | Wipes all battle records for the world |
| `/dragon export [world]` | Exports data to `plugins/Engram/exports/<timestamp>.json` |
| `/dragon import <file>` | Imports from a JSON export file |
| `/dragon spawn [world]` | Manually spawns the MM dragon (triggers full swap flow) |
| `/dragon reload` | Reloads `config.yml` and `strategies.yml` without restart |

### Tab Completion

All subcommands should support tab completion via Paper's `TabCompleter` or Brigadier API.

---

## 14. NG Patterns — What Not to Do

### ❌ Blocking the main thread with DB calls

```java
// NG: SQLite reads/writes block — never do this on the main thread
@EventHandler
public void onDeath(EntityDeathEvent e) {
    dataStore.saveBattleRecord(record); // blocks main thread → TPS drop
}

// GOOD: offload to async, then come back to main for any Bukkit API calls
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    dataStore.saveBattleRecord(record);
});
```

### ❌ String-concatenated SQL

```java
// NG: SQL injection risk + hard to maintain
String sql = "SELECT * FROM battle_records WHERE world_uid = '" + worldUid + "'";

// GOOD: always use PreparedStatement
PreparedStatement ps = conn.prepareStatement(
    "SELECT * FROM battle_records WHERE world_uid = ?"
);
ps.setString(1, worldUid);
```

### ❌ Listening to PlayerMoveEvent without sampling

```java
// NG: PlayerMoveEvent fires ~20x/second per player. Storing every call will flood memory.
@EventHandler
public void onMove(PlayerMoveEvent e) {
    session.recordAltitude(e.getPlayer(), e.getTo().getY()); // called thousands of times per minute
}

// GOOD: sample on a timer instead
```

### ❌ Calling MythicMobs internal APIs

```java
// NG: internal classes — break without warning on MM updates
import io.lumine.mythic.core.skills.SkillTrigger;
new SkillTrigger(...);

// GOOD: only use io.lumine.mythic.bukkit.BukkitAPIHelper or io.lumine.mythic.api.*
```

### ❌ Removing vanilla dragon on the same tick as spawn

```java
// NG: crashes world state
@EventHandler
public void onSpawn(EntitySpawnEvent e) {
    if (e.getEntity() instanceof EnderDragon d) {
        d.remove(); // entity not fully registered yet
    }
}
// GOOD: schedule removal for the next tick minimum, ideally after animation
```

### ❌ Hardcoding strategy thresholds in Java

```java
// NG: requires recompile to tune
if (bowRatio > 0.6) return StrategyType.ANTI_ARCHER;

// GOOD: read thresholds from config.yml so admins can tune without recompile
if (bowRatio > config.getBowRatioThreshold()) return StrategyType.ANTI_ARCHER;
```

### ❌ Skipping the fired-once guard on HP triggers

```java
// NG: fires every damage event while dragon is below 75% HP — skill spams
if (hpPercent <= 75) dispatcher.cast(dragon, "Phase1Skill");

// GOOD: use firedTriggers Set as shown in Section 11
```
