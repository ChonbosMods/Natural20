# Pool Entry Authoring Rules

## Structure

Each entry: `id` (int), `intro` (string), `details` (2-3 strings), `reactions` (2-3 strings), optional `statCheck` ({ pass, fail }).

## Writing Rules

1. **No hardcoded names.** Never name a specific person (e.g. "Old Maren", "Farmer Gareth"). NPCs are spawned dynamically: any name you invent will not match a real NPC in the world. Use role-based references ("the old potter", "a hunter", "the herbalist") or `{npc_name}` to reference other NPCs by their generated name. **The speaking NPC should always use first person** ("I", "me", "my", "myself"): never `{npc_name}` for self-reference. `{npc_name}` is for when the speaker mentions another NPC by name, making the world feel connected.

2. **Details must directly continue the intro.** If the intro mentions strange tracks, the details must be about those tracks. Never write generic details that could fit any intro.

3. **Reactions close the same emotional arc.** If the intro is frightening, the reaction should express fear, resolve, or unease about that specific thing.

4. **Variable usage:** `{subject_focus_the}` for locations (lowercase). `{subject_focus_The}` at sentence start only. `{npc_name}` to name-drop another NPC in the settlement (never for self-reference: use "I"/"me"). `{time_ref}` and `{direction}` sparingly in details for grounding.

5. **No em dashes.** Use colons instead.

6. **statCheck pass/fail text is always NPC dialogue.** The NPC is speaking, not a narrator. Never write second-person narration like "You look around and see nothing." Instead write what the NPC says in response: "You are looking in the right place, but there is nothing left to find." The skill check result line (`[WIS] Perception 8 : Failure`) already communicates the mechanical action.

7. **statCheck pass text reveals hidden information** the NPC couldn't or wouldn't share voluntarily. The NPC reacts to the player's demonstrated competence.

8. **statCheck fail text is the NPC's response to a failed attempt.** The NPC either didn't notice the attempt, brushes it off, or redirects. Never narrate what the player does or sees.

9. **Author 2-3 details per entry.** The system randomly includes each detail (70% chance per detail, capped at 2). A topic might show 0, 1, or 2 detail prompts. Author enough that any subset works. There is no decisive/end-topic response: topics exhaust naturally when the player has explored the available options.

10. **Write as a real NPC speaks.** Natural, conversational, not flowery or overwrought. Short sentences. Concrete observations over abstract commentary.

11. **Each entry in a pool should feel distinct.** Cover different angles of the theme: different causes, different witnesses, different evidence, different emotional responses.
