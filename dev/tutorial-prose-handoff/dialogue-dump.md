# Tutorial + Jiub Dialogue Dump

Every hardcoded or JSON-authored string the player can see across the Jiub
onboarding flow and the tutorial quest (`tutorial_main`), laid out in the
order they fire in play so an outside brainstormer can read the arc
end-to-end.

Token reference at the bottom (`{target_npc}`, `{Background}`, etc.) — the
runtime substitutes them via `DialogueResolver`.

---

## 0. The arc in order (who speaks when)

1. **Jiub (intro page, forced)** — `INTRO1` → `INTRO2` → picker → `INTRO3`.
2. **Jiub (standard dialogue session, exitable)** — one-topic quizzing on
   stats / affixes / mobs / quests / parties (Jiub.json).
3. **Celius (phase 1 turn-in)** — walk to his settlement, talk, get the
   phase 2 hand-off.
4. **Random settlement NPC (phase 2 target)** — "opener" line on first
   interaction, "closer" line on [Continue].
5. **Celius (phase 2 turn-in)** — report back, get the phase 3 hand-off.
6. **Celius (phase 3 turn-in)** — closing line after the boss is dead.
7. **System chat line** — fires after phase 3 turn-in.

"Keeping watch" is an always-available idle topic on Celius that any
Celius visit can open; it's unrelated to the quest state.

---

## 1. Jiub — forced intro page (`JiubIntroPage.java`)

The intro page is a custom UI page with one line at a time and a single
Continue button. Player cannot close it; Continue is the only exit.

### INTRO1
> Stand up. There you go. You were dreaming. Not even last night's storm could wake you.

### INTRO2
> Somewhere behind you is the life you came from. I'd like to hear what it looked like.

*(Continue here opens the background picker.)*

### INTRO3 (template, rendered after Confirm on the picker)
> Go talk to Celius Gravus. He has been looking for help with something urgent and could use {0} of your caliber.

`{0}` is substituted with `a` / `an` + `{Background}` (e.g. "a Soldier",
"an Entertainer"). After INTRO3 Continue, the player enters the standard
Jiub dialogue session below.

---

## 2. Jiub — standard dialogue session (`Jiub.json`)

Jiub's greeting and five hand-authored topics. Each topic is a Continue
chain of 2-4 dialogue nodes. The greeting fires first; topics appear as a
list and can be revisited. `.../` wraps command names in the
entity-highlight color; inline Unicode escapes are intentional.

### Greeting node (`jiub_greeting`)
> Ask me anything you'd like to know.

### Topic: "Stats & Leveling"

**Node 1**
> You're a character with six ability scores now. Strength, Dexterity, Constitution, Intelligence, Wisdom, and Charisma shape everything from the weight of your blows to how NPCs respond when you open your mouth. As you explore, fight, and complete quests, you earn experience, gain levels, and collect ability points to place wherever you choose. Type /sheet at any time to open your character sheet and see your scores laid out.

**Node 2**
> Every score does two jobs. When a moment calls for a roll of the d20, for a dialogue challenge or a feat of perception or a test of will, your score modifies that roll in the old D&D tradition. The rest of the time, quietly and always, those same scores scale your capability in combat. Dexterity raises your baseline crit chance, Strength swells the damage your crits deal, and Chonstitution hardens your resolve and increases HP.

**Node 3** (terminal)
> The world climbs with you. As your level rises, the loot you find scales to match, and the affixes on your gear grow more potent in your hands. Your character sheet keeps the picture whole. It shows your scores, your level, your unspent points, and every quest you've taken on.

### Topic: "Gear Affixes"

**Node 1**
> Nearly everything in Natural 20 is shaped by affixes, the small named bonuses that turn a plain longsword into something you'll remember finding. A weapon may burn its targets, a helmet may turn thorns on anyone who strikes you, a pair of gloves may grant a sliver of life from every wound you deal. Most gear you find carries at least one, and the best gear carries several.

**Node 2**
> When you loot a piece of equipment, the affix lines appear in its tooltip, each one colored and named. They're drawn from a single deep pool that spans damage of every element, critical hits, resistances, absorption, thorns, leech, evasion, and more besides. Some pair with your ability scores. A crit-damage affix in the hands of a Strength-built fighter hits harder than the same affix on someone who didn't invest there.

**Node 3** (terminal)
> The creatures you fight draw from that same pool. A champion marked with Life Leech will drink back health from every hit it lands on you, and a boss wreathed in Thorns will punish every blow you throw. Reading an elite mob, then, is a lot like reading a tooltip. The affixes you know from your own gear are the very same ones threatening you from across the battlefield, and knowing what to expect is half of surviving.

### Topic: "Mob Groups & Enemies"

**Node 1**
> Enemies in Natural 20 do not wander the world alone. They travel in groups, a single boss with a band of champion minions at its flank, and every group carries a difficulty tier that marks it at a glance.

**Node 2**
> Every tiered mob wears a colored tint you can read from across the field. Uncommon groups carry a green wash, Rare a blue one, Epic a regal purple, and Legendary a burning gold. The brighter the tint, the more affixes the group carries, the harder its members hit, and the richer the loot they'll leave behind. Bosses earn their own names too, so the Overlord who leads today's warband will not be the Overlord who leads the next.

**Node 3** (terminal)
> Groups belong to their environment. A forest encounter is not a desert encounter. A tundra pack favors beasts of the cold, where a volcanic one favors things that crawled from the heat. A rare outlier spawn breaks the rule now and then, a reward for the wanderer willing to be surprised. Groups seed the world in two ways, ambient encounters that appear as you roam the surface, and quest and POI encounters that anchor to a specific objective and wait for you to arrive.

### Topic: "Quests"

**Node 1**
> Quests in Natural 20 come from the Hytale settlers you meet in towns across the land. A conversation reveals their trouble, their rumor, their need. If you choose to help, the quest joins your character sheet and travels with you from that moment on. Type /sheet to open your Quest Log, where every active thread and every quest you've finished is kept in view.

**Node 2**
> A quest may not be just a single errand. They can unfold in phases, an exposition and then one or more conflicts, each with its own objective and its own reward. You might be asked to speak with someone, gather resources from a particular region, fetch a lost item, clear a group of enemies, or slay a named boss. Every phase you complete pays out on its own, with experience, loot, and whatever rare item the quest giver was willing to part with.

**Node 3**
> Some quests will not mark your objective with a single point on the map. Instead, a circle will be drawn around the area, and the thing you are looking for lies somewhere within it. More often than not, the trail leads underground, so bring a torch and the patience to dig. The circle tells you where to begin. The finding is yours to do.

**Node 4** (terminal)
> Kill credit is generous. If you've struck a foe in the moments before its death, the kill counts as yours, whether the final blow was your swing, an ally's arrow, a well-timed fall, or the environment itself. Gathering quests respect where you travel too. The farther from safety you work, the larger the haul you'll bring home for the same task. Your Quest Log keeps every active thread in view, lets you toggle a waypoint on each one, and preserves a record of every quest you've seen through.

### Topic: "Parties"

**Node 1**
> Every player in Natural 20 belongs to a party. If you're adventuring alone, you're a party of one. Invite a friend and you become a party of two, with all the shared fortune that comes with it. Type /sheet to send an invite or to accept one waiting for you.

**Node 2**
> Parties claim quests together. When any member accepts a quest, the rest of the party accepts alongside them, and objective progress is shared between nearby party members. Turn in a phase and your partymates collect their rewards whether they fought at your side or were away from the keyboard entirely. The experience, the loot, and the items arrive for each of them in full.

**Node 3** (terminal)
> The world answers your numbers. Enemies spawning near a larger party rise to meet the group facing them, so there is no trivializing a fight by simply bringing more swords. Leadership passes automatically if a leader goes quiet too long, so a stale invite will never strand an active group. And the quests you accept together stay yours. If your paths diverge, the adventure you began remains on every sheet that agreed to it.

---

## 3. Celius Gravus — dialogue (`CeliusGravus.json`)

Celius is the tutorial NPC in the spawn settlement. He has one standing
greeting, one always-available idle topic, and three quest turn-in topics
that surface only when `tutorial_main` is in the matching phase state.
Every quest turn-in topic shares the same display label: **"A Matter of
Urgency"**.

### Greeting (`celius_greeting`, always)
> Stay sharp out there. The road past the gate has a way of sorting the prepared from the hopeful.

### Idle topic: "Keeping watch" (always available)

**Node `node_watch_1`** (terminal)
> The gate keeps the worst of it out. The rest of us do the rest.

### Quest topic (phase 1 turn-in — gated on `tutorial_main` phase 0 READY_FOR_TURN_IN)

Topic label: **A Matter of Urgency**

**Entry node** (`node_tutorial_phase1_entry`, onEnter fires
`TUTORIAL_TURN_IN_PHASE_1`)
> Good, Jiub told me to expect you. Glad you came when you did.

*(Continue button)*

**Assign node** (`node_tutorial_phase1_assign`, exhausts topic)
> Ride out to {target_npc_settlement} and find {target_npc}, they'll have something for you to bring back to me.

### Quest topic (phase 2 turn-in — gated on `tutorial_main` phase 1 READY_FOR_TURN_IN)

Topic label: **A Matter of Urgency**

**Entry node** (`node_tutorial_phase2_entry`, onEnter fires
`TUTORIAL_TURN_IN_PHASE_2`)
> Back already. Good. Whatever {target_npc} told you, hold onto it.

*(Continue button)*

**Assign node** (`node_tutorial_phase2_assign`, exhausts topic)
> There's one more thing I'll need your hands for. Check your quest log, I've marked the spot.

### Quest topic (phase 3 turn-in — gated on `tutorial_main` phase 2 READY_FOR_TURN_IN)

Topic label: **A Matter of Urgency**

**Entry node** (`node_tutorial_phase3_entry`, onEnter fires
`TUTORIAL_TURN_IN_PHASE_3`)
> You found {boss_name} and put them down. Heard the work from here.

*(Continue button)*

**Close node** (`node_tutorial_phase3_close`, exhausts topic)
> That's the sort of work this town remembers. Go on, your road's your own from here.

---

## 4. Phase 2 target NPC — tutorial-flavored lines

The phase 2 talk-to-NPC target is picked randomly at quest creation (a
settlement NPC, not Celius). The player sees the target NPC render two
lines during the interaction, authored as bindings by
`TutorialQuestFactory` (and re-applied if needed in
`TUTORIAL_TURN_IN_PHASE_1`):

### Opener (`target_npc_opener`)
> You're the one Celius sent. Good. Take back what I'm about to tell you, and tell him I said to move quickly.

### Closer (`target_npc_closer`, after Continue)
> Go on, then. Don't keep Celius waiting.

---

## 5. Quest log / waypoint strings

Not voice-acted but player-facing text.

### Initial objective summary (quest creation, phase 1 state)
> Return to Celius Gravus

### Phase 2 objective summary (written by `TUTORIAL_TURN_IN_PHASE_1` after resolving a target)
> Speak with {target_npc} in {target_npc_settlement}

### Phase 2 objective summary fallback (degenerate world — no other settlement yet)
> Wait for word from another settlement.

### Phase 3 objective summary (built from `ObjectiveType.KILL_BOSS`)
> Kill {boss_name}

### Quest header / quest log title (all phases)
> A Matter of Urgency

---

## 6. Phase 3 completion system message

Fires in chat as a single line after `TUTORIAL_TURN_IN_PHASE_3` marks the
quest completed.

### System line
> Tutorial complete. {boss_name} falls, and Celius nods you on.

---

## 7. Binding reference

Every `{token}` above is substituted at dialogue-render time from the
quest's variable bindings map. For brainstorming purposes, treat these
as authoritative values:

| Token | Value |
|---|---|
| `{Background}` | the Background enum's `displayName()` — "Soldier", "Folk Hero", "Charlatan", etc. (15 total). |
| `{target_npc}` | generated name of the random phase-2 target NPC, e.g. "Pharendiel Garevyn". Wrapped in the entity-highlight color when rendered in Celius's dialogue. |
| `{target_npc_settlement}` | display name of the phase-2 target's settlement, e.g. "Hollowglen". Also highlighted. |
| `{boss_name}` | name of the phase-3 boss, generated by `Nat20MobNameGenerator` at phase-2 turn-in, e.g. "Baneblister the Broken". Also highlighted. |
| `{target_npc_role}` | the target NPC's role as a common noun ("blacksmith", "guard", etc.) — not currently used in any tutorial text, but available. |

---

## 8. Authoring rules in force

These constraints apply to every line above. Any rewrites must keep
them intact.

- **R7: max 2 sentences per line.** Hard cap.
- **R8: first person or direct address only.** No narrator / reporter voice.
- **R13: no colons, no em / en / hyphen-as-dash.** Rewrite to flow with
  commas and periods. Hyphens inside compound words ("empty-handed") are
  fine.
- **R14: no corrective reframing.** Don't write "It's not X, it's Y" or
  "I wasn't X, I was Y" — state the intended thing directly.
- **Don't invent locations, events, or named characters outside the
  registry.** The only named entities the tutorial can reference are
  Jiub, Celius Gravus, and the bindings above. POI types / settlement
  types are common nouns, not specific landmarks.

Full rules live at `authoring/authoring_rules.md`.

---

## 9. What's known to be weak / open

- **Jiub's five explainer topics** read more like manuals than like a
  character. They were written for info density first. Voice is a fair
  target for revision.
- **Celius's lines are functional but thin.** The "Glad you came when
  you did" / "Heard the work from here" beats acknowledge the player
  without giving Celius much character.
- **The system message at phase 3 turn-in** is the weakest beat in the
  arc. It's utility-grade filler.
- **The idle "Keeping watch" topic** on Celius is a placeholder; two
  lines of essentially nothing. If he's going to be a named, recurring
  NPC the player walks past repeatedly, this slot deserves actual
  character.
- **Jiub has no idle / post-tutorial topics.** Once the player has
  heard the five explainer topics, he has nothing new to say on
  subsequent visits. Every topic is `exhaustsTopic: false` but the
  content doesn't change.
