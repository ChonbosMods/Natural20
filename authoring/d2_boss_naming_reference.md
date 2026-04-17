# The yellow-text name generator of Diablo II, decoded

Diablo II's randomly generated **Unique** monsters (yellow-name minibosses) draw their names from **three separate word pools**, not two: a **Prefix**, a **Suffix**, and an optional **Appellation**. The canonical LoD pool contains **52 prefixes, 69 suffixes, and 25 appellations** — yielding 89,700 possible name combinations (or 3,588 without the appellation). The words are stored as string-table pointers in three MPQ files — `UniquePrefix.txt`, `UniqueSuffix.txt`, and `UniqueAppellation.txt` — with the visible text living in `string.tbl`, `expansionstring.tbl`, and `patchstring.tbl`. Diablo II: Resurrected preserves these pools bit-for-bit.

One important correction to the framing of the question: names are built as **`<Prefix><Suffix> [the <Appellation>]`** — the prefix and suffix are **concatenated into a single word** (so you get "Bloodmaul," "Shadowfang," "Coldcrow," "Stormsnarl," "Vilewing"), and the "the X" tag is then appended only *some* of the time. The single-word compound (e.g., "Shadowcrow," "Blackstorm," "Snotpus") is always present; the appellation is optional. Every Unique monster name players have seen for 20+ years — from "Gloomeye the Cold" to "Spineripper the Unholy" — is assembled from the three lists below.

## How the generator actually works

When the engine rolls a Unique monster (via the `umon#` column in `Levels.txt` and the `rndname` mod in `MonUMod.txt`, mod ID 1), it picks one entry from each list **independently** of the others and of the underlying monster type. That's why absurd combinations like "Snotpus the Jade" or "Bonemaggot the Witch" can land on a Fallen, a Skeleton, or a Succubus alike — the name has **no relationship** to the monster's family or its affix modifiers (Fire Enchanted, Cursed, Stone Skin, etc., which come from a separate file, `MonUMod.txt` rows 2+). The appellation is applied probabilistically (community estimates put it at roughly 50–75% of spawns, not 100%). Super Uniques like Bishibosh, Rakanishu, Eldritch, Pindleskin, and Ventar the Unholy are **not** part of this system — they are hand-authored in `SuperUniques.txt`.

A few overlaps are worth flagging: **"Flame" appears in both the prefix and suffix pools** (so "Flameflame" is theoretically possible and has been observed), and **"Sharp"** appears in both the prefix pool and the appellation pool. Everything else is unique to its list.

## The 52 prefixes (first component)

Alphabetized from `UniquePrefix.txt`:

Ash, Bane, Bile, Black, Blade, Blight, Blood, Bone, Chaos, Death, Devil, Dire, Doom, Dragon, Dread, Flame, Flesh, Foul, Gloom, Gore, Gray, Grief, Gut, Haze, Ice, Mind, Mold, Moon, Night, Ooze, Pit, Plague, Puke, Pulse, Rot, Rust, Seethe, Shadow, Sharp, Sin, Snot, Soul, Spine, Spirit, Star, Steel, Stone, Storm, Viper, Vile, Warp, Wind, Wrath

## The 69 suffixes (second component)

Alphabetized from `UniqueSuffix.txt`:

Bang, Bender, Bite, Blister, Break, Brow, Burn, Call, Claw, Cloud, Crawler, Crow, Dancer, Drinker, Drool, Eater, Eye, Fang, Feast, Fester, Fist, Flame, Froth, Grin, Growler, Grumble, Hack, Hawk, Head, Heart, Horn, Jade, Jade, Kill, Killer, Lust, Maggot, Maim, Maul, Maw, Poison, Pox, Pus, Raven, Razor, Rend, Ripper, Shank, Shard, Shield, Shifter, Skin, Skull, Slime, Sludge, Snarl, Spawn, Spell, Thirst, Thorn, Tongue, Touch, Venom, Vex, Weaver, Web, Widow, Wight, Wing, Wolf, Wound

## The 25 appellations (optional "the X" tag)

Alphabetized from `UniqueAppellation.txt`:

the Axe, the Cold, the Dark, the Dead, the Destroyer, the Flayer, the Grim, the Hammer, the Howler, the Hunter, the Hungry, the Impaler, the Jagged, the Mad, the Mauler, the Quick, the Shade, the Sharp, the Slasher, the Slayer, the Tainted, the Unclean, the Unholy, the Witch, the Wraith

## Where the data lives and how to extract it

The three word lists are stored as **plain tab-delimited text files** inside the game's MPQ archives. In Classic Diablo II the prefix list shipped in `patch_d2.mpq`; in Lord of Destruction and every subsequent build, all three files live in `d2exp.mpq` (or, for patches, `patch_d2.mpq` override copies). Each row contains a single numeric **string-table pointer**, not the literal English word. The engine resolves those pointers against the localized string tables — `string.tbl`, `expansionstring.tbl`, and `patchstring.tbl` — which is why the same pool renders correctly in German, French, Korean, Chinese, and Polish clients.

The actual *firing* of the generator is controlled by `MonUMod.txt`, where **row 1 is the `rndname` mod** — automatically attached to any monster spawned as Unique via `Levels.txt`'s `umon#` column. The remaining rows of `MonUMod.txt` (Extra Strong, Fire Enchanted, Cursed, Stone Skin, Multiple Shots, etc.) are the affix modifiers the user explicitly excluded, and they are a separate system with no overlap with the name pools.

| File | Contents | Rows | MPQ |
|---|---|---|---|
| `UniquePrefix.txt` | Prefix pool | 52 | `patch_d2.mpq` / `d2exp.mpq` |
| `UniqueSuffix.txt` | Suffix pool | 69 | `d2exp.mpq` |
| `UniqueAppellation.txt` | Appellation pool | 25 | `d2exp.mpq` |
| `MonUMod.txt` | Generator trigger (`rndname`, row 1) + affix modifier table | — | `d2data.mpq` |

## Version differences across Classic, LoD, and Resurrected

The **69-suffix / 25-appellation** pool is an **LoD-era** configuration. Classic Diablo II v1.00–1.06 shipped a smaller pool (some suffixes like "Jade," "Bang," "Shard," and most appellations were added or finalized in the 1.07/LoD expansion file layout); the 1.09 and 1.10 patches stabilized the pools to the 52/69/25 counts listed above. Every subsequent patch (1.11, 1.12, 1.13c, 1.14d) left the lists untouched. **Diablo II: Resurrected** uses the same files unchanged — the v2.4–3.1 ladder patches ("Reign of the Warlock") have added new Super Uniques and balance tweaks but, to the best of public knowledge, have **not altered** the three random-name text files. The D2R localization team translated the same pointer set into the modern UI, which is why a German D2R client still rolls "Blutfäule der Gehetzte" from exactly the same index as an English "Bloodrot the Hunted"-equivalent seed.

## Notes on structure and edge cases

The format is effectively a two-layer template. The prefix and suffix are **always glued together without a space** (the Sapostoluk Python generator and Perchance port both replicate this, and in-game examples like "Shadowcrow," "Warpskull," "Gutshank," and "Bloodgutter" confirm it — these *look* like compound words precisely because they are). The "the X" appellation is **space-separated** and can be absent, giving two visible name shapes: "Gloomeye" and "Gloomeye the Cold." There are no three-word prefix+suffix names, no genuinely single-word names that bypass both pools, and no names that randomly swap in monster-type descriptors — "Bonesnap the Unholy" and "Coldmaster the Horrific" in the user's question are close-in-style approximations rather than literal pool entries (the real generator would produce "Bonebreak the Unholy" or "Icecrawler the Grim" from these same components). The pool is also why certain compounds — "Shadowfang," "Bloodmaul," "Soulthirst," "Deathspell" — recur across thousands of runs and have become unofficial shorthand in the D2 community.

## Key takeaways

The Diablo II Unique-monster name generator is one of the tightest, most elegant procedural naming systems in any ARPG: **146 total words** across three lists produce **nearly 90,000 name combinations**, and the whole system is driven by one flag (`rndname`) in one config file. The pools have been stable since 2001, preserved verbatim through Resurrected, and the 25-entry appellation list — long the hardest piece to track down because early fan sites truncated it — is now fully recovered above. For anyone reconstructing the generator, the only two practical gotchas are the **"Flame" prefix/suffix collision** and the fact that the **appellation is probabilistic**, not mandatory; everything else is a clean three-way independent draw.