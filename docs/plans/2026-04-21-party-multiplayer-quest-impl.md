# Party & Multiplayer Quest Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce a server-side Party abstraction (every player in a party of 1 by default, growing via invites), migrate active-quest storage into a single quest-keyed `Nat20PartyQuestStore` with an accepters-immutable rule, and scale mob mlvl by nearby online party members. All behavior changes are gated by unit tests written test-first.

**Architecture:** See `docs/plans/2026-04-21-party-multiplayer-quest-design.md` for the full locked design. Core: `QuestInstance` gains `accepters: List<UUID>`. The store holds primary `Map<questId, QuestInstance>` and a secondary `Map<PlayerUuid, Set<questId>>` index. `QuestStateManager.getActiveQuests(player)` becomes a read into the store, filtered by the index. `Nat20Party` + `Nat20PartyRegistry` own membership mechanics. Completion remains per-player on `Nat20PlayerData.completedQuests`.

**Tech Stack:** Java 25, Gradle (`./gradlew test`, `./gradlew compileJava`), JUnit (style matches existing tests in `src/test/java/com/chonbosmods/**`), Gson for persistence.

**Execution notes:**
- Slices 1–4 run inside a git worktree and are pure unit-test territory. `./gradlew test --tests` per file.
- Slices 5–6 need devserver for smoke testing; they run on `main` after slices 1–4 land.
- Commit after every GREEN. Commit messages follow conventional prefixes (`test:`, `feat:`, `refactor:`).
- User instruction: no `Co-Authored-By` lines, no pushing without being told to.

---

## Slice 1 — Nat20PartyQuestStore (TDD, worktree-safe)

New package: `com.chonbosmods.quest.party`. The store is a single-instance server-global (held by `Natural20.java` as a field, consistent with other nat20 singletons). Keyed by the existing `QuestInstance.questId` string (already unique today because `preGeneratedQuest` is consumed at accept time).

### Task 1.1 — QuestInstance gains accepters field

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestInstance.java`
- Test: `src/test/java/com/chonbosmods/quest/QuestInstanceAcceptersTest.java` (create)

**Step 1 — Write failing test**

```java
package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestInstanceAcceptersTest {

    @Test
    void newInstanceHasEmptyAcceptersByDefault() {
        QuestInstance q = new QuestInstance();
        assertNotNull(q.getAccepters(), "accepters must be non-null for legacy-deserialized instances");
        assertTrue(q.getAccepters().isEmpty());
    }

    @Test
    void setAcceptersStoresProvidedUuids() {
        QuestInstance q = new QuestInstance();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        q.setAccepters(List.of(a, b));
        assertEquals(List.of(a, b), q.getAccepters());
    }

    @Test
    void hasAccepterReturnsTrueForMemberAndFalseForNon() {
        QuestInstance q = new QuestInstance();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        q.setAccepters(List.of(a));
        assertTrue(q.hasAccepter(a));
        assertFalse(q.hasAccepter(b));
    }
}
```

**Step 2 — Verify RED**

Run: `./gradlew test --tests com.chonbosmods.quest.QuestInstanceAcceptersTest`
Expected: compile failure (`getAccepters`, `setAccepters`, `hasAccepter` missing) or fail.

**Step 3 — Minimal implementation**

In `QuestInstance.java` add:

```java
private List<UUID> accepters = new ArrayList<>();

public List<UUID> getAccepters() {
    if (accepters == null) accepters = new ArrayList<>(); // legacy Gson round-trip
    return accepters;
}

public void setAccepters(List<UUID> accepters) {
    this.accepters = accepters == null ? new ArrayList<>() : new ArrayList<>(accepters);
}

public boolean hasAccepter(UUID player) {
    return getAccepters().contains(player);
}
```

And add `import java.util.UUID;` at the top.

**Step 4 — Verify GREEN**

Run: `./gradlew test --tests com.chonbosmods.quest.QuestInstanceAcceptersTest`
Expected: 3 tests pass.

**Step 5 — Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestInstance.java \
        src/test/java/com/chonbosmods/quest/QuestInstanceAcceptersTest.java
git commit -m "test(quest): QuestInstance carries an accepters UUID list"
```

---

### Task 1.2 — Nat20PartyQuestStore.add and getById

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java`
- Test: `src/test/java/com/chonbosmods/quest/party/Nat20PartyQuestStoreTest.java`

**Step 1 — Failing test**

```java
package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyQuestStoreTest {

    @Test
    void addAndGetByIdRoundTrip() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestInstance q = new QuestInstance();
        q.setQuestId("q-001");
        q.setAccepters(List.of(UUID.randomUUID()));

        store.add(q);

        assertSame(q, store.getById("q-001"));
    }

    @Test
    void getByIdReturnsNullForUnknown() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        assertNull(store.getById("nope"));
    }

    @Test
    void addRejectsQuestWithoutQuestId() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestInstance q = new QuestInstance();
        q.setAccepters(List.of(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> store.add(q));
    }
}
```

Note: `setQuestId` does not exist on `QuestInstance` yet. Add a setter in this task.

**Step 2 — Verify RED** (compile failure for `setQuestId` and missing class)

**Step 3 — Minimal implementation**

Add `public void setQuestId(String questId) { this.questId = questId; }` to `QuestInstance.java`.

Create `Nat20PartyQuestStore.java`:

```java
package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import java.util.HashMap;
import java.util.Map;

public class Nat20PartyQuestStore {
    private final Map<String, QuestInstance> primary = new HashMap<>();

    public void add(QuestInstance quest) {
        String id = quest.getQuestId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("QuestInstance must have a questId before add()");
        }
        primary.put(id, quest);
    }

    public QuestInstance getById(String questId) {
        return primary.get(questId);
    }
}
```

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git add src/main/java/com/chonbosmods/quest/party/Nat20PartyQuestStore.java \
        src/main/java/com/chonbosmods/quest/QuestInstance.java \
        src/test/java/com/chonbosmods/quest/party/Nat20PartyQuestStoreTest.java
git commit -m "test(quest): Nat20PartyQuestStore add/getById"
```

---

### Task 1.3 — queryByPlayer via secondary index

**Files:**
- Modify: `Nat20PartyQuestStore.java`
- Modify: `Nat20PartyQuestStoreTest.java`

**Step 1 — Failing tests (append to existing test file)**

```java
@Test
void queryByPlayerReturnsQuestsWherePlayerIsAccepter() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();

    QuestInstance q1 = new QuestInstance();
    q1.setQuestId("q1");
    q1.setAccepters(List.of(alice));

    QuestInstance q2 = new QuestInstance();
    q2.setQuestId("q2");
    q2.setAccepters(List.of(alice, bob));

    QuestInstance q3 = new QuestInstance();
    q3.setQuestId("q3");
    q3.setAccepters(List.of(bob));

    store.add(q1); store.add(q2); store.add(q3);

    assertEquals(Set.of("q1", "q2"), idsOf(store.queryByPlayer(alice)));
    assertEquals(Set.of("q2", "q3"), idsOf(store.queryByPlayer(bob)));
    assertEquals(Set.of(),          idsOf(store.queryByPlayer(UUID.randomUUID())));
}

@Test
void queryByPlayerNeverReturnsNull() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    assertNotNull(store.queryByPlayer(UUID.randomUUID()));
}

private static Set<String> idsOf(Collection<QuestInstance> quests) {
    return quests.stream().map(QuestInstance::getQuestId).collect(Collectors.toSet());
}
```

Add imports: `java.util.Set`, `java.util.Collection`, `java.util.stream.Collectors`.

**Step 2 — Verify RED**

**Step 3 — Minimal implementation**

Add to `Nat20PartyQuestStore.java`:

```java
private final Map<UUID, Set<String>> byPlayer = new HashMap<>();

public void add(QuestInstance quest) {
    String id = quest.getQuestId();
    if (id == null || id.isEmpty()) {
        throw new IllegalArgumentException("QuestInstance must have a questId before add()");
    }
    primary.put(id, quest);
    for (UUID player : quest.getAccepters()) {
        byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(id);
    }
}

public List<QuestInstance> queryByPlayer(UUID player) {
    Set<String> ids = byPlayer.get(player);
    if (ids == null || ids.isEmpty()) return List.of();
    List<QuestInstance> out = new ArrayList<>(ids.size());
    for (String id : ids) out.add(primary.get(id));
    return out;
}
```

Imports: `java.util.*`.

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "test(quest): PartyQuestStore secondary index for player lookup"
```

---

### Task 1.4 — remove removes from both maps

**Files:**
- Modify: store + test

**Step 1 — Failing test**

```java
@Test
void removeDeletesFromPrimaryAndIndex() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();
    QuestInstance q = new QuestInstance();
    q.setQuestId("gone");
    q.setAccepters(List.of(alice));
    store.add(q);

    store.remove("gone");

    assertNull(store.getById("gone"));
    assertTrue(store.queryByPlayer(alice).isEmpty());
}

@Test
void removeOfUnknownIdIsNoOp() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    assertDoesNotThrow(() -> store.remove("never-existed"));
}
```

**Step 2 — Verify RED**

**Step 3 — Minimal implementation**

```java
public void remove(String questId) {
    QuestInstance q = primary.remove(questId);
    if (q == null) return;
    for (UUID player : q.getAccepters()) {
        Set<String> ids = byPlayer.get(player);
        if (ids == null) continue;
        ids.remove(questId);
        if (ids.isEmpty()) byPlayer.remove(player);
    }
}
```

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "test(quest): PartyQuestStore remove cleans index"
```

---

### Task 1.5 — Mutations survive re-query (no transient-deserialization trap)

**Goal:** prove that mutating a QuestInstance returned from the store is visible to the next `queryByPlayer` / `getById`. This is the key contract vs. the legacy Gson-copy-on-every-read bug.

**Step 1 — Failing test**

```java
@Test
void mutationsOnStoredInstancePersistAcrossReads() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();
    QuestInstance q = new QuestInstance();
    q.setQuestId("mut");
    q.setAccepters(List.of(alice));
    q.setMaxConflicts(3);
    store.add(q);

    QuestInstance first = store.getById("mut");
    first.incrementConflictCount();

    QuestInstance second = store.getById("mut");
    assertEquals(1, second.getConflictCount(), "store must return the same live instance, not a copy");

    assertSame(first, second);
}
```

**Step 2 — Verify RED** (should pass already if you implemented task 1.2 as a live reference; if you naively cloned, it fails)

**Step 3 — Implementation**

No change if you kept the simple HashMap. If you added any cloning/Gson round-trip, remove it. The contract is: the store holds the authoritative live reference; callers must not mutate fields outside store methods, but there is no defensive copy.

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "test(quest): PartyQuestStore returns live references (no transient-deserialization trap)"
```

---

### Task 1.6 — Persistence round-trip (save/load)

**Files:**
- Modify: `Nat20PartyQuestStore.java` + test
- Test uses `@TempDir` (JUnit Jupiter) to isolate filesystem

**Step 1 — Failing test**

```java
@Test
void saveAndLoadRoundTripsAllQuests(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("party_quests.json");

    Nat20PartyQuestStore out = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();

    QuestInstance q1 = new QuestInstance();
    q1.setQuestId("q1");
    q1.setAccepters(List.of(alice, bob));
    q1.setMaxConflicts(2);
    out.add(q1);

    out.saveTo(file);

    Nat20PartyQuestStore in = new Nat20PartyQuestStore();
    in.loadFrom(file);

    QuestInstance loaded = in.getById("q1");
    assertNotNull(loaded);
    assertEquals(List.of(alice, bob), loaded.getAccepters());
    assertEquals(2, loaded.getMaxConflicts());

    // index rebuilt on load
    assertEquals(1, in.queryByPlayer(alice).size());
    assertEquals(1, in.queryByPlayer(bob).size());
}

@Test
void loadFromMissingFileStartsEmpty(@TempDir Path tmp) {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    assertDoesNotThrow(() -> store.loadFrom(tmp.resolve("nope.json")));
    assertNull(store.getById("anything"));
}
```

Add imports: `java.nio.file.Path`, `org.junit.jupiter.api.io.TempDir`.

**Step 2 — Verify RED**

**Step 3 — Minimal implementation**

```java
private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
private static final java.lang.reflect.Type PRIMARY_TYPE =
    new com.google.gson.reflect.TypeToken<Map<String, QuestInstance>>(){}.getType();

public void saveTo(Path file) throws IOException {
    Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
    Files.writeString(file, GSON.toJson(primary, PRIMARY_TYPE));
}

public void loadFrom(Path file) throws IOException {
    primary.clear();
    byPlayer.clear();
    if (!Files.exists(file)) return;
    String json = Files.readString(file);
    if (json.isEmpty()) return;
    Map<String, QuestInstance> loaded = GSON.fromJson(json, PRIMARY_TYPE);
    if (loaded == null) return;
    primary.putAll(loaded);
    for (QuestInstance q : loaded.values()) {
        for (UUID player : q.getAccepters()) {
            byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(q.getQuestId());
        }
    }
}
```

Imports: `com.google.gson.Gson`, `com.google.gson.GsonBuilder`, `java.io.IOException`, `java.nio.file.*`.

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "feat(quest): PartyQuestStore JSON persistence with index rebuild on load"
```

---

### Task 1.7 — turnIn moves completion to each accepter's completedQuests

**Files:**
- Modify: `Nat20PartyQuestStore.java` + test
- New test fixture: inline `FakePlayerDataProvider` inside the test file or a simple functional interface

**Step 1 — Failing test**

```java
@Test
void turnInRecordsCompletionForEveryAccepterAndRemovesQuest() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();

    QuestInstance q = new QuestInstance();
    q.setQuestId("done");
    q.setAccepters(List.of(alice, bob));
    store.add(q);

    List<UUID> recordedFor = new ArrayList<>();
    store.turnIn("done", (player, inst) -> recordedFor.add(player));

    assertEquals(List.of(alice, bob), recordedFor);
    assertNull(store.getById("done"));
    assertTrue(store.queryByPlayer(alice).isEmpty());
    assertTrue(store.queryByPlayer(bob).isEmpty());
}

@Test
void turnInUnknownIdDoesNothing() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    List<UUID> recordedFor = new ArrayList<>();
    store.turnIn("nope", (p, i) -> recordedFor.add(p));
    assertTrue(recordedFor.isEmpty());
}
```

**Step 2 — Verify RED**

**Step 3 — Minimal implementation**

Add nested functional interface:

```java
@FunctionalInterface
public interface CompletionSink {
    void record(UUID player, QuestInstance quest);
}

public void turnIn(String questId, CompletionSink sink) {
    QuestInstance q = primary.get(questId);
    if (q == null) return;
    for (UUID player : q.getAccepters()) sink.record(player, q);
    remove(questId);
}
```

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "feat(quest): PartyQuestStore.turnIn iterates accepters via sink"
```

---

### Task 1.8 — migrate player's legacy questFlags into store

**Files:**
- Modify: `Nat20PartyQuestStore.java` + test

**Step 1 — Failing test**

```java
@Test
void migratePlayerDataMovesLegacyQuestsIntoStoreWithSelfAsAccepter() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();

    QuestInstance legacy = new QuestInstance();
    legacy.setQuestId("legacy-1");
    // accepters empty (legacy save didn't have it)

    Map<String, QuestInstance> legacyActive = new HashMap<>();
    legacyActive.put("legacy-1", legacy);

    store.migratePlayer(alice, legacyActive);

    QuestInstance migrated = store.getById("legacy-1");
    assertNotNull(migrated);
    assertEquals(List.of(alice), migrated.getAccepters());
    assertEquals(1, store.queryByPlayer(alice).size());
}

@Test
void migratePlayerIsIdempotentWhenQuestAlreadyInStore() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    UUID alice = UUID.randomUUID();

    QuestInstance existing = new QuestInstance();
    existing.setQuestId("legacy-1");
    existing.setAccepters(List.of(alice));
    store.add(existing);

    QuestInstance legacy = new QuestInstance();
    legacy.setQuestId("legacy-1");

    store.migratePlayer(alice, Map.of("legacy-1", legacy));

    // did not overwrite or duplicate
    assertSame(existing, store.getById("legacy-1"));
    assertEquals(1, store.queryByPlayer(alice).size());
}
```

**Step 2 — Verify RED**

**Step 3 — Minimal implementation**

```java
public void migratePlayer(UUID player, Map<String, QuestInstance> legacyActive) {
    if (legacyActive == null || legacyActive.isEmpty()) return;
    for (QuestInstance legacy : legacyActive.values()) {
        String id = legacy.getQuestId();
        if (id == null || id.isEmpty()) continue;
        if (primary.containsKey(id)) continue; // idempotent: first writer wins
        legacy.setAccepters(List.of(player));
        primary.put(id, legacy);
        byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(id);
    }
}
```

**Step 4 — Verify GREEN**

**Step 5 — Commit**

```bash
git commit -am "feat(quest): PartyQuestStore.migratePlayer for legacy questFlags data"
```

---

### Task 1.9 — end-of-slice sanity: run whole suite

Run: `./gradlew test`

Expected: all existing tests still pass alongside the new `Nat20PartyQuestStoreTest` and `QuestInstanceAcceptersTest`.

If something unrelated fails: investigate; the store is new code, nothing else should touch it yet.

Commit only if anything was adjusted: otherwise, slice 1 is done.

---

## Slice 2 — Nat20Party domain (TDD, worktree-safe)

New package: `com.chonbosmods.party`. The Registry is a server-global singleton, similar to `Nat20PartyQuestStore`. For unit-test purposes the registry is POJO-constructable with injected clocks for the ghost-leader test.

### Task 2.1 — Nat20Party basic entity

**Files:**
- Create: `src/main/java/com/chonbosmods/party/Nat20Party.java`
- Test: `src/test/java/com/chonbosmods/party/Nat20PartyTest.java`

**Test contents:**

```java
@Test
void newPartyOfOneHasLeaderAsOnlyMember() {
    UUID alice = UUID.randomUUID();
    Nat20Party p = Nat20Party.ofSolo(alice);
    assertEquals(alice, p.getLeader());
    assertEquals(List.of(alice), p.getMembers());
    assertTrue(p.isSolo());
}

@Test
void membersAreJoinOrdered() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    Nat20Party p = Nat20Party.ofSolo(a);
    p.addMember(b);
    p.addMember(c);
    assertEquals(List.of(a, b, c), p.getMembers());
    assertFalse(p.isSolo());
}
```

**Implementation (minimum):**

```java
public class Nat20Party {
    private final String partyId;
    private final List<UUID> members = new ArrayList<>();
    private UUID leader;

    public Nat20Party(String partyId, UUID leader) {
        this.partyId = partyId;
        this.leader = leader;
        this.members.add(leader);
    }
    public static Nat20Party ofSolo(UUID player) {
        return new Nat20Party(UUID.randomUUID().toString(), player);
    }
    public String getPartyId() { return partyId; }
    public UUID getLeader() { return leader; }
    public List<UUID> getMembers() { return List.copyOf(members); }
    public boolean isSolo() { return members.size() == 1; }
    public void addMember(UUID player) {
        if (!members.contains(player)) members.add(player);
    }
}
```

Commit: `test(party): Nat20Party entity with ordered members`

---

### Task 2.2 — Remove member, leader succession on explicit leave

**Test contents:**

```java
@Test
void memberLeaveRemovesThemKeepsLeader() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    Nat20Party p = Nat20Party.ofSolo(a);
    p.addMember(b);
    p.removeMember(b);
    assertEquals(List.of(a), p.getMembers());
    assertEquals(a, p.getLeader());
}

@Test
void leaderLeavePromotesNextByJoinOrder() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    Nat20Party p = Nat20Party.ofSolo(a);
    p.addMember(b);
    p.addMember(c);
    p.removeMember(a);
    assertEquals(List.of(b, c), p.getMembers());
    assertEquals(b, p.getLeader());
}

@Test
void removingLastMemberLeavesPartyEmpty() {
    UUID a = UUID.randomUUID();
    Nat20Party p = Nat20Party.ofSolo(a);
    p.removeMember(a);
    assertTrue(p.getMembers().isEmpty());
    assertTrue(p.isEmpty());
}
```

**Implementation:**

```java
public void removeMember(UUID player) {
    members.remove(player);
    if (player.equals(leader) && !members.isEmpty()) {
        leader = members.get(0);
    }
}
public boolean isEmpty() { return members.isEmpty(); }
```

Commit: `test(party): member leave + automatic succession by join order`

---

### Task 2.3 — Nat20PartyRegistry: auto-create on lookup, every player always in a party

**Files:**
- Create: `src/main/java/com/chonbosmods/party/Nat20PartyRegistry.java`
- Test: `src/test/java/com/chonbosmods/party/Nat20PartyRegistryTest.java`

**Test contents:**

```java
@Test
void firstLookupForPlayerAutoCreatesSizeOneParty() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    Nat20Party p = reg.getParty(alice);
    assertTrue(p.isSolo());
    assertEquals(alice, p.getLeader());
}

@Test
void secondLookupReturnsSameParty() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    assertSame(reg.getParty(alice), reg.getParty(alice));
}

@Test
void differentPlayersGetDifferentParties() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    assertNotSame(reg.getParty(alice), reg.getParty(bob));
}
```

**Implementation minimum:**

```java
public class Nat20PartyRegistry {
    private final Map<UUID, Nat20Party> byPlayer = new HashMap<>();
    public Nat20Party getParty(UUID player) {
        return byPlayer.computeIfAbsent(player, Nat20Party::ofSolo);
    }
}
```

Commit: `test(party): registry auto-creates size-1 party on first lookup`

---

### Task 2.4 — Accept invite (joining a growing party)

**Test contents:**

```java
@Test
void acceptInviteJoinsInviterPartyAndDisposesOldSolo() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();
    Nat20Party alicesParty = reg.getParty(alice);
    Nat20Party bobsOldParty = reg.getParty(bob);

    reg.acceptInvite(bob, alicesParty.getPartyId());

    assertEquals(alicesParty, reg.getParty(bob));
    assertEquals(List.of(alice, bob), reg.getParty(alice).getMembers());
    assertTrue(bobsOldParty.isEmpty(), "disposed old solo party");
}

@Test
void acceptInviteForAlreadyPartiedPlayerRejects() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    UUID carol = UUID.randomUUID();

    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());
    // bob is now in alice's party (size 2). accepting another invite must fail.
    assertThrows(IllegalStateException.class,
        () -> reg.acceptInvite(bob, reg.getParty(carol).getPartyId()));
}
```

**Implementation:**

```java
private final Map<String, Nat20Party> byPartyId = new HashMap<>();

public Nat20Party getParty(UUID player) {
    return byPlayer.computeIfAbsent(player, p -> {
        Nat20Party np = Nat20Party.ofSolo(p);
        byPartyId.put(np.getPartyId(), np);
        return np;
    });
}

public void acceptInvite(UUID invitee, String targetPartyId) {
    Nat20Party target = byPartyId.get(targetPartyId);
    if (target == null) throw new IllegalArgumentException("no such party: " + targetPartyId);
    Nat20Party current = getParty(invitee);
    if (!current.isSolo()) throw new IllegalStateException("already in a multi-member party");
    current.removeMember(invitee); // disposes old solo
    byPartyId.remove(current.getPartyId());
    target.addMember(invitee);
    byPlayer.put(invitee, target);
}
```

Commit: `test(party): acceptInvite joins inviter and disposes old solo`

---

### Task 2.5 — Leave reverts to fresh size-1 party

**Test:**

```java
@Test
void leavePartyRevertsToFreshSoloParty() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());
    reg.leave(bob);

    Nat20Party bobsNew = reg.getParty(bob);
    assertTrue(bobsNew.isSolo());
    assertEquals(bob, bobsNew.getLeader());
    assertEquals(List.of(alice), reg.getParty(alice).getMembers());
}
```

**Implementation:**

```java
public void leave(UUID player) {
    Nat20Party current = byPlayer.get(player);
    if (current == null) return;
    current.removeMember(player);
    if (current.isEmpty()) byPartyId.remove(current.getPartyId());
    Nat20Party solo = Nat20Party.ofSolo(player);
    byPartyId.put(solo.getPartyId(), solo);
    byPlayer.put(player, solo);
}
```

Commit: `test(party): leave reverts to fresh size-1 party`

---

### Task 2.6 — Kick is leader-only

**Test:**

```java
@Test
void leaderCanKickNonLeaderMember() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

    reg.kick(alice, bob);

    assertEquals(List.of(alice), reg.getParty(alice).getMembers());
    assertTrue(reg.getParty(bob).isSolo());
}

@Test
void nonLeaderCannotKick() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    UUID carol = UUID.randomUUID();
    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());
    reg.acceptInvite(carol, reg.getParty(alice).getPartyId());

    assertThrows(SecurityException.class, () -> reg.kick(bob, carol));
}

@Test
void cannotKickSelf() {
    Nat20PartyRegistry reg = new Nat20PartyRegistry();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());
    assertThrows(IllegalArgumentException.class, () -> reg.kick(alice, alice));
}
```

**Implementation:**

```java
public void kick(UUID kicker, UUID target) {
    if (kicker.equals(target)) throw new IllegalArgumentException("cannot kick self");
    Nat20Party party = byPlayer.get(kicker);
    if (party == null || !kicker.equals(party.getLeader())) {
        throw new SecurityException("only leader can kick");
    }
    if (!party.getMembers().contains(target)) {
        throw new IllegalArgumentException("target not in party");
    }
    party.removeMember(target);
    Nat20Party targetSolo = Nat20Party.ofSolo(target);
    byPartyId.put(targetSolo.getPartyId(), targetSolo);
    byPlayer.put(target, targetSolo);
}
```

Commit: `test(party): kick is leader-only, not self`

---

### Task 2.7 — Ghost leader rule (auto-succession after N days offline)

Clock injection: `Nat20PartyRegistry` accepts a `Supplier<Instant>` in its constructor for tests. Default production constructor uses `Instant::now`.

**Test:**

```java
@Test
void ghostLeaderRuleTransfersLeadershipWhenLeaderOfflineLongerThanThreshold() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
    Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

    reg.markOffline(alice);

    // 8 days pass
    now.set(Instant.parse("2026-04-29T00:00:00Z"));

    reg.markOnline(bob);

    assertEquals(bob, reg.getParty(bob).getLeader(),
        "after 8 days offline leader, bob logging in promotes him");
}

@Test
void ghostLeaderRuleDoesNotFireBeforeThreshold() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
    Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

    reg.markOffline(alice);
    now.set(Instant.parse("2026-04-27T00:00:00Z")); // 6 days
    reg.markOnline(bob);

    assertEquals(alice, reg.getParty(bob).getLeader(), "still under threshold, leader stays alice");
}
```

**Implementation:**

```java
private final Supplier<Instant> clock;
private final Duration ghostThreshold;
private final Map<UUID, Instant> lastOnline = new HashMap<>();
private final Set<UUID> online = new HashSet<>();

public Nat20PartyRegistry() { this(Instant::now, Duration.ofDays(7)); }

public Nat20PartyRegistry(Supplier<Instant> clock, Duration ghostThreshold) {
    this.clock = clock;
    this.ghostThreshold = ghostThreshold;
}

public void markOnline(UUID player) {
    online.add(player);
    lastOnline.put(player, clock.get());
    Nat20Party party = byPlayer.get(player);
    if (party == null || party.isSolo()) return;
    UUID leader = party.getLeader();
    if (player.equals(leader)) return;
    Instant leaderSeen = lastOnline.getOrDefault(leader, Instant.EPOCH);
    if (Duration.between(leaderSeen, clock.get()).compareTo(ghostThreshold) > 0
        && !online.contains(leader)) {
        party.promoteToLeader(player);
    }
}

public void markOffline(UUID player) {
    online.remove(player);
    lastOnline.put(player, clock.get());
}
```

Add `Nat20Party.promoteToLeader(UUID)`:

```java
public void promoteToLeader(UUID player) {
    if (!members.contains(player)) throw new IllegalArgumentException("not a member");
    leader = player;
}
```

Commit: `test(party): ghost-leader rule auto-promotes next online after N days`

---

### Task 2.8 — Party persistence (save/load)

Same pattern as Task 1.6. Aggregate JSON, rebuilt registry on load.

**Test:** save registry with 2 parties → load → same party composition and leaders.

Commit: `feat(party): Nat20PartyRegistry JSON persistence`

---

### Task 2.9 — Slice 2 sanity

Run: `./gradlew test`. All green. Commit any fixups.

---

## Slice 3 — Wire QuestStateManager to the store

This is the compat layer. Existing call sites for `QuestStateManager.getActiveQuests(data)` need to keep working, but now they proxy into the store.

### Task 3.1 — QuestStateManager takes a store reference

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestStateManager.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (construction site)
- Test: `src/test/java/com/chonbosmods/quest/QuestStateManagerAdapterTest.java`

**Test intent:**

```java
@Test
void addQuestWritesToStoreAndNotToQuestFlags() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    QuestStateManager mgr = new QuestStateManager(store);
    Nat20PlayerData data = new Nat20PlayerData(/* ...uuid... */);
    QuestInstance q = new QuestInstance();
    q.setQuestId("adapt-1");

    mgr.addQuest(data, q);

    assertNotNull(store.getById("adapt-1"));
    assertNull(data.getQuestData("active_quests"), "legacy questFlags no longer written");
    assertEquals(List.of(data.getPlayerUuid()), q.getAccepters());
}

@Test
void getActiveQuestsReturnsStoreContentsFilteredByPlayer() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    QuestStateManager mgr = new QuestStateManager(store);
    Nat20PlayerData alice = new Nat20PlayerData(/* aliceUuid */);
    Nat20PlayerData bob = new Nat20PlayerData(/* bobUuid */);

    QuestInstance q = new QuestInstance();
    q.setQuestId("shared");
    q.setAccepters(List.of(alice.getPlayerUuid(), bob.getPlayerUuid()));
    store.add(q);

    assertEquals(Set.of("shared"), mgr.getActiveQuests(alice).keySet());
    assertEquals(Set.of("shared"), mgr.getActiveQuests(bob).keySet());
}
```

(If `Nat20PlayerData` doesn't expose `getPlayerUuid`, add it as part of this task and write a small test for it.)

**Implementation sketch:**

```java
public class QuestStateManager {
    private final Nat20PartyQuestStore store;
    public QuestStateManager(Nat20PartyQuestStore store) { this.store = store; }

    public Map<String, QuestInstance> getActiveQuests(Nat20PlayerData data) {
        Map<String, QuestInstance> out = new HashMap<>();
        for (QuestInstance q : store.queryByPlayer(data.getPlayerUuid())) {
            out.put(q.getQuestId(), q);
        }
        return out;
    }

    public void addQuest(Nat20PlayerData data, QuestInstance quest) {
        if (quest.getAccepters().isEmpty()) {
            quest.setAccepters(List.of(data.getPlayerUuid()));
        }
        store.add(quest);
    }

    public void removeQuest(Nat20PlayerData data, String questId) {
        store.remove(questId);
    }

    public QuestInstance getQuest(Nat20PlayerData data, String questId) {
        QuestInstance q = store.getById(questId);
        return (q != null && q.hasAccepter(data.getPlayerUuid())) ? q : null;
    }
    // markQuestCompleted, getCompletedQuestIds unchanged except turn-in uses store.turnIn
}
```

Commit: `refactor(quest): QuestStateManager reads/writes via PartyQuestStore`

---

### Task 3.2 — DialogueActionRegistry GIVE_QUEST snapshots party at accept time

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` around line 223 (`GIVE_QUEST` handler, per existing investigation).

**Test intent:** the GIVE_QUEST handler must build the accepters list from the accepting player's current party. Add a small adapter test using a fake party registry.

Mock-free alternative: extract the "build accepters from party" into a helper method on `QuestStateManager` (e.g. `acceptForParty(Nat20Party party, QuestInstance q)`) and unit-test that helper directly.

**Minimal helper test:**

```java
@Test
void acceptForPartySnapshotsCurrentMembersAsAccepters() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    QuestStateManager mgr = new QuestStateManager(store);

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    Nat20Party party = Nat20Party.ofSolo(alice);
    party.addMember(bob);

    QuestInstance q = new QuestInstance();
    q.setQuestId("party-quest");

    mgr.acceptForParty(party, q);

    QuestInstance stored = store.getById("party-quest");
    assertEquals(List.of(alice, bob), stored.getAccepters());
}

@Test
void acceptForPartyFreezesAcceptersIgnoringLaterPartyChurn() {
    Nat20PartyQuestStore store = new Nat20PartyQuestStore();
    QuestStateManager mgr = new QuestStateManager(store);

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    Nat20Party party = Nat20Party.ofSolo(alice);
    party.addMember(bob);

    QuestInstance q = new QuestInstance();
    q.setQuestId("frozen");
    mgr.acceptForParty(party, q);

    party.removeMember(bob); // bob leaves after accept

    assertEquals(List.of(alice, bob), store.getById("frozen").getAccepters(),
        "accepters must remain frozen to original snapshot");
}
```

**Helper:**

```java
public void acceptForParty(Nat20Party party, QuestInstance quest) {
    quest.setAccepters(List.copyOf(party.getMembers()));
    store.add(quest);
}
```

Then update the GIVE_QUEST handler call site to invoke `acceptForParty(partyRegistry.getParty(player), preGeneratedQuest)` in place of the current per-player `addQuest`.

Commit: `refactor(quest): accept snapshots current party members as frozen accepters`

---

### Task 3.3 — Migration hook on PlayerConnect

**Files:**
- Modify: whatever listener handles PlayerConnect / PlayerReadyEvent (per the nat20 memory on event hooks) to call `store.migratePlayer(playerUuid, legacyActiveQuests)` once, then clear the legacy key.

Unit-test the legacy-data-reader helper in isolation:

```java
@Test
void legacyLoaderReturnsEmptyWhenKeyMissing() {
    Nat20PlayerData data = new Nat20PlayerData(UUID.randomUUID());
    assertTrue(QuestStateManager.readLegacyActiveQuests(data).isEmpty());
}

@Test
void legacyLoaderDeserializesQuestFlagsJson() {
    Nat20PlayerData data = new Nat20PlayerData(UUID.randomUUID());
    data.setQuestData("active_quests", "{\"q\":{\"questId\":\"q\"}}");
    Map<String, QuestInstance> loaded = QuestStateManager.readLegacyActiveQuests(data);
    assertEquals(Set.of("q"), loaded.keySet());
}
```

Commit: `feat(quest): one-shot migration from legacy questFlags into PartyQuestStore`

---

### Task 3.4 — Smoke stub: wiring compiles

Run: `./gradlew compileJava`. Any remaining compile errors from call sites are fixed here; only mechanical updates, no behavior changes. Commit with `refactor(quest): adjust call sites for PartyQuestStore adapter`.

---

## Slice 4 — mlvl scaling

### Task 4.1 — PartyMlvlCurve (pure function)

**Files:**
- Create: `src/main/java/com/chonbosmods/party/PartyMlvlCurve.java`
- Test: `src/test/java/com/chonbosmods/party/PartyMlvlCurveTest.java`

**Test intent:**

```java
@Test
void soloAddsNothing() {
    assertEquals(5, PartyMlvlCurve.apply(5, 1));
}

@Test
void partyOfTwoAddsOne() {
    assertEquals(6, PartyMlvlCurve.apply(5, 2));
}

@Test
void linearForSmallParties() {
    // current starting-point shape: f(n) = n; document and lock the linear shape
    for (int n = 1; n <= 5; n++) assertEquals(5 + (n - 1), PartyMlvlCurve.apply(5, n));
}

@Test
void nearbyCountZeroIsTreatedAsSolo() {
    assertEquals(5, PartyMlvlCurve.apply(5, 0));
}
```

**Implementation:**

```java
public final class PartyMlvlCurve {
    private PartyMlvlCurve() {}
    public static int apply(int baseMlvl, int nearbyPartyMembers) {
        int n = Math.max(1, nearbyPartyMembers);
        return baseMlvl + (n - 1);
    }
}
```

Commit: `test(party): PartyMlvlCurve linear starting-point formula`

---

### Task 4.2 — Nearby-count snapshot helper

**Files:**
- Create: `src/main/java/com/chonbosmods/party/NearbyPartyCount.java`
- Test: pure-math test using injected distance fn

**Test:**

```java
@Test
void countsOnlineMembersWithinRadius() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    Set<UUID> online = Set.of(a, b, c);
    Map<UUID, Double> distanceFromTrigger = Map.of(a, 0.0, b, 40.0, c, 200.0);

    int n = NearbyPartyCount.count(List.of(a, b, c), online, distanceFromTrigger::get, 50.0);
    assertEquals(2, n, "a at 0m and b at 40m within radius 50m; c excluded");
}

@Test
void offlineMembersExcluded() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    Set<UUID> online = Set.of(a); // b offline
    int n = NearbyPartyCount.count(List.of(a, b), online, p -> 0.0, 50.0);
    assertEquals(1, n);
}
```

**Implementation:**

```java
public final class NearbyPartyCount {
    private NearbyPartyCount() {}
    public static int count(List<UUID> members, Set<UUID> online,
                            java.util.function.Function<UUID, Double> distance,
                            double radius) {
        int n = 0;
        for (UUID m : members) {
            if (!online.contains(m)) continue;
            Double d = distance.apply(m);
            if (d == null) continue;
            if (d <= radius) n++;
        }
        return n;
    }
}
```

Commit: `test(party): NearbyPartyCount helper for mlvl scaling proximity`

---

### Task 4.3 — Wire into Nat20MobGroupSpawner (smoke-testable only)

**Files:**
- Modify: the spawn-time mlvl calculation sites in `Nat20MobGroupSpawner` (POI trigger, ambient roll, quest group). Each site already computes `baseMlvl`; wrap that value with `PartyMlvlCurve.apply(baseMlvl, nearby)` where `nearby = NearbyPartyCount.count(party.getMembers(), onlineSet, distanceFn, RADIUS)`.

No new unit tests here: the spawner is a live-world integration surface. Smoke test checklist:

- Solo in zone: mobs spawn at base mlvl.
- Two partymates within rally radius trigger a zone spawn: mobs spawn at mlvl+1.
- Partymate 300 blocks away: no effect.

Commit: `feat(mobs): party-nearby scaling via PartyMlvlCurve at spawn time`

---

## Slice 5 — /sheet Party tab + banners (integration; merge first, smoke on main)

**Pre-req:** slices 1–4 merged to main and smoke tests 1–6 passed.

### Task 5.1 — Party tab UI template + wiring

Mirror existing Character Sheet / Quest Log tabs. Party tab is a peer tab under the existing `/sheet` hotkey flow. Populate from `Nat20PartyRegistry.getParty(viewer)`.

### Task 5.2 — Invite / Accept / Decline / Leave buttons

Server-side `Nat20PartyRegistry` calls already exist from slice 2. UI sends packets: `party/invite`, `party/accept`, `party/decline`, `party/leave`.

### Task 5.3 — Kick button (leader only)

Hidden for non-leaders. Server authoritative check still applies.

### Task 5.4 — Pending invites list

Received: only self sees. Sent: all party members see.

### Task 5.5 — Party Invite banner

Uses existing banner system. Text: `{inviterName} invited you to a party.` Action opens /sheet Party tab (or inline accept/decline if banner supports it).

### Task 5.6 — Quest Phase Transition banner

Fires to all online accepters on phase advance. Offline accepters: no retroactive banner.

### Task 5.7 — Quest Completion banner (two variants)

- Present-at-turn-in variant: "You and your party completed {quest}."
- Absent-at-turn-in variant: "Your party completed {quest} while you were away. Rewards delivered."

Commit after each task: smoke each flow in devserver before moving on.

---

## Slice 6 — In-quest incidental proximity gate (integration; main branch)

**Pre-req:** slice 5 merged; existing combat XP/loot distribution paths identified.

### Task 6.1 — Unit-test the proximity filter used by quest incidentals

Same shape as `NearbyPartyCount` but scoped to accepters, not party members. Extract a helper `NearbyAccepters.filter(List<UUID>, Function<UUID,Double>, radius)` returning the subset that gets the incidental.

### Task 6.2 — Hook KILL_MOBS / KILL_BOSS credit sharing

When any accepter kills a tracked mob, all accepters get objective-counter credit (already one counter per QuestInstance, so this is automatic). Incidental XP uses the proximity filter.

### Task 6.3 — Hook FETCH_ITEM / COLLECT_RESOURCES / TALK_TO_NPC incidentals

Same proximity-filter hook. TALK_TO_NPC incidentals may be zero-reward (just progress); confirm during smoke.

---

## Verification Checklist

Before declaring work complete:

- [ ] Every new function/method has a failing test written first
- [ ] Each test failed for the expected reason before implementation
- [ ] All tests pass: `./gradlew test`
- [ ] `./gradlew compileJava` clean
- [ ] Slices 1–4 landed behind unit tests in a worktree
- [ ] Slices 5–6 smoke-tested in devserver on main
- [ ] Migration verified: spin up a world with legacy `questFlags` data, confirm quests surface after migration
- [ ] Ghost-leader rule verified: manual clock test in a playtest (or accept the unit test coverage)
- [ ] Known shortcomings recorded in `docs/known-issues.md` (no-cancellation, ghost-leader exploitability, quest-bundle free-loading)

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-04-21-party-multiplayer-quest-impl.md`. Two execution options:

**1. Subagent-Driven (this session)** — dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — open a new session with superpowers:executing-plans inside the worktree for batched execution with checkpoints.

Which approach?
