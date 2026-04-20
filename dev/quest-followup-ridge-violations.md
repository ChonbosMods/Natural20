# R3 "Ridge" Violations: Handoff

## The problem

Three quest templates in `src/main/resources/quests/v2/index.json` reference a "ridge" (eastern ridge / south ridge / "past the ridge") as a spatial landmark. The game does not generate named ridges, ridgelines, or hill features as POIs or traversable landmarks. The player cannot "get to the ridge" or "clear the south ridge" because no such place exists in the world the generator builds.

These references were authored as scene-setting detail but function as invented locations in the player-facing text, creating a promise the world cannot deliver: the player looks for a ridge, finds none, and the quest beat reads as broken.

## The rule violated

**R3 (Quest Authoring Rules):** *No spatial modification of POI types.* From `authoring/quest_v2/quest_authoring_rules.md`:

> "You may reference a POI type if the settlement has one. You may NOT add detail that changes its meaning. 'The mine' is fine. 'The collapsed eastern shaft of the mine' is not. The game generates a mine. It does not generate shafts, rooms, floors, wings, or interior features."

Ridges are a stricter R2 violation in addition (no invented locations), but R3 is the cleaner frame: "the ridge" isn't a POI type at all, it's spatial color invented by the author.

**R2 (for completeness):** *No invented locations.* "'The area has been dangerous' is fine. 'The storehouse on the eastern ridge' is not: there is no storehouse POI type, and the game does not generate ridges."

## What the rewrite must do

1. Remove every mention of "ridge" / "ridgeline" / "south ridge" / "eastern ridge" / "past the ridge" / etc. from all text fields of the three templates.
2. Preserve each template's voice, emotional arc, and situation fit. These are not full rewrites; they are surgical excisions of the offending spatial language plus whatever local rewording the excision requires.
3. Where the ridge was load-bearing (e.g. `daring_enterprise_08` is literally about crossing unexplored terrain past the ridge), rework the expedition target into something the generator actually produces. Acceptable framings include:
   - "A stretch of land past the edge of what {settlement_name} has surveyed" (generic, no landmark)
   - "Country {settlement_name} hasn't walked in a generation" (temporal, no landmark)
   - Reference the general direction ("east of here", "out past the last farms") without naming terrain features
   - Let the `{enemy_type_plural}` carry the location load ("Wherever they've dug in")
4. Do not invent a replacement landmark (no "old quarry", "forgotten pass", "abandoned road"). The fix is to remove the spatial specificity, not swap one invention for another.
5. All other authoring rules apply: R7 (max 4 sentences per field), R13 (no colons or dashes), R14 (no corrective reframing), R8 (first person only), R29 (resolution closes), R30 (no mechanics).
6. Preserve every `{variable}` that was already in the field (`{settlement_name}`, `{enemy_type_plural}`, `{kill_count}`, `{target_npc}`, `{target_npc_role}`, `{target_npc_settlement}`, `{quest_item}`, `{quest_item_full}`, `{reward_item}`).
7. `daring_enterprise_08` has a `topicHeader` of "What Lies Beyond" that still works without a ridge. Keep it.

## Affected templates

### 1. `daring_enterprise_08` — topicHeader: "What Lies Beyond"

Situation: daring_enterprise. Tone arc: ambitious/bold → triumphant.

The ridge is load-bearing in this template: exposition, declineText, expositionTurnInText, AND targetNpcOpener all reference it. The quest's premise is "survey land past the eastern ridge." The fix must re-premise the expedition without inventing a substitute landmark.

**Current text (ridge references in bold):**

- `expositionText`: "There's a stretch of land past **the eastern ridge** that nobody from {settlement_name} has surveyed in living memory. I want to be the first. But {enemy_type_plural} have claimed the approach, and I need {kill_count} of them driven off before I can even begin."
- `acceptText`: "I've been dreaming about this for months. You just turned it from a dream into a plan." *(clean, no ridge)*
- `declineText`: "**The ridge** will wait for me. It's been waiting this long. But I can feel my nerve cooling every day I don't act."
- `expositionTurnInText`: "The approach is clear. I stood at **the ridgeline** this morning and looked east, and the land just kept going. We're doing this."
- `conflict1Text`: "Before I commit, I want counsel from someone who's actually explored uncharted territory. {target_npc} in {target_npc_settlement} is a {target_npc_role} who mapped the western valleys years ago. Ask them what I should expect and what mistakes to avoid." *(clean)*
- `conflict1TurnInText`: "{target_npc} gave you real field notes? I'm reading these and my hands are shaking from pure excitement. They confirmed what I suspected, and there's good ground out there." *(clean)*
- `conflict2Text`: "One last thing. I need a {quest_item} for the survey work itself. Proper equipment, not guesswork. If I'm going to claim I found something, I want the measurements to back it up." *(clean)*
- `conflict2TurnInText`: "Everything's ready. Maps, tools, provisions. I'm leaving at first light and I've never been more certain about anything." *(clean)*
- `resolutionText`: "I found a valley with water, good soil, and more space than {settlement_name} has used in a generation. I came back with a map and a grin I couldn't wipe off my face for three days. Take {reward_item}. When they draw the map of what I found, your name goes beside mine in the corner where anyone can see, for as long as there's a map to read. You made this possible, every step of it." *(mentions "a valley" as the discovery; this is acceptable because a valley is discovered, not an existing named landmark. Leave as-is.)*
- `targetNpcOpener`: "Surveying past **the eastern ridge**? The geography is promising if you approach from the northern slope, and I mapped the drainage patterns from a distance. There's a valley that looked fertile. Take my field notes and improve on them."
- `targetNpcCloser`: *(verify in-place)*

**Fields needing rewrite:** `expositionText`, `declineText`, `expositionTurnInText`, `targetNpcOpener`.

### 2. `self_sacrifice_for_an_ideal_07` — topicHeader: "Without Ceremony"

Situation: self_sacrifice_for_an_ideal. Tone arc: principled/quiet → steady. The NPC is an unpaid watchkeeper who walks the perimeter every night.

The ridge is spatial framing for where enemies have been thickening. The fix is lighter than daring_enterprise_08: the perimeter-walker already has a `{settlement_name}` perimeter to reference. "South ridge" can become the perimeter itself, or the direction ("south end", "south side"), without inventing new terrain.

**Current text (ridge references in bold):**

- `expositionText`: "I walk the perimeter of {settlement_name} every evening. Not for pay, not because anyone asked, just because I believe someone should. The {enemy_type_plural} along **the south ridge** have been thickening, and I can't thin them alone anymore. I need {kill_count} of them dealt with."
- `acceptText`: "Good. I'll use the breathing room to reinforce the watch points." *(clean)*
- `declineText`: "I'll keep at it. One pair of eyes is better than none." *(clean)*
- `expositionTurnInText`: "**The ridge** is quieter already. I walked it last night and the only sound was the wind, which is exactly how it should be. There's one more thing I've been putting off."
- `conflict1Text`: *(clean)*
- `conflict1TurnInText`: *(clean)*
- `resolutionText`: *(clean, ends on the perimeter being secure; no ridge reference)*

**Fields needing rewrite:** `expositionText`, `expositionTurnInText`.

### 3. `loss_of_loved_ones_07` — topicHeader: "The Unvisited Grave"

Situation: loss_of_loved_ones. Tone arc: grief-ridden → quietly at peace. The NPC wants to visit a sister's grave that's been cut off by enemies.

The ridge reference is minimal — only one instance, in expositionText. The fix is a single clause.

**Current text (ridge reference in bold):**

- `expositionText`: "My sister is buried past **the ridge**, and I haven't been able to visit since the {enemy_type_plural} moved in. I need {kill_count} of them cleared before I can get up there safely. It's been too long already, and the guilt of staying away is worse than the grief."
- All other fields: clean.

**Field needing rewrite:** `expositionText` only. Suggested shape: move "past the ridge" to something like "out past where I can walk safely right now" or "up where I can't get to", preserving the distance/inaccessibility beat without naming terrain.

## Output

Return a JSON patch of the form:

```json
{
  "daring_enterprise_08": {
    "expositionText": "...",
    "declineText": "...",
    "expositionTurnInText": "...",
    "targetNpcOpener": "..."
  },
  "self_sacrifice_for_an_ideal_07": {
    "expositionText": "...",
    "expositionTurnInText": "..."
  },
  "loss_of_loved_ones_07": {
    "expositionText": "..."
  }
}
```

Only include fields you rewrote. Do not include fields you left unchanged.

## Self-check before returning

1. Grep your output for "ridge". Should be zero hits.
2. Grep for ": " (colon-space) and " — " / " – " / " - " (dash). Should be zero hits.
3. Every `{variable}` present in the original is present in the rewrite.
4. Each rewritten field ≤ 4 sentences.
5. Read each rewrite aloud next to its template's other (unmodified) fields. Voice should match.
