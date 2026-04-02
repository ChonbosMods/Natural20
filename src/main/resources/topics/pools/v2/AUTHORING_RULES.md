# Pool Entry Checklist

For expanded guidance, variable reference, and voice direction, see `docs/authoring/`.

- Schema and rules: `docs/authoring/pools.md`
- Voice (intense categories): `docs/authoring/rumor-pools.md`
- Voice (mild categories): `docs/authoring/smalltalk-pools.md`
- Writing details/reactions/statCheck: `docs/authoring/detail-responses.md`

## Entry Format

Each entry: `id` (int), `intro` (string), `details` (2-3 strings), `reactions` (2-3 strings), optional `statCheck` ({ pass, fail }).

## Rules

1. No hardcoded NPC names. Use role references or `{npc_name}` for other NPCs. First person for self.
2. Details must directly continue the intro.
3. Reactions close the same emotional arc as the intro.
4. Use `{subject_focus_the}` (mid-sentence) and `{subject_focus_The}` (sentence-start). Never manual "the {subject_focus}".
5. Use conjugation helpers (`{subject_focus_is}`, `_has`, `_was`) when subject is sentence subject.
6. No em dashes. Use colons.
7. statCheck pass/fail text is NPC dialogue, never second-person narration.
8. statCheck pass reveals hidden information. statCheck fail deflects naturally.
9. Author 2-3 details per entry. System shows 0-2 (70% chance each, max 2).
10. Stat distribution target: CHA ~50%, WIS ~25%, INT ~15%, STR/DEX/CON ~10%. Write statCheck text to match the template's skill type.
11. Write natural, conversational NPC speech. Short sentences. Concrete observations.
12. Each entry in a pool should feel distinct: different angles of the theme.
13. Biome-neutral: universal terrain features only.
14. `{npc_name}` is for mentioning OTHER NPCs, never self-reference.
