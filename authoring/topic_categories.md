# Topic Categories

Each pool entry belongs to exactly one category. Categories control pool balance, entity requirements, and the emotional register of the entry.

Target percentages are guidelines for overall pool balance across all settlements, not per-settlement hard constraints. A settlement with a mine should naturally produce more `poi_awareness` entries. A settlement with many NPCs should produce more `npc_opinions`.

---

## `mundane_daily_life`

**Description:** Weather, food, trade, sleep, work, daily routine. The boring baseline. These entries have zero dramatic content and exist to make NPCs feel like people with lives. No entity dependencies.

**Target percentage:** ~30% of pool

**Valence distribution:** ~70% neutral, ~15% positive, ~15% negative

**Required entities:** none

**Location scope:** `universal`

**Example prompts:**

1. "Slept through half the morning. First time in weeks I didn't have something pulling me out of bed before dawn."
2. "My back's been killing me. Too much lifting, not enough rest. That's the life, I suppose."
3. "Had a good stew last night. Simple: just root vegetables and salt. Sometimes simple is best."
4. "I've been meaning to mend my fence for a month now. Every morning I look at it, and every morning I find something else to do instead."
5. "The rain's been steady for three days. Good for the ground, bad for my mood."
6. "Traded some wool for a new belt yesterday. Fair deal. Better than fair, honestly."
7. "The mornings have been cold. Not winter cold, just that kind of cold that makes you want another hour under the blankets."
8. "I burned dinner last night. Fell asleep by the fire and woke up to smoke. Not my finest evening."

**Anti-examples:**

1. "The weather has been exhibiting unusual patterns consistent with arcane interference." : Narrator voice, not a person talking. Information briefing, not an observation.
2. "Strange winds have been blowing from the north, carrying whispers that no one can quite make out." : Quest hook. Mystery setup. The player will want to investigate the whispers.
3. "The harvest this year has been the worst in living memory, and unless something changes, the settlement faces a crisis of food security." : Multi-beat narrative with dramatic escalation. A real person would say "Harvest was bad this year."
4. "Food supplies are running low due to disrupted trade routes from the eastern provinces." : Invented events (disrupted trade routes), narrator voice, geopolitical briefing.
5. "I've noticed the days growing shorter at an unnatural rate. Something is wrong with the sun." : Mystery hook disguised as weather talk.

---

## `npc_opinions`

**Description:** Opinions about other named NPCs in the settlement. Gossip, complaints, admiration, petty grievances. The core Oblivion-style dialogue: NPCs talking about people, not events.

**Target percentage:** ~25% of pool

**Valence distribution:** ~35% positive, ~35% negative, ~30% neutral

**Required entities:** 1-2 NPC references

**Location scope:** `local`

**Example prompts:**

1. "{npc_name} borrowed my best saw three weeks ago and hasn't said a word about returning it."
2. "I like {npc_name}. Never has a bad word for anyone. You don't find that often."
3. "{npc_name} talks too much. You ask a simple question and get the whole history of the province."
4. "{npc_name} and {npc_name_2} have been spending a lot of time together. I don't know what they're planning, but they're always whispering."
5. "Say what you will about {npc_name}, but when my roof leaked last autumn, that was the first person at my door with tools."
6. "{npc_name} thinks they're better than the rest of us. You can see it in the way they walk."
7. "I don't trust {npc_name}. Can't say why exactly. Just a feeling."
8. "{npc_name} makes the best bread in this settlement. Won't share the recipe, though. I've asked."

**Anti-examples:**

1. "{npc_name} has been acting strangely ever since returning from the northern ruins. I fear something may have followed them back." : Quest hook. Mystery setup. Invented event (trip to northern ruins).
2. "{npc_name} is secretly meeting with agents from the eastern kingdom to negotiate a trade deal that could change the settlement's future." : Invented event, dramatic escalation, geopolitical briefing.
3. "The relationship between {npc_name} and {npc_name_2} reflects the broader tensions within the settlement's leadership structure." : Narrator voice. No person talks like this.
4. "{npc_name} was seen carrying a strange artifact through the market at midnight." : Quest hook. Mystery object. Dramatic framing.
5. "I believe {npc_name} knows more about the disappearances than they're letting on." : Quest hook. References invented events (disappearances).

---

## `settlement_pride`

**Description:** Feelings about living in this settlement. Town pride, complaints about local life, comparisons to other places. Ranges from boastful to resigned.

**Target percentage:** ~15% of pool

**Valence distribution:** ~40% positive, ~30% negative, ~30% neutral

**Required entities:** settlement name; optionally other settlement name

**Location scope:** `local` or `regional`

**Example prompts:**

1. "Nice, friendly folk in {settlement_name}. Most of them, anyway."
2. "{settlement_name} isn't much to look at, but it's home. I've made my peace with it."
3. "I've heard things are good in {other_settlement}. Can't say the same for here, but we get by."
4. "You could do worse than {settlement_name}. Good people, honest work, clean water."
5. "I'm not going to pretend this is the finest settlement in the land. But it's ours, and that counts for something."
6. "{settlement_name}'s not for everyone. If you like excitement, you're in the wrong place. If you like quiet, pull up a chair."

**Anti-examples:**

1. "{settlement_name} was founded two hundred years ago by settlers from the coastal provinces who were driven inland by rising tides." : Lore dump. No NPC talks like a history textbook.
2. "The settlement has experienced unprecedented growth following the discovery of rare minerals in the nearby caves." : Narrator voice, invented events, economic briefing.
3. "{settlement_name} sits at a critical crossroads between the eastern trade routes and the western frontier, making it a target for rival factions." : Geopolitical briefing, dramatic framing, invented geography.

---

## `poi_awareness`

**Description:** Awareness of local POIs without dramatic framing. NPCs know the mine exists, the farm is busy, the shrine is old. They don't report strange lights or mysterious events at POIs.

**Target percentage:** ~10% of pool

**Valence distribution:** ~40% neutral, ~30% positive, ~30% negative

**Required entities:** 1 POI type

**Location scope:** `local`

**Example prompts:**

1. "The {poi_type}'s been busy lately. More coming and going than usual."
2. "I don't go near the old {poi_type}. Never have. Just don't like the feel of it."
3. "The {poi_type} keeps this place running, whether people want to admit it or not."
4. "I used to work at the {poi_type} when I was younger. Hard work, but honest."
5. "The {poi_type} could use some repairs. Everyone says so, nobody does anything about it."
6. "I walk past the {poi_type} every morning. Quiet place. I like that about it."

**Anti-examples:**

1. "Strange lights have been appearing at the {poi_type} after midnight. Someone should investigate." : Quest hook. Mystery setup.
2. "The {poi_type} was built on the site of an ancient shrine, and the workers have reported hearing chanting from beneath the foundations." : Invented interior details, lore dump, quest hook.
3. "The {poi_type} produces most of the settlement's income and employs thirty workers from the surrounding villages." : Information briefing. Invented specifics (thirty workers, surrounding villages).
4. "There's a collapsed tunnel on the {poi_type}'s east face that nobody talks about." : Invented spatial detail below POI type abstraction.
5. "The {poi_type} has been operating at reduced capacity since the accident last month." : Invented event (accident), dramatic framing.

---

## `creature_complaints`

**Description:** Mundane grumbling about local wildlife and mob types. NPCs are annoyed by creatures, not threatened by organized attacks. Pests, not invaders.

**Target percentage:** ~10% of pool

**Valence distribution:** ~60% negative, ~25% neutral, ~15% positive

**Required entities:** 1 mob type

**Location scope:** `local`

**Example prompts:**

1. "{mob_type} and rats. Seems like they're everywhere lately."
2. "I saw {mob_type} near the road yesterday. Didn't bother me, but I walked a bit faster."
3. "The {mob_type} have been a nuisance this season. Getting into everything."
4. "Used to be you could walk the roads without worrying about {mob_type}. Not anymore."
5. "I don't mind the {mob_type} so much. They stay out of my way, I stay out of theirs."
6. "My neighbor lost a chicken to {mob_type} last week. Just one, but it's the principle."

**Anti-examples:**

1. "A {mob_type} warband has been organizing raids on the southern farms. The guard is overwhelmed." : Military framing, invented event (organized raids), quest hook.
2. "The {mob_type} have grown bolder since the ancient seal was broken in the mountain pass." : Lore dump, invented event, quest hook.
3. "Reports suggest the {mob_type} population has increased 300% in the past month, possibly due to migration from the eastern wastelands." : Narrator voice, invented statistics, geographic invention.
4. "Something is driving the {mob_type} down from the mountains. They seem afraid of something even worse." : Quest hook. Mystery setup. Escalating drama.

---

## `distant_rumors`

**Description:** Things heard about other settlements. Light, secondhand, often unreliable. The NPC doesn't know much and doesn't pretend to. Gossip, not intelligence reports.

**Target percentage:** ~10% of pool

**Valence distribution:** ~35% neutral, ~35% negative, ~30% positive

**Required entities:** 1 other settlement name

**Location scope:** `regional`

**Example prompts:**

1. "I heard things are good in {other_settlement}. Can't say the same for here."
2. "A trader from {other_settlement} came through last week. Said business is slow everywhere."
3. "I've got a cousin in {other_settlement}. Haven't heard from them in a while. Probably fine."
4. "They say {other_settlement} has better food than we do. I doubt it, but I've never been."
5. "Word is {other_settlement} is growing fast. Good for them, I suppose."
6. "I met someone from {other_settlement} once. Seemed nice enough. Talked too much about their market, though."

**Anti-examples:**

1. "Refugees have been streaming in from {other_settlement} after the dragon attack destroyed their granary." : Invented events (dragon attack, destroyed granary), dramatic narrative.
2. "Intelligence from {other_settlement} suggests a cult has infiltrated their ruling council." : Narrator voice, invented events, espionage framing, quest hook.
3. "The alliance between {settlement_name} and {other_settlement} is fracturing due to trade disputes that could erupt into open conflict." : Geopolitical briefing, invented events, dramatic escalation.
