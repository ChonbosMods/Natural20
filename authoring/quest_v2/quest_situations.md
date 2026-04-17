# Quest Situations

22 dramatic situations adapted from Polti's 36 for the Natural 20 quest v2 system. Each situation defines an **emotional frame** — not a mechanical chain. Authors choose objectives freely from the available types listed; the situation tells them what the quest *feels like*, not what buttons the player presses.

**This is the MVP set.** Situations excluded from MVP (Abduction, Adultery, Murderous Adultery, Crimes of Love, All Sacrificed for Passion, An Enemy Loved, Slaying of a Kinsman Unrecognized, Revolt, Falling Prey to Cruelty/Misfortune, Conflict with a God, Discovery of Dishonor of a Loved One) are deferred until new objective types expand the mechanical vocabulary.

---

## Objective Types (current)

| Type | What the player does |
|---|---|
| KILL_MOBS | Kill N of an enemy type |
| **KILL_BOSS** | **Kill one named boss (single target; `{boss_name}` and `{group_difficulty}` bound pre-POI; champions spawn alongside but do not count toward completion)** |
| COLLECT_RESOURCES | Gather N of a resource |
| FETCH_ITEM | Retrieve a specific item |
| TALK_TO_NPC | Speak to a named NPC at another settlement |

Quest chains are 2–3 phases (exposition + 1–2 conflicts + resolution). Each phase draws one objective. Authors pick any combination from the situation's available types — three KILL_MOBS phases is valid if the situation supports it.

---

## Situations

### 1. Supplication

**Description:** Someone begs for help they can't handle alone. The most basic quest shape: a person in need, a problem beyond their means, and you.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** desperate → grateful

**Authoring notes:** The NPC should feel genuinely unable to solve this themselves — not lazy, not delegating, but outmatched. Exposition should establish *why* they can't do it. Resolution should feel like weight lifted.

---

### 2. Deliverance

**Description:** Rescue the helpless from a threat. Someone is in danger and can't extract themselves. The quest is about removing the danger or reaching the person.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** urgent → relieved

**Authoring notes:** Closely related to Supplication, but the emotional center is on the *endangered party*, not the quest giver. The quest giver may be a worried friend, relative, or neighbor — not the person in danger. Use `{settlement_npc}` or `{target_npc}` to ground the endangered party.

---

### 3. Recovery

**Description:** Something was taken or lost, get it back. Could be stolen goods, a misplaced heirloom, or resources that went missing. The quest is about restoration.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** wronged/frustrated → vindicated

**Authoring notes:** The lost thing should matter to the NPC personally or practically — not just "fetch me a thing." Exposition should establish what was lost and why it matters. Recovery quests can range from petty ("someone took my tools") to serious ("our winter stores are gone").

---

### 4. Daring Enterprise

**Description:** A bold expedition into danger for a specific goal. The NPC wants something that requires courage to obtain. There's a clear target and a clear risk.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** ambitious/nervous → triumphant

**Authoring notes:** The NPC should acknowledge the risk. This isn't a casual errand — it's something they've been thinking about and wouldn't ask lightly. The goal should feel worth the danger. Good fit for multi-phase chains where early phases establish the danger and later phases claim the prize.

---

### 5. Pursuit

**Description:** Hunting down a threat. Something has been causing problems and it's time to deal with it directly. Proactive, not reactive.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** determined/angry → satisfied

**Authoring notes:** Unlike Supplication (reactive, desperate), Pursuit is proactive and driven. The NPC has had enough. Exposition should establish a pattern of problems — not a single incident, but a last straw. The NPC wants the source dealt with, not just the symptoms.

---

### 6. Disaster

**Description:** The community has been hit by something and needs to stabilize. Crops failed, creatures overran an area, supplies were destroyed. The quest is about recovery and triage.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** shaken → stabilized

**Authoring notes:** The disaster has already happened — the quest isn't about preventing it. The NPC is dealing with aftermath. Tone should be weary and practical, not panicked. "We need X to get through this" rather than "the sky is falling." The quest invented the disaster, but it should feel like a bad season, not an apocalypse.

---

### 7. Obtaining

**Description:** The NPC needs something acquired through effort. Practical, straightforward, often work-related. They need materials, a specific item, or information to complete a project or solve a problem.

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** practical/hopeful → satisfied

**Authoring notes:** The least dramatic situation. The NPC has a clear need and a clear ask. Keep it grounded — this is a favor between neighbors, not an epic quest. Works well for shorter chains. Exposition should make the need feel reasonable, not contrived.

---

### 8. Enigma

**Description:** Something doesn't add up and the NPC wants answers. Not a grand mystery — a local question that's been nagging them. Who, what, or why.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** puzzled/uneasy → clarity

**Authoring notes:** The "mystery" must resolve within the quest's own phases. No dangling threads. The answer should be mundane or bittersweet, not a conspiracy. TALK_TO_NPC phases work naturally as "ask around" steps. The resolution should deliver a clear answer, even if it's not a happy one.

---

### 9. Vengeance

**Description:** Someone wronged the NPC or their community, and they want payback. Not justice — this is personal. The NPC may not be entirely in the right.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM

**Tone arc:** bitter/cold → settled (not necessarily happy)

**Authoring notes:** Vengeance quests should feel morally gray. The NPC's grievance is real, but their response might be disproportionate or misdirected. Resolution tone is "settled," not "triumphant" — the NPC got what they wanted but may not feel better. Authors can lean into this ambiguity.

---

### 10. Conflict with Fate

**Description:** Unknown or incomprehensible forces are plaguing the land. Things are going wrong and nobody knows why. The quest is about pushing back against something that feels bigger than anyone.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, COLLECT_RESOURCES

**Tone arc:** bewildered/fatalistic → cautiously hopeful

**Authoring notes:** Frame the threat as mysterious but the response as practical. The NPC doesn't understand the cause but knows what needs doing: kill the creatures that appeared, gather materials for protection, find the object that might help. Avoid cosmic lore — keep it grounded in "bad things are happening and we need to deal with them."

---

### 11. Rivalry of Kinsmen

**Description:** Tension between people who should be allies — neighbors, partners, old friends. The quest is about navigating a social conflict within or between settlements.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** exasperated/hurt → resigned or reconciled

**Authoring notes:** The NPC is caught in the middle or is one of the parties. TALK_TO_NPC phases work as mediation steps. Resolution doesn't have to be happy — sometimes people agree to disagree. The conflict should be petty enough for a village ("they moved the fence line") but feel real to the people involved.

---

### 12. Madness

**Description:** Someone is acting irrationally and the NPC is worried about them. Not clinical — a neighbor who's been making strange decisions, hoarding things, avoiding everyone, or fixated on something that doesn't make sense.

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

**Tone arc:** worried → relieved or bittersweet

**Authoring notes:** Handle with care. The "madness" should be sympathetic — stress, grief, fear, obsession — not mocking. The quest is about helping, not fixing. Resolution might be "they're okay, they just needed space" or "they needed this specific thing to feel safe again." Avoid framing mental distress as a problem the player solves with a fetch quest.

---

### 13. Self-Sacrifice for an Ideal

**Description:** The NPC is committed to something bigger than themselves — a project, a principle, a duty — and needs help seeing it through. They've given what they can and it's not enough.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** weary but resolute → fulfilled

**Authoring notes:** The NPC's commitment should feel admirable, not foolish. They're not asking for pity — they're asking for a hand because the cause matters. Exposition should establish what they've already given. Resolution should feel like completion, not rescue.

---

### 14. Self-Sacrifice for Kindred

**Description:** The NPC is doing something difficult for family. They need help because they've stretched themselves thin trying to provide, protect, or care for someone.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** devoted/anxious → relieved

**Authoring notes:** Use `{settlement_npc}` to ground the family member when possible. The emotional center is the relationship, not the task. "I need iron" is Obtaining. "I need iron because my kid needs a brace for their leg and I can't leave them alone long enough to get it" is Self-Sacrifice for Kindred.

---

### 15. Necessity of Sacrificing Loved Ones

**Description:** The NPC faces a painful choice that affects someone they care about. Delivering hard news, letting go, choosing the lesser harm. The quest puts the player in the middle of something heavy.

**Available objectives:** KILL_MOBS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** heavy/reluctant → somber acceptance

**Authoring notes:** The darkest tone in the MVP set. Resolution is acceptance, not happiness. The NPC isn't okay — they're enduring. Use sparingly. TALK_TO_NPC phases can carry the emotional weight ("tell {target_npc} that I can't..."). KILL_MOBS phases can frame the sacrifice as protecting someone by dealing with a threat they couldn't face together.

---

### 16. Loss of Loved Ones

**Description:** Grief, remembrance, seeking closure. Someone is gone and the NPC is dealing with it. The quest is about honoring, remembering, or finding peace.

**Available objectives:** KILL_MOBS, KILL_BOSS, FETCH_ITEM, TALK_TO_NPC

**Tone arc:** mourning → bittersweet peace

**Authoring notes:** The loss has already happened. The quest is about what comes after. FETCH_ITEM can be a keepsake or memento. TALK_TO_NPC can be "tell {target_npc} what happened" or "my mother's friend in {target_npc_settlement} should hear this from a person, not a letter." Resolution should feel like the NPC has taken one step forward, not that grief is resolved.

---

### 17. Ambition

**Description:** The NPC wants to build, create, or achieve something. Not a need — a want. They have a vision and need help making it real.

**Available objectives:** KILL_MOBS, FETCH_ITEM, COLLECT_RESOURCES, TALK_TO_NPC

**Tone arc:** driven/excited → proud

**Authoring notes:** The lightest dramatic situation alongside Obtaining. The difference: Obtaining is practical ("I need this"), Ambition is aspirational ("I want to make this"). The NPC should be enthusiastic, maybe slightly grandiose for their station. A cook who wants to create the perfect dish. A blacksmith who wants to build something they've never attempted. Keep the ambition scaled to a village.

---

### 18. Mistaken Jealousy

**Description:** The NPC suspects someone of something — disloyalty, scheming, betrayal — and they're wrong. The quest sends the player to find out the truth, which turns out to be innocent.

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

**Tone arc:** suspicious/anxious → sheepish/relieved

**Authoring notes:** The resolution must reveal the suspicion was unfounded. This is a comedy-of-errors situation, not a thriller. The NPC should feel a little foolish at the end. TALK_TO_NPC phases naturally frame as "go ask {target_npc} what they've been up to." The truth should be mundane — planning a surprise, helping someone privately, pursuing a harmless hobby.

---

### 19. Erroneous Judgment

**Description:** Someone has been wrongly blamed for something. The NPC believes they're innocent (or the NPC IS the wrongly blamed party) and wants the truth established.

**Available objectives:** FETCH_ITEM, TALK_TO_NPC

**Tone arc:** indignant/concerned → justice restored

**Authoring notes:** Similar to Enigma but the emotional center is injustice, not curiosity. Someone's reputation is at stake. TALK_TO_NPC phases can be "go talk to {target_npc} and hear their side." FETCH_ITEM can be evidence that clears someone. Resolution should feel like fairness won.

---

### 20. Remorse

**Description:** The NPC wronged someone and wants to make it right. They're carrying guilt and need help bridging the gap — delivering an apology, making a gesture, or retrieving something to offer as amends.

**Available objectives:** KILL_BOSS, TALK_TO_NPC, FETCH_ITEM

**Tone arc:** guilty → lightened

**Authoring notes:** The NPC should feel genuine remorse, not performative guilt. They're asking for help because they can't face the person directly, or because they need something to make the gesture meaningful. FETCH_ITEM framed as "bring me this so I can offer it to them" or "find this — it belonged to them and I need to return it." Resolution is the NPC feeling lighter, not necessarily forgiven.

---

### 21. Involuntary Crimes of Love

**Description:** Accidental harm caused by good intentions or a misunderstanding. The NPC did something that hurt someone they care about without meaning to, and now the relationship is damaged.

**Available objectives:** TALK_TO_NPC, FETCH_ITEM

**Tone arc:** regretful/confused → resolved

**Authoring notes:** Overlaps with Remorse but the key difference: in Remorse, the NPC knows what they did wrong. Here, the NPC may not fully understand what went wrong — they need help figuring it out as much as fixing it. TALK_TO_NPC phases can be "go talk to {target_npc} and find out why they're upset with me."

---

### 22. Obstacles to Love

**Description:** The NPC cares about someone and something stands in the way — distance, disapproval, circumstance, misunderstanding. The quest is about removing or navigating the barrier.

**Available objectives:** TALK_TO_NPC, FETCH_ITEM

**Tone arc:** earnest/nervous → hopeful

**Authoring notes:** Keep it grounded. This is village romance, not epic love story. The obstacle should be practical or social, not dramatic. "Her family doesn't approve" or "I haven't seen them since they moved to {target_npc_settlement}" or "I need to find something worthy to give them." Resolution is a step forward, not a wedding.

---

## Deferred Situations (post-MVP)

These situations are excluded from MVP due to mechanical limitations (need DELIVER_ITEM, ESCORT, or other objective types not yet implemented), tonal concerns, or insufficient differentiation from MVP situations.

| Polti # | Situation | Reason Deferred |
|---|---|---|
| 6 | Falling Prey to Cruelty/Misfortune | Folds into Disaster and Supplication |
| 9 | Conflict with a God | Needs more objective types for proper expression |
| 16 | Madness (Abduction variant) | Abduction excluded from MVP |
| 20 | Involuntary Crimes of Love (Adultery) | Tonal concerns for procedural village quests |
| 21 | Adultery | Tonal concerns |
| 22 | Murderous Adultery | Tonal concerns |
| 23 | All Sacrificed for Passion | Hard to proceduralize |
| 24 | Crimes of Love | Overlaps with 21, needs more objective types |
| 25 | Discovery of Dishonor of a Loved One | Needs more objective types for proper expression |
| 26 | An Enemy Loved | Very narrative-dependent, hard to proceduralize |
| 27 | Slaying of a Kinsman Unrecognized | Requires narrative reveal mechanics |
| 28 | Abduction | Needs ESCORT or similar objective type |
| 29 | Revolt | Implies political complexity the system can't express |

---

## Notes for Authors

**Situations are emotional frames, not mechanical prescriptions.** A Supplication quest with three KILL_MOBS phases is valid. A Daring Enterprise quest that's all TALK_TO_NPC is valid if the author frames the "daring" part as navigating dangerous social territory. The situation tells you how the NPC *feels* about the quest, not what the player *does*.

**Tone arcs are guidelines, not rails.** "Desperate → grateful" doesn't mean the exposition must contain the word "desperate." It means the NPC starts from a place of need and ends from a place of relief. How the author voices that is up to them.

**The available objectives list will expand.** When DELIVER_ITEM, ESCORT, or other types are implemented, deferred situations will be revisited and existing situations will gain new mechanical options. Author templates against what exists now; don't write text that implies mechanics that don't exist yet.

**Variable palette is in `quest_v2_variable_review.md`.** The quest variable palette is different from the smalltalk palette. Use `{enemy_type}`, not `{mob_type}`. Use `{target_npc}`, not `{npc_name}`. The variable review document is the canonical reference.
