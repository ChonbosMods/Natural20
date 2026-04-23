# Party Quest Proximity + Mlvl Scaling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the Quest Missed eviction flow at phase-objective completion and the strength-only party mlvl scaling at mob-group spawn, per `docs/plans/2026-04-22-party-quest-proximity-and-mlvl-scaling-design.md`.

**Architecture:** Two thin pure-function cores (`Nat20PartyProximityEvictor`, `Nat20PartyMlvlScaler`) unit-tested in isolation, then wired into four objective-completion sites and four mob-spawn sites. All eviction state is modeled via a new `droppedAccepters` set on `QuestInstance`, preserving the "accepters is frozen" bedrock from the 2026-04-21 design. Deferred Quest-Missed banners queue on `Nat20PlayerData` for offline recipients and self-clean on turn-in. One shared `NAT20_PARTY_PROXIMITY = 80.0` constant drives both checks for MVP.

**Tech Stack:** Java 25, Hytale ScaffoldIt 0.2.x, JUnit 5 (already set up: see `build.gradle.kts:11-22` and existing tests at `src/test/java/com/chonbosmods/quest/`), Gson for `QuestInstance` serialization, Hytale `BuilderCodec` for `Nat20PlayerData`.

---

## Pre-flight

Before starting:

1. **Working directory has uncommitted changes** (per conversation-start `git status`). Decide before starting: either stash (`git stash push -u -m "pre-party-proximity-impl"`) or commit them. Do not intermix unrelated work with these tasks.
2. **devServer constraint:** `./gradlew devServer` cannot run from a git worktree (memory `devserver-worktree-limitation.md`). Work on `main` directly or a feature branch on the same checkout; do NOT create a worktree.
3. **Recommended branch name if using a feature branch:** `feat/party-proximity-mlvl-scaling`.
4. **Verify tests currently pass** before starting: `./gradlew test`.

Expected: green test run. If red, resolve before starting this plan.

---

## Task 1: Add `Nat20PartyTuning` constants class

**Files:**
- Create: `src/main/java/com/chonbosmods/party/Nat20PartyTuning.java`

**Step 1: Create the constants holder**

```java
package com.chonbosmods.party;

public final class Nat20PartyTuning {
    private Nat20PartyTuning() {}

    public static final double NAT20_PARTY_PROXIMITY = 80.0;
    public static final int MLVL_PARTY_CAP = 6;
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/party/Nat20PartyTuning.java
git commit -m "feat(party): add party-tuning constants class"
```

---

## Task 2: Add `droppedAccepters` field + eligibility helpers on `QuestInstance`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestInstance.java`
- Test: `src/test/java/com/chonbosmods/quest/QuestInstanceDroppedAcceptersTest.java` (create)

**Step 1: Write the failing test**

Create `src/test/java/com/chonbosmods/quest/QuestInstanceDroppedAcceptersTest.java`:

```java
package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestInstanceDroppedAcceptersTest {

    @Test
    void eligibleAccepters_excludesDropped() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        q.setAccepters(List.of(alice, bob, carol));

        q.dropAccepter(bob);

        Set<UUID> eligible = q.eligibleAccepters();
        assertEquals(Set.of(alice, carol), eligible);
    }

    @Test
    void isEligible_false_whenDropped() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        q.setAccepters(List.of(alice));

        assertTrue(q.isEligible(alice));
        q.dropAccepter(alice);
        assertFalse(q.isEligible(alice));
    }

    @Test
    void isEligible_false_whenNotAccepter() {
        QuestInstance q = new QuestInstance();
        q.setAccepters(List.of(UUID.randomUUID()));
        assertFalse(q.isEligible(UUID.randomUUID()));
    }

    @Test
    void dropAccepter_isIdempotent() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        q.setAccepters(List.of(alice));
        q.dropAccepter(alice);
        q.dropAccepter(alice);
        assertEquals(1, q.droppedAccepters().size());
    }

    @Test
    void droppedAccepters_freshQuest_isEmpty() {
        QuestInstance q = new QuestInstance();
        assertTrue(q.droppedAccepters().isEmpty());
    }
}
```

**Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.chonbosmods.quest.QuestInstanceDroppedAcceptersTest"`
Expected: compile failure on the `dropAccepter`, `droppedAccepters`, `eligibleAccepters`, `isEligible` symbols.

**Step 3: Implement on `QuestInstance`**

Add these to `QuestInstance.java` after the `accepters` field (line 29 area):

```java
// Derived eligibility: accepters evicted by party-proximity rule.
// Historical `accepters` remains frozen per 2026-04-21 §2.
private Set<UUID> droppedAccepters = new HashSet<>();

public Set<UUID> droppedAccepters() {
    if (droppedAccepters == null) droppedAccepters = new HashSet<>();
    return droppedAccepters;
}

public void dropAccepter(UUID player) {
    if (droppedAccepters == null) droppedAccepters = new HashSet<>();
    droppedAccepters.add(player);
}

public boolean isEligible(UUID player) {
    if (!accepters.contains(player)) return false;
    return droppedAccepters == null || !droppedAccepters.contains(player);
}

public Set<UUID> eligibleAccepters() {
    Set<UUID> out = new LinkedHashSet<>(accepters);
    if (droppedAccepters != null) out.removeAll(droppedAccepters);
    return out;
}
```

Add imports as needed (`HashSet`, `LinkedHashSet`, `Set`).

Note the null-check on `droppedAccepters`: legacy Gson-deserialized QuestInstances written before this field existed will have `null` here. The defensive null-check handles the migration case in-line.

**Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.chonbosmods.quest.QuestInstanceDroppedAcceptersTest"`
Expected: 5 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestInstance.java \
        src/test/java/com/chonbosmods/quest/QuestInstanceDroppedAcceptersTest.java
git commit -m "feat(quest): add droppedAccepters + eligibility helpers on QuestInstance"
```

---

## Task 3: Make `Nat20PartyQuestStore` index respect eligibility

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java`
- Test: `src/test/java/com/chonbosmods/quest/party/Nat20PartyQuestStoreEligibilityTest.java` (create)

**Step 1: Read the current store**

Read `Nat20PartyQuestStore.java` fully, particularly `add` (lines ~64-73), `queryByPlayer` (lines ~79-85), `remove` (lines ~87-96), and the `byPlayer` index. This task adds one new method and updates `queryByPlayer`.

**Step 2: Write the failing test**

Create `src/test/java/com/chonbosmods/quest/party/Nat20PartyQuestStoreEligibilityTest.java`:

```java
package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyQuestStoreEligibilityTest {

    @TempDir Path tmp;
    Nat20PartyQuestStore store;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    @BeforeEach
    void setup() {
        store = new Nat20PartyQuestStore();
        store.setSaveDirectory(tmp);
    }

    @Test
    void dropAccepter_removesFromPerPlayerIndex() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        assertEquals(1, store.queryByPlayer(alice).size());
        assertEquals(1, store.queryByPlayer(bob).size());

        store.dropAccepter("q1", bob);

        assertEquals(1, store.queryByPlayer(alice).size());
        assertEquals(0, store.queryByPlayer(bob).size(), "bob should no longer see the quest");
    }

    @Test
    void dropAccepter_missingQuest_isNoop() {
        store.dropAccepter("nonexistent", alice);
        // must not throw
    }

    @Test
    void dropAccepter_persistsAcrossReload() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);
        store.dropAccepter("q1", bob);

        Nat20PartyQuestStore reloaded = new Nat20PartyQuestStore();
        reloaded.setSaveDirectory(tmp);
        reloaded.load();

        assertEquals(0, reloaded.queryByPlayer(bob).size());
        assertTrue(reloaded.get("q1").droppedAccepters().contains(bob));
    }
}
```

**Step 3: Run to verify failure**

Run: `./gradlew test --tests "com.chonbosmods.quest.party.Nat20PartyQuestStoreEligibilityTest"`
Expected: compile failure on `store.dropAccepter` + `store.get`.

**Step 4: Implement the new store methods**

Add to `Nat20PartyQuestStore.java`:

```java
public void dropAccepter(String questId, UUID playerUuid) {
    QuestInstance q = primary.get(questId);
    if (q == null) return;
    q.dropAccepter(playerUuid);
    Set<String> indexEntry = byPlayer.get(playerUuid);
    if (indexEntry != null) {
        indexEntry.remove(questId);
        if (indexEntry.isEmpty()) byPlayer.remove(playerUuid);
    }
    save();
}

public QuestInstance get(String questId) {
    return primary.get(questId);
}
```

Also update the existing `load()` / `add()` logic so the `byPlayer` index is built from `eligibleAccepters()`, not raw `accepters`. Locate the for-loop that populates `byPlayer` on reload and change:

```java
for (UUID uuid : q.getAccepters()) { ... }
```

to:

```java
for (UUID uuid : q.eligibleAccepters()) { ... }
```

Apply the same change in `add()` (so re-adding a quest with existing droppedAccepters indexes correctly).

**Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.chonbosmods.quest.party.Nat20PartyQuestStoreEligibilityTest"`
Expected: 3 tests pass.

**Step 6: Run the full test suite to catch regressions**

Run: `./gradlew test`
Expected: all pass (existing `QuestInstanceAcceptersTest` + others).

**Step 7: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java \
        src/test/java/com/chonbosmods/quest/party/Nat20PartyQuestStoreEligibilityTest.java
git commit -m "feat(quest): honor droppedAccepters in Nat20PartyQuestStore index"
```

---

## Task 4: Pure-function proximity evictor core

**Files:**
- Create: `src/main/java/com/chonbosmods/party/Nat20PartyProximityEvictor.java`
- Test: `src/test/java/com/chonbosmods/party/Nat20PartyProximityEvictorTest.java`

The evictor logic lives in a pure function so it can be tested without the live store / world. The wiring tasks (6-9) call this pure function then apply the result to the store and banner queue.

**Step 1: Write the failing test**

```java
package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyProximityEvictorTest {

    // Position tuples are 3 doubles via a small record for the test.
    record Pos(double x, double y, double z) {}

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    UUID carol = UUID.randomUUID();

    Pos aliceAt = new Pos(0, 0, 0);
    Pos bobNear = new Pos(30, 0, 0);
    Pos bobFar = new Pos(1000, 0, 0);
    Pos origin = aliceAt;

    @Test
    void allInRange_noEvictions() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{30,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty());
    }

    @Test
    void farMemberEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{1000,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }

    @Test
    void offlineMemberEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            Optional.empty();  // bob is offline

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }

    @Test
    void triggeringPlayerNeverEvicted() {
        // Even if the position resolver fails for the triggering player, they stay.
        Function<UUID, Optional<double[]>> positions = u -> Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty());
    }

    @Test
    void exactlyAtRadius_notEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{80,0,0}) :  // exactly 80
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty(), "exactly-at-radius is inclusive");
    }

    @Test
    void justBeyondRadius_evicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{80.001,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }
}
```

**Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.chonbosmods.party.Nat20PartyProximityEvictorTest"`
Expected: compile failure on `Nat20PartyProximityEvictor.sweep`.

**Step 3: Implement the evictor**

```java
package com.chonbosmods.party;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure function: given the eligible-accepters set, the triggering player's uuid + anchor
 * position, and a position resolver, returns the set of accepters that should be evicted.
 *
 * Offline accepters (positionResolver returns empty) are evicted.
 * The triggering player is never evicted, even if their position resolver fails.
 * Distance check is inclusive at the radius.
 */
public final class Nat20PartyProximityEvictor {
    private Nat20PartyProximityEvictor() {}

    public static Set<UUID> sweep(
            Set<UUID> eligibleAccepters,
            UUID triggeringPlayer,
            double[] anchorXyz,
            Function<UUID, Optional<double[]>> positionResolver,
            double radius) {
        Set<UUID> evicted = new HashSet<>();
        double r2 = radius * radius;
        for (UUID uuid : eligibleAccepters) {
            if (uuid.equals(triggeringPlayer)) continue;
            Optional<double[]> pos = positionResolver.apply(uuid);
            if (pos.isEmpty()) { evicted.add(uuid); continue; }
            double[] p = pos.get();
            double dx = p[0] - anchorXyz[0];
            double dy = p[1] - anchorXyz[1];
            double dz = p[2] - anchorXyz[2];
            if (dx*dx + dy*dy + dz*dz > r2) evicted.add(uuid);
        }
        return evicted;
    }
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "com.chonbosmods.party.Nat20PartyProximityEvictorTest"`
Expected: 6 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/party/Nat20PartyProximityEvictor.java \
        src/test/java/com/chonbosmods/party/Nat20PartyProximityEvictorTest.java
git commit -m "feat(party): add pure proximity-evictor function"
```

---

## Task 5: Pure-function mlvl scaler core

**Files:**
- Create: `src/main/java/com/chonbosmods/party/Nat20PartyMlvlScaler.java`
- Test: `src/test/java/com/chonbosmods/party/Nat20PartyMlvlScalerTest.java`

**Step 1: Write the failing test**

```java
package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import static com.chonbosmods.party.Nat20PartyTuning.MLVL_PARTY_CAP;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyMlvlScalerTest {

    @Test
    void solo_noBump() {
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 1));
    }

    @Test
    void partyOfTwo_plusOne() {
        assertEquals(6, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 2));
    }

    @Test
    void partyOfFour_plusThree() {
        assertEquals(8, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 4));
    }

    @Test
    void cappedAtMlvlPartyCap() {
        // baseMlvl=5, nearby=20 -> bump is 19 but capped at MLVL_PARTY_CAP
        assertEquals(5 + MLVL_PARTY_CAP,
                Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 20));
    }

    @Test
    void zeroNearby_treatedAsSolo() {
        // Edge case: resolver returned 0 (shouldn't happen but defensive).
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 0));
    }

    @Test
    void negativeNearby_treatedAsSolo() {
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, -1));
    }
}
```

**Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.chonbosmods.party.Nat20PartyMlvlScalerTest"`
Expected: compile failure on `Nat20PartyMlvlScaler.computeEffectiveMlvl`.

**Step 3: Implement**

```java
package com.chonbosmods.party;

/**
 * Party-size mlvl scaling. Pure function form for testability.
 * The wiring-layer call (apply) uses the live PartyRegistry; test code uses
 * computeEffectiveMlvl directly with a hand-picked nearbyCount.
 */
public final class Nat20PartyMlvlScaler {
    private Nat20PartyMlvlScaler() {}

    public static int computeEffectiveMlvl(int baseMlvl, int nearbyCount) {
        int bump = Math.min(Math.max(0, nearbyCount - 1), Nat20PartyTuning.MLVL_PARTY_CAP);
        return baseMlvl + bump;
    }
}
```

The live `apply(int baseMlvl, UUID triggeringPlayerUuid)` overload is added in Task 10 when the call sites are wired.

**Step 4: Run tests**

Run: `./gradlew test --tests "com.chonbosmods.party.Nat20PartyMlvlScalerTest"`
Expected: 6 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/party/Nat20PartyMlvlScaler.java \
        src/test/java/com/chonbosmods/party/Nat20PartyMlvlScalerTest.java
git commit -m "feat(party): add pure mlvl-scaler function"
```

---

## Task 6: `QuestMissedBanner` + `PendingQuestMissedBanner` record

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/QuestMissedBanner.java`
- Create: `src/main/java/com/chonbosmods/quest/PendingQuestMissedBanner.java`

**Step 1: Create `PendingQuestMissedBanner`**

```java
package com.chonbosmods.quest;

public record PendingQuestMissedBanner(
        String questId,
        String topicHeader,
        long queuedAtEpochMs) {}
```

**Step 2: Create `QuestMissedBanner`**

Mirror `QuestCompletionBanner.java` exactly. Read it first to confirm the current signature, then:

```java
package com.chonbosmods.quest;

import server.util.EventTitleUtil;
import server.util.Message;
import server.player.PlayerRef;
import java.util.logging.Logger;  // adjust to match project logging (see HytaleLogger pattern)

public final class QuestMissedBanner {
    private static final /* HytaleLogger */ ??? LOGGER = /* match QuestCompletionBanner */;
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private QuestMissedBanner() {}

    public static void show(PlayerRef player, String topicHeader) {
        LOGGER.atInfo().log("Firing Quest Missed banner: header='%s', player=%s",
                topicHeader, player.getUuid());
        EventTitleUtil.showEventTitleToPlayer(
                player,
                Message.raw("Quest Missed"),
                Message.raw(topicHeader),
                true,
                null,
                TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }

    public static void show(PlayerRef player, QuestInstance quest) {
        String topicHeader = quest.getVariableBindings()
                .getOrDefault("quest_topic_header", "Quest");
        show(player, topicHeader);
    }
}
```

**Read `QuestCompletionBanner.java` first** to copy the exact logger import and pattern. Substitute the `???` above to match.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestMissedBanner.java \
        src/main/java/com/chonbosmods/quest/PendingQuestMissedBanner.java
git commit -m "feat(quest): add QuestMissedBanner + pending-banner record"
```

---

## Task 7: Queue + drain pending banners on `Nat20PlayerData`

**Files:**
- Modify: `src/main/java/com/chonbosmods/data/Nat20PlayerData.java`

**Background:** `Nat20PlayerData` uses `BuilderCodec` (see lines 39-63). A new `List<PendingQuestMissedBanner>` field needs a matching `KeyedCodec` entry with a PascalCase key (memory: `hytale-codec-config` — KeyedCodec keys MUST be PascalCase).

**Step 1: Read `Nat20PlayerData.java` end-to-end**

Confirm the exact codec shape, the `completedQuests` field's KeyedCodec (use it as a template — both are lists of records).

**Step 2: Add the field**

Add after `completedQuests` field declaration:

```java
private List<PendingQuestMissedBanner> pendingQuestMissedBanners = new ArrayList<>();
```

Add getter + mutator:

```java
public List<PendingQuestMissedBanner> getPendingQuestMissedBanners() {
    if (pendingQuestMissedBanners == null) pendingQuestMissedBanners = new ArrayList<>();
    return pendingQuestMissedBanners;
}

public void addPendingQuestMissedBanner(PendingQuestMissedBanner b) {
    getPendingQuestMissedBanners().add(b);
}

public List<PendingQuestMissedBanner> drainPendingQuestMissedBanners() {
    List<PendingQuestMissedBanner> drained = new ArrayList<>(getPendingQuestMissedBanners());
    getPendingQuestMissedBanners().clear();
    return drained;
}

public void removePendingQuestMissedBanner(String questId) {
    getPendingQuestMissedBanners().removeIf(b -> b.questId().equals(questId));
}
```

**Step 3: Add a `KeyedCodec<PendingQuestMissedBanner>` entry to the codec definition**

Follow the `completedQuests` pattern. The PascalCase key: `"PendingQuestMissedBanners"`. The per-record codec needs three fields: `"QuestId"` (String), `"TopicHeader"` (String), `"QueuedAtEpochMs"` (Long).

If `PendingQuestMissedBanner` as a `record` doesn't play nicely with `BuilderCodec`, introduce a tiny companion codec using the same pattern `CompletedQuestRecord` uses. Read `CompletedQuestRecord.java` first for the exact shape, mirror it.

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/data/Nat20PlayerData.java \
        src/main/java/com/chonbosmods/quest/PendingQuestMissedBanner.java
git commit -m "feat(data): queue pending Quest-Missed banners on Nat20PlayerData"
```

---

## Task 8: Drain pending banners on `PlayerReadyEvent`

**Files:**
- Modify: `src/main/java/com/chonbosmods/Natural20.java`

**Step 1: Read the existing PlayerReadyEvent handler**

Around line 785+. Identify where `Nat20PlayerData data` is fetched, before existing quest-waypoint restoration logic (around line 809).

**Step 2: Add the drain block**

After the migration block, before existing quest-waypoint restoration:

```java
// Drain pending Quest-Missed banners queued while offline.
List<PendingQuestMissedBanner> pending = data.drainPendingQuestMissedBanners();
if (!pending.isEmpty()) {
    PlayerRef playerRef = event.getPlayerRef();
    event.getPlayerRef().getWorld().execute(() -> {
        for (PendingQuestMissedBanner b : pending) {
            QuestMissedBanner.show(playerRef, b.topicHeader());
        }
    });
}
```

Add imports for `PendingQuestMissedBanner`, `QuestMissedBanner`, `List`.

**Why `world.execute`:** memory `custom-page-open-world-thread` — banner rendering must run on the world thread; PlayerReadyEvent may fire on a different thread.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(quest): drain pending Quest-Missed banners on PlayerReadyEvent"
```

---

## Task 9: Central eviction chokepoint `Nat20QuestProximityEnforcer`

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/Nat20QuestProximityEnforcer.java`
- Test: `src/test/java/com/chonbosmods/quest/Nat20QuestProximityEnforcerTest.java`

This is the single method every objective-completion site calls. It uses the pure evictor from Task 4, then applies the result via the store + banner queue.

**Step 1: Write the failing test**

Test the enforcer with a stub store + stub banner-dispatcher:

```java
package com.chonbosmods.quest;

import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class Nat20QuestProximityEnforcerTest {

    @TempDir Path tmp;
    Nat20PartyQuestStore store;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    List<UUID> onlineBannersFired = new ArrayList<>();
    List<UUID> offlineBannersQueued = new ArrayList<>();

    @BeforeEach
    void setup() {
        store = new Nat20PartyQuestStore();
        store.setSaveDirectory(tmp);
    }

    @Test
    void sweep_evictsBob_andFiresOnlineBanner() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        q.getVariableBindings().put("quest_topic_header", "Saving the Orchard");
        store.add(q);

        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{500,0,0}) :
            Optional.empty();
        Predicate<UUID> online = u -> true;  // both online

        Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
            q, alice, new double[]{0,0,0},
            positions, online,
            store,
            (uuid, topicHeader) -> onlineBannersFired.add(uuid),
            (uuid, pending) -> offlineBannersQueued.add(uuid));

        assertTrue(q.droppedAccepters().contains(bob));
        assertEquals(List.of(bob), onlineBannersFired);
        assertTrue(offlineBannersQueued.isEmpty());
    }

    @Test
    void sweep_offlineBob_queuesBannerInsteadOfFiring() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) : Optional.empty();
        Predicate<UUID> online = u -> u.equals(alice);

        Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
            q, alice, new double[]{0,0,0},
            positions, online,
            store,
            (uuid, topicHeader) -> onlineBannersFired.add(uuid),
            (uuid, pending) -> offlineBannersQueued.add(uuid));

        assertEquals(List.of(bob), offlineBannersQueued);
        assertTrue(onlineBannersFired.isEmpty());
    }
}
```

**Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.chonbosmods.quest.Nat20QuestProximityEnforcerTest"`
Expected: compile failure on `Nat20QuestProximityEnforcer.sweepForPhaseCompletion`.

**Step 3: Implement the enforcer**

```java
package com.chonbosmods.quest;

import com.chonbosmods.party.Nat20PartyProximityEvictor;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import java.util.*;
import java.util.function.*;

public final class Nat20QuestProximityEnforcer {
    private Nat20QuestProximityEnforcer() {}

    @FunctionalInterface
    public interface OnlineBannerDispatcher {
        void fire(UUID playerUuid, String topicHeader);
    }

    @FunctionalInterface
    public interface OfflineBannerQueuer {
        void queue(UUID playerUuid, PendingQuestMissedBanner pending);
    }

    /**
     * Evict eligible accepters outside radius or offline. Ghost-safe:
     * offline evictees get a pending banner, online evictees get an immediate banner.
     * The triggering player is never evicted.
     */
    public static void sweepForPhaseCompletion(
            QuestInstance quest,
            UUID triggeringPlayer,
            double[] anchorXyz,
            Function<UUID, Optional<double[]>> positionResolver,
            Predicate<UUID> isOnline,
            Nat20PartyQuestStore store,
            OnlineBannerDispatcher online,
            OfflineBannerQueuer offline) {
        Set<UUID> toEvict = Nat20PartyProximityEvictor.sweep(
                quest.eligibleAccepters(),
                triggeringPlayer,
                anchorXyz,
                positionResolver,
                com.chonbosmods.party.Nat20PartyTuning.NAT20_PARTY_PROXIMITY);
        if (toEvict.isEmpty()) return;

        String topicHeader = quest.getVariableBindings()
                .getOrDefault("quest_topic_header", "Quest");
        for (UUID uuid : toEvict) {
            store.dropAccepter(quest.getQuestId(), uuid);
            if (isOnline.test(uuid)) {
                online.fire(uuid, topicHeader);
            } else {
                offline.queue(uuid, new PendingQuestMissedBanner(
                        quest.getQuestId(), topicHeader, System.currentTimeMillis()));
            }
        }
    }
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "com.chonbosmods.quest.Nat20QuestProximityEnforcerTest"`
Expected: 2 tests pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/Nat20QuestProximityEnforcer.java \
        src/test/java/com/chonbosmods/quest/Nat20QuestProximityEnforcerTest.java
git commit -m "feat(quest): central proximity enforcer chokepoint"
```

---

## Task 10: Live-store wiring helper `Nat20QuestProximityGate`

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/Nat20QuestProximityGate.java`

This is the adapter between the pure enforcer and the live runtime (party registry, ECS store, player-ref lookup, banner firing on world thread). The four per-objective call sites invoke `Nat20QuestProximityGate.check(quest, triggeringPlayer)` and it handles the rest.

**Step 1: Read supporting infrastructure**

Before writing, read:
- `Nat20PartyRegistry.java` for the exact `getParty(UUID)` and `isOnline(UUID)` signatures.
- `AmbientSpawnSystem.java` around line 120 for the `TransformComponent.getPosition()` pattern on the live store.
- `QuestCompletionBanner.java` for the PlayerRef -> banner dispatch.

**Step 2: Implement**

```java
package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.party.Nat20PartyRegistry;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import server.ecs.EntityStore;
import server.ecs.Ref;
import server.ecs.Store;
import server.ecs.TransformComponent;
import server.player.PlayerRef;
import server.world.World;

import java.util.Optional;
import java.util.UUID;

public final class Nat20QuestProximityGate {
    private Nat20QuestProximityGate() {}

    /**
     * Entry point for every objective-completion site. Runs on the world thread.
     */
    public static void check(
            QuestInstance quest,
            UUID triggeringPlayer,
            double[] anchorXyz,
            World world,
            Natural20 plugin) {
        Nat20PartyRegistry registry = Nat20PartyRegistry.get();
        Nat20PartyQuestStore store = plugin.getPartyQuestStore();  // add accessor if missing

        Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
                quest,
                triggeringPlayer,
                anchorXyz,
                uuid -> resolvePosition(uuid, world),
                registry::isOnline,
                store,
                (uuid, topicHeader) -> firePlayerBanner(uuid, topicHeader, world, plugin),
                (uuid, pending) -> queueOfflineBanner(uuid, pending, plugin));
    }

    private static Optional<double[]> resolvePosition(UUID uuid, World world) {
        PlayerRef ref = world.getPlayerRegistry().getOnline(uuid);
        if (ref == null) return Optional.empty();
        Store<EntityStore> store = ref.getStore();
        Ref<EntityStore> entityRef = ref.getEntityRef();
        var tx = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (tx == null) return Optional.empty();
        var pos = tx.getPosition();
        return Optional.of(new double[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private static void firePlayerBanner(UUID uuid, String topicHeader, World world, Natural20 plugin) {
        PlayerRef ref = world.getPlayerRegistry().getOnline(uuid);
        if (ref == null) return;  // went offline between sweep and dispatch; already handled as offline
        QuestMissedBanner.show(ref, topicHeader);
    }

    private static void queueOfflineBanner(UUID uuid, PendingQuestMissedBanner pending, Natural20 plugin) {
        // Offline player's Nat20PlayerData is not guaranteed to be in-memory.
        // Use plugin's player-data registry to load + mutate + save for offline members.
        plugin.mutateOfflinePlayerData(uuid, data -> data.addPendingQuestMissedBanner(pending));
    }
}
```

**Note:** `plugin.mutateOfflinePlayerData` may not exist. If it doesn't, check how `completedQuests` writes for offline accepters at turn-in (same problem: an offline accepter gets a completed-quest record). Reuse that pattern — it is the existing precedent for offline-mutate in this system.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. If missing accessors, add them to `Natural20.java`:

```java
public Nat20PartyQuestStore getPartyQuestStore() { return partyQuestStore; }
```

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/Nat20QuestProximityGate.java \
        src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(quest): live-runtime gate adapting proximity enforcer to ECS"
```

---

## Task 11: Wire `Nat20QuestProximityGate` into `POIKillTrackingSystem`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/POIKillTrackingSystem.java`

**Step 1: Read `creditOwner` (around lines 99, 144-154)**

Confirm: `ownerUuid` is the killer, objective completion fires when the tracked mob dies and the objective counter hits target. You need the killer's current position to anchor the sweep.

**Step 2: Add the gate call at phase-completion moment**

Locate where the objective transitions from incomplete to complete (likely `objective.markComplete()` or a counter-hit check). Immediately after that transition — and before any downstream phase-advance / banner — call the gate:

```java
if (objectiveJustCompleted) {
    double[] anchor = getKillerPosition(ownerUuid, world);  // or reuse killer's existing Vec3 in scope
    Nat20QuestProximityGate.check(quest, ownerUuid, anchor, world, plugin);
}
```

Use whatever position variable is already in scope for the killer — most tracking systems have the triggering player's position available since they needed it to check damage contribution.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/POIKillTrackingSystem.java
git commit -m "feat(quest): apply party proximity check on KILL objective completion"
```

---

## Task 12: Wire into `CollectResourceTrackingSystem`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/CollectResourceTrackingSystem.java`

**Step 1: Read the objective-completion path**

Around lines 33-80+. The triggering player is the `Ref<EntityStore> ref` / `player` variable at the completion site. Extract their UUID + position via `TransformComponent`.

**Step 2: Add the gate call**

At the moment the objective transitions to complete:

```java
if (objectiveJustCompleted) {
    UUID triggeringUuid = /* extract from ref */;
    var tx = store.getComponent(ref, TransformComponent.getComponentType());
    double[] anchor = {tx.getPosition().getX(), tx.getPosition().getY(), tx.getPosition().getZ()};
    Nat20QuestProximityGate.check(quest, triggeringUuid, anchor, world, plugin);
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/CollectResourceTrackingSystem.java
git commit -m "feat(quest): apply party proximity check on COLLECT objective completion"
```

---

## Task 13: Wire into `FetchItemTrackingSystem`

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/FetchItemTrackingSystem.java`

Same pattern as Task 12. Read the file, locate the objective-completion transition, insert the `Nat20QuestProximityGate.check(...)` call with the fetcher as the triggering player.

**Step 1-3:** Mirror Task 12.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/FetchItemTrackingSystem.java
git commit -m "feat(quest): apply party proximity check on FETCH objective completion"
```

---

## Task 14: Wire into TALK objective completion

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` (around line 78, `COMPLETE_TALK_TO_NPC`)

**Step 1: Read the COMPLETE_TALK_TO_NPC action handler**

Triggering player is the player who closed the dialogue, available from `DialogueActionContext`. Position: their current player-entity position.

**Step 2: Add the gate call at phase-completion moment**

After the objective is marked complete and before phase-advance:

```java
// Quest-Missed proximity gate: evict party accepters outside 80 blocks
// (or offline) before phase-advance propagates.
var playerRef = context.getPlayerRef();
var tx = playerRef.getStore().getComponent(playerRef.getEntityRef(),
    TransformComponent.getComponentType());
double[] anchor = {tx.getPosition().getX(), tx.getPosition().getY(), tx.getPosition().getZ()};
Nat20QuestProximityGate.check(quest, playerRef.getUuid(), anchor,
    playerRef.getWorld(), plugin);
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat(quest): apply party proximity check on TALK objective completion"
```

---

## Task 15: Ghost-case self-cleaning at turn-in

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java` (`turnIn` method)

**Step 1: Read `turnIn` method** (around lines 130-135)

Currently it iterates accepters for reward distribution. It needs to additionally iterate `droppedAccepters` and purge pending banners for each.

**Step 2: Modify**

```java
public void turnIn(String questId, CompletionSink sink) {
    QuestInstance q = primary.get(questId);
    if (q == null) return;
    // Reward surviving accepters.
    for (UUID uuid : q.eligibleAccepters()) {
        sink.completeFor(uuid, q);
    }
    // Purge pending banners for dropped accepters (ghost-case self-clean).
    for (UUID uuid : q.droppedAccepters()) {
        sink.purgePendingBannerFor(uuid, questId);
    }
    remove(questId);
}
```

Update `CompletionSink` interface in the same file (or its adjacent definition) to include:

```java
void purgePendingBannerFor(UUID uuid, String questId);
```

**Step 3: Update the `CompletionSink` implementation**

Find where the sink is instantiated (likely `QuestStateManager` or `Natural20.java`) and add the method, routing to `Nat20PlayerData.removePendingQuestMissedBanner(questId)` for the uuid (same offline-mutate pattern as Task 10).

**Step 4: Verify compilation + run all tests**

Run: `./gradlew test`
Expected: all pass.

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java \
        src/main/java/com/chonbosmods/Natural20.java  # or wherever the sink lives
git commit -m "feat(quest): self-clean pending Quest-Missed banners on turn-in"
```

---

## Task 16: Add `Nat20PartyMlvlScaler.apply` live overload

**Files:**
- Modify: `src/main/java/com/chonbosmods/party/Nat20PartyMlvlScaler.java`

**Step 1: Add live-runtime overload**

Add to the class (keeping the pure `computeEffectiveMlvl` unchanged):

```java
public static int apply(int baseMlvl, UUID triggeringPlayer, World world) {
    Nat20PartyRegistry registry = Nat20PartyRegistry.get();
    Nat20Party party = registry.getParty(triggeringPlayer);

    PlayerRef triggerRef = world.getPlayerRegistry().getOnline(triggeringPlayer);
    if (triggerRef == null) return baseMlvl;  // shouldn't happen; defensive
    var tx = triggerRef.getStore().getComponent(triggerRef.getEntityRef(),
            TransformComponent.getComponentType());
    Vector3d anchor = tx.getPosition();

    int nearby = NearbyPartyCount.count(
            party.getMembers(),
            registry.getOnlineSet(),  // add this accessor if missing
            uuid -> {
                PlayerRef r = world.getPlayerRegistry().getOnline(uuid);
                if (r == null) return Double.MAX_VALUE;
                var txm = r.getStore().getComponent(r.getEntityRef(),
                        TransformComponent.getComponentType());
                if (txm == null) return Double.MAX_VALUE;
                return txm.getPosition().distanceTo(anchor);
            },
            Nat20PartyTuning.NAT20_PARTY_PROXIMITY);

    return computeEffectiveMlvl(baseMlvl, nearby);
}
```

Note: `NearbyPartyCount.count` signature per explore: `(List<UUID> members, Set<UUID> online, Function<UUID, Double> distance, double radius)`. If `registry.getOnlineSet()` doesn't exist, add it (it wraps the internal online map).

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/party/Nat20PartyMlvlScaler.java \
        src/main/java/com/chonbosmods/party/Nat20PartyRegistry.java  # if accessor added
git commit -m "feat(party): add live apply(baseMlvl, player, world) overload"
```

---

## Task 17: Wire `Nat20PartyMlvlScaler.apply` at four spawn sites

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/poi/POIGroupSpawnCoordinator.java`
- Modify: `src/main/java/com/chonbosmods/progression/ambient/AmbientSpawnSystem.java`
- Modify: `src/main/java/com/chonbosmods/commands/SpawnGroupCommand.java`
- Modify: `src/main/java/com/chonbosmods/quest/POIProximitySystem.java` (if POI-authored spawn path is distinct from coordinator)

**Step 1: Locate the base-mlvl determination at each site**

For each file, find where the base mlvl (pre-party-bump) is established. Route it through `Nat20PartyMlvlScaler.apply(baseMlvl, triggeringPlayerUuid, world)` before it feeds into the spawner.

**POIGroupSpawnCoordinator.firstSpawn (line 60+):**
`playerUuid` is in scope. Replace the bare base-mlvl with `Nat20PartyMlvlScaler.apply(baseMlvl, playerUuid, world)` at the line just before `scaleSystem.rollDifficultyWeighted` (around 87-88).

**AmbientSpawnSystem (line 240+):**
`playerUuid` in scope. Same pattern around line 252.

**SpawnGroupCommand (line 43-65):**
Convert `playerRef.getUuid()` to uuid, then route the base mlvl (currently derived inside spawner since `forcedDifficulty` is null). This one requires either:
(a) pre-rolling the base mlvl in the command, applying the scaler, then passing it to the spawner as a forced parameter, OR
(b) teaching `Nat20MobGroupSpawner.spawnGroup` to take an optional triggering-player parameter and do the scaling internally.

**Recommendation: (a)** — keeps the scaler at the call-site level and leaves `Nat20MobGroupSpawner` ignorant of party concerns. Read the command file first; if it's a thin dev-debug tool, just skip the scaling here (it's a dev command and accurate mlvl-scaling isn't load-bearing for testing). Note this as a known limitation in the commit message.

**POIProximitySystem (line 110+):**
Delegates to `POIGroupSpawnCoordinator.firstSpawn` — scaling happens in the coordinator. No change needed if Task 17 already handled coordinator. Verify the delegation goes through `POIGroupSpawnCoordinator` and NOT through a separate spawn path.

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/poi/POIGroupSpawnCoordinator.java \
        src/main/java/com/chonbosmods/progression/ambient/AmbientSpawnSystem.java \
        src/main/java/com/chonbosmods/commands/SpawnGroupCommand.java
git commit -m "feat(party): route base mlvl through party-size scaler at spawn sites"
```

---

## Task 18: Full regression test pass

**Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: all pass. Any red test is a regression to fix before smoke-testing.

**Step 2: Compile-and-run sanity**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

**Step 3: Commit (empty-ish) only if you had to fix regressions**

```bash
# Only if Step 1 surfaced problems that required fixes.
git commit -am "fix: <specific regression>"
```

---

## Task 19: Manual smoke test

devServer does not run from worktrees (memory `devserver-worktree-limitation`). If you worked on a branch, merge to `main` first or switch back to main. Then:

```bash
./gradlew devServer
```

**Smoke script.** Run each with 2 test accounts (or one account + a mock party member via an admin command if available). Mark PASS/FAIL per step in the commit message or as a separate notes file.

### 19.1 Baseline: solo party of 1, no behavior change

1. Connect as a single player.
2. Accept a fetch or kill quest from an NPC.
3. Complete all phases solo.
4. Expected: quest completes normally. `Nat20QuestProximityGate` loop is a no-op.

**PASS criteria:** Quest Completed banner fires, rewards delivered, no log errors.

### 19.2 Party of 2, both in range — no eviction

1. Form a party of 2.
2. Both stand within ~40 blocks of each other.
3. Accept a KILL quest together.
4. Finish the final kill with both nearby.
5. Complete turn-in.

**PASS criteria:** Both receive rewards, both see Quest Completed banner at turn-in. No Quest Missed banner fires.

### 19.3 Party of 2, one far — eviction + Quest Missed banner

1. Form a party of 2. Both accept a KILL quest.
2. Party member B teleports 500 blocks away.
3. Party member A finishes the final kill of the phase.
4. Expected at the moment the phase objective completes:
   - Player B sees "Quest Missed" banner immediately.
   - Player B's quest log no longer shows the quest.
   - Player A's quest advances to next phase (or READY_FOR_TURN_IN).

**PASS criteria:** Banner fires for B, quest gone from B's log, A continues normally.

### 19.4 Offline-at-completion — deferred banner

1. Party of 2. Both accept quest. B logs off.
2. A finishes the final phase-completing event.
3. B logs back in.

**PASS criteria:** B sees Quest Missed banner on login. Quest is not in their active log.

### 19.5 Ghost case — silent cleanup

1. Party of 2. Both accept quest. B logs off.
2. A completes all phases + turn-in.
3. B logs back in.

**PASS criteria:** B sees NOTHING. No Quest Missed banner, no Quest Completed banner, no reward.

### 19.6 Mlvl scaling — solo vs party of 3

1. Solo player triggers a POI spawn in a zone with base mlvl 5. Record mob HP/damage in combat logs.
2. Form a party of 3, all within 80 blocks of POI entry. Trigger the spawn.
3. Expected party spawn: mobs at mlvl 7 (base + (3-1) = +2). HP/damage curves proportionally higher.

**PASS criteria:** Mob HP/damage observably scales up with party size. Mlvl delta matches formula.

### 19.7 Mlvl cap at +6

1. Difficult to test without 8 accounts. Skip or simulate via a `/nat20` debug command that takes an explicit nearby-count parameter if one exists.
2. If skipped, note this as "Not playtested; relies on `Nat20PartyMlvlScalerTest.cappedAtMlvlPartyCap` unit test."

---

## Task 20: Update memory + status

**Step 1: Update the project memory note**

Edit `/home/keroppi/.claude/projects/-home-keroppi-Development-Hytale-Natural20/memory/party-proximity-and-mlvl-scaling.md`: append a status line like:

> Implementation status (2026-04-22): All 19 tasks shipped. Smoke tests 19.1-19.6 PASS, 19.7 skipped. Commits <first>..<last> on main.

**Step 2: Commit the memory update**

Memory is outside the repo so no git commit needed there.

---

## Known shortcomings / deferred

- **Group-size scaling** (more mobs / more champions per party) is not implemented. Strength-only per design §6.
- **Decoupled proximity radii** for mlvl-scaling vs quest-credit are not split. Single `NAT20_PARTY_PROXIMITY` drives both.
- **Quest Missed banner icon:** null (no icon). Followup: greyed completion icon if art gets made.
- **Banner flood on login:** ghost with 20 missed quests gets 20 banners on connect. Low priority per design §9.
- **`SpawnGroupCommand` may skip scaling** if the command path doesn't expose a base-mlvl pre-roll cleanly. Noted in Task 17 if skipped.

---

## Rollback plan

If smoke testing surfaces a blocking regression, revert in reverse commit order. The implementation is broken into ~18 small commits specifically to make single-commit rollbacks viable. The data-model commits (Tasks 2-3) are the only ones requiring care: a rollback after quests already carry `droppedAccepters` data will leave that field ignored in JSON; `QuestInstance` will deserialize fine (Gson ignores unknown fields) but any already-evicted players would be silently re-eligible.
