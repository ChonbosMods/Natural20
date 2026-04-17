# Phase 2 Batch 2 — External Review Set (14 templates)

All templates are **dramatic** quest templates (target `src/main/resources/quests/v2/index.json`, scored against Polti's 22 situations). Not mundane-pool bounty templates.

Curated from 39 new templates (41 authored; 2 stashed in `deferred_humanoid_only/` pending a future `bossCategory` schema filter). Covers all 6 tonal clusters, all 4 chain shapes (single / COLLECT+BOSS / FETCH+BOSS / TALK+BOSS), and exercises the high-risk surfaces flagged in prior review rounds: variable-constraint leakage, pronoun assumptions in target NPC dialogue, boss-action humanoid-coding, chain-integrity on layered setups.

**Two templates deferred** out of the originally-selected set per reviewer feedback:
- `remorse_14` (What I Sold): commerce-based plot requires a humanoid boss. Stashed.
- `pursuit_17` (I Know The Type): reformed-bandit voice requires a humanoid boss. Stashed.

Substitutes swapped in: `remorse_15` (sister's keepsake, also confession-arc but boss-action-agnostic), `pursuit_15` (traveler-trader who carried a name home, boss-type-neutral).

**Internal audits already passed:** no rewardFlavor usage; no boss leak into pre-conflict fields on two-phase; no kill_count in boss-bound text; no colons or em-dashes; no R14 pattern hits; exposition-objective coherence on every two-phase; single-phase templates keep expositionTurnInText populated.

## Cold Revenge

### `vengeance_13` — *An Old Limp*
**Chain:** single · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Humanoid-action plot (father, cane). Tests whether the generic-boss language holds up when the exposition plot beat is inherently human-shaped.

**expositionText**
> My son walks with a limp, and he will walk with it until the day he goes in the ground. {boss_name} is the reason, and I have known the name for three years now. I am past the age where I can see to it myself, but you are not. If you are the one who finishes this, I will not forget you.

**acceptText** / **declineText**
> ✓ Good. A willing hand is what I have been waiting for, and you are one. Do what needs doing.
>
> ✗ Of course you will not. Nobody has, and I do not know why I expected you to be different. Go on, then.

**expositionTurnInText**
> So the name is finished. I heard it from a trader on the road before you even came back, and I sat down where I stood. {boss_name} is not a sound I will have to carry anymore.

**resolutionText**
> Come with me to the window. My son is out there teaching his own boy to throw a stone, and today he looked up at me without that shadow in his face. Take {reward_item}. I was saving it for a day that felt like an ending, and today does.

**skillCheck:** PERCEPTION · DC 13
> pass: You see the cane by the door, and you see how worn the handle is. My son uses his own now, but that one on the nail was mine, from the year I did what I could to stand between them and it was not enough. I gave it to him the day he learned to walk again.
>
> fail: He is strong in the ways that matter. That is all I will say about him today.

---

### `remorse_15` — *What I Left Behind*
**Chain:** FETCH+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Complicity remorse via a sister's keepsake. Confession skillCheck ('I hid in the back of a traveler's cart') is R14-borderline on its self-revelation beat — watch the rewrite.

**expositionText**
> There is a {quest_item} of my sister's with a trader in {other_settlement}, and I could not bring myself to go and claim it after what happened. I need it back in this house before I can speak the rest of what I owe her memory. If you would go and collect it for me, I can say the part I have been putting off. I have not been ready until now, and I am trying to be.

**acceptText** / **declineText**
> ✓ Thank you. I have been holding this in for so long I forgot I was holding it, and something in me loosened when you agreed.
>
> ✗ I understand, and I am not going to ask you twice. This is mine to carry one way or another, and I should not have handed it to a stranger.

**expositionTurnInText**
> You brought it. I had not seen it since the week it happened, and my hands do not quite know what to do with it yet. Sit down a moment, and I will tell you the part I could not say before.

**conflict1Text** *(boss reveal)*
> The night {boss_name} came, I was supposed to be with her, and I was not, because I had drawn them to that road a week earlier and I was afraid to show my face. She went in my place, and she did not come back. I want {boss_name} finished by the same hands that brought this home to me. No one else should stand in for me again.

**conflict1TurnInText**
> So it is over. I thought I would have more to say when the day came, and I find I do not. I am going to sit with her things a while, and I think for the first time that will feel like company instead of a wound.

**resolutionText**
> I put her {quest_item} back where she used to keep it, and the house feels like hers again for the first time in a year. Please take {reward_item}. She would have liked you, and I would rather something of hers go out the door with someone who earned it than sit in a drawer while I pretend I am the one who deserves to keep it.

**skillCheck:** INSIGHT · DC 14
> pass: I hid in the back of a traveler's cart the day she went. I could have walked back with her. I told myself I was being careful, and I have spent a year learning that what I actually was had a harder word for it.
>
> fail: She was the better of us, and she is gone. There is not much more to it than that, from my end.

---

## Hot Grief

### `loss_of_loved_ones_15` — *My Wife's Unfinished Row*
**Chain:** COLLECT+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Wife's unfinished work. {quest_item} pool-agnosticism surface: 'spend the evening with her tools' — does that hold if the item is grain or ore?

**expositionText**
> My wife passed two winters ago, and there is work of hers I have never been able to finish for her. I need {gather_count} of {quest_item} to do it properly, and I cannot bring myself to gather them for some reason. If you'd do that for me, I could sit with her things one last time and actually get somewhere.

**acceptText** / **declineText**
> ✓ Thank you. I didn't know I'd been waiting for someone to offer until you did.
>
> ✗ That's alright. It's been two winters already, so it can wait a little longer.

**expositionTurnInText**
> You brought plenty. I'll spend the evening with her tools and see how far I get. It's strange how much lighter I feel just having these in front of me.

**conflict1Text** *(boss reveal)*
> There is one more thing, and I'd be a coward not to say it now. I finally know what took her. It goes by {boss_name}, and for two winters I told myself the name didn't matter. It matters. Put {boss_name} down for me so I can finish her work without it breathing on my shoulder.

**conflict1TurnInText**
> It is done. I thought I might weep, and I haven't, and that surprises me more than anything. I'll finish her work tomorrow with steady hands for the first time.

**resolutionText**
> Take {reward_item}, please. She bought it for me our first year together and I have kept it folded away because I couldn't bear to use it. You've given me back the part of my life where her things can be used and not just mourned.

**skillCheck:** INSIGHT · DC 13
> pass: The truth is I stopped doing her work because finishing it felt like admitting she was really gone. I know how that sounds. I'm sixty-four years old and I have been hiding from a row of unfinished stitches like a child.
>
> fail: It's been two winters. Some things don't mend on a schedule.

---

### `loss_of_loved_ones_20` — *A Letter Never Sent*
**Chain:** TALK+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Estranged-brother arc with explicit male pronouns on the deceased. Pronoun test: deceased is authorially pinned male, questgiver is they/them via the target NPC.

**expositionText**
> My brother and I stopped speaking before he died last year, and I have carried that silence around like a second pack ever since. There's a message I need {target_npc} in {target_npc_settlement} to hear from me, and I can't put it in writing because writing is what got us into this. Would you go see them and carry back what they say?

**acceptText** / **declineText**
> ✓ Thank you. I should have done this the week after he passed, and instead I waited a full year. That's on me.
>
> ✗ That's fair. It's been a year of me not doing it, so one more refusal won't change much.

**expositionTurnInText**
> You went. You really did. Whatever they said to you, I can tell by your face they didn't hate me for it. That's already more than I expected from this week.

**conflict1Text** *(boss reveal)*
> I didn't tell you everything because I wanted them to hear it first. My brother didn't die the way people around here say he did. The thing that took him goes by {boss_name}, and I've known the name for six months and done nothing because I thought hate wasn't a good enough reason. I've changed my mind. End {boss_name}, please, and come back when it's quiet again.

**conflict1TurnInText**
> Quiet. That's the word for it. I have spent a year waking up to a fight I never got to finish, and this morning I woke up without it for the first time. I don't quite know who I am without that weight yet.

**resolutionText**
> Please, take {reward_item}. It was our father's, and my brother and I fought about who should have it for the better part of a decade. I am tired of objects being the reason people don't speak. You've earned it more than either of us ever did.

**targetNpcOpener / targetNpcCloser**
> opener: So they finally came around. I've been expecting this knock for months, honestly. Your friend and their brother were both too stubborn to live, and only one of them had to learn that the hard way.
>
> closer: Tell them I forgive them on his behalf, as much as that's worth from someone who wasn't his blood. And tell them to come see me when they're ready to actually sit with it.

---

### `loss_of_loved_ones_23` — *Our Children's Father*
**Chain:** single · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Widow with young children, boss kill as closure for a story-to-tell. Short and emotionally direct.

**expositionText**
> My husband was killed five months ago and our two children still ask me every night when he's coming home. I can't answer them properly while the thing that did it is still out there. It goes by {boss_name}, and I learned the name from a man who passed through {settlement_name} last week and didn't know what it meant to me. End {boss_name}, and I'll finally be able to tell my children something true.

**acceptText** / **declineText**
> ✓ Thank you. Nights are the hardest, and I've been rehearsing what to tell them for weeks.
>
> ✗ I understand. You've your own family to think about, I'm sure. I shouldn't have put you in the middle of mine.

**expositionTurnInText**
> It's finished. I told the children tonight, simply, with the name and the ending both. They cried, and then they slept, and then I cried alone in the kitchen, which is exactly how it needed to go.

**resolutionText**
> Please, take {reward_item}. It belonged to him, and I'd rather it be with someone who helped us than sit in a drawer reminding me of him every time I open it. Our children will grow up knowing his story has an ending, and that's because of you.

**skillCheck:** INSIGHT · DC 13
> pass: I am so angry I can hardly breathe sometimes, and I can't be angry in front of them, so I am angry in the pantry and the garden and the dark. Tonight I might be angry one more time, and then maybe less tomorrow.
>
> fail: I've got two children watching me, so I don't get to fall apart. That's the long and short of it.

---

## Protective

### `deliverance_14` — *Bring Her Home*
**Chain:** COLLECT+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Missing daughter + preparing house for return. Setup exposition is about 'readying the house' — tests whether that reads for any {quest_item} (cloth, herbs, stone, tools).

**expositionText**
> My daughter has been gone three nights now and I've been doing what I can to ready the house for her return. I need {gather_count} {quest_item} before anything else, so I can finish what I've been preparing for her. I haven't slept since the first night, and my hands aren't steady enough to see it through alone. Help me with this part first, friend, and then I'll tell you the rest.

**acceptText** / **declineText**
> ✓ Thank you. You've given me something to do besides stand at the door and listen.
>
> ✗ Please, she's a child. If you won't help then I'll go myself, and we both know how that ends.

**expositionTurnInText**
> Set it down there, that's perfect. Now sit with me a moment, because the reason she isn't home yet is the harder piece of this, and I couldn't say it with my hands full.

**conflict1Text** *(boss reveal)*
> {boss_name} took her. I know it because I saw the shape of it at the edge of the fields the night before, and I know it because nothing else moves the way the ground moved when she went missing. It runs with the {group_difficulty} kind, but {boss_name} is the one that carried her off. End it, and bring her back to the door I've been waiting at.

**conflict1TurnInText**
> She's at the table. She's not talking yet and she won't let me out of her sight, but she's eating. I don't have words for what you did. I'll find them later.

**resolutionText**
> Take {reward_item}, please. I was saving it for her naming day but she'd rather you have it and so would I. Come see her in a week or two, when she's ready to meet the one who brought her home.

**skillCheck:** INSIGHT · DC 13
> pass: I keep picturing it. Every quiet minute in this house I picture what it did to her, what she saw, what she thought when I wasn't there. I'd trade ten years of my life to take those pictures out of my head.
>
> fail: I'm fine. Please, just help me, there's no time for talking it out.

---

### `deliverance_17` — *The Family Next Door*
**Chain:** TALK+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Family-next-door rescue with TALK setup. Target NPC is a veteran of this kind of threat. Pronoun surface in opener/closer.

**expositionText**
> The family next door hasn't opened their door in two days, and I can hear the children crying through the wall at night. Something is hunting their house specifically and it's getting bolder every time it comes back. I sent word to {target_npc} over in {target_npc_settlement} because they've seen this kind of thing before and I need their read before I do anything stupid. Go speak with {target_npc} first, please, I'll lose my nerve if I try to do this blind.

**acceptText** / **declineText**
> ✓ You're a steadier soul than I've been all week. Go carefully, and come back as soon as you know what they know.
>
> ✗ Those children, friend. I can hear them. If nobody helps me then I'll walk over there myself tonight, and I'm not the person for that kind of walk.

**expositionTurnInText**
> So it's what we feared. Sit down, because now I have to ask you the harder thing, and I've been working up to it since you left.

**conflict1Text** *(boss reveal)*
> {boss_name} is what's been hunting them. {target_npc} confirmed what I suspected, and they say it's singled out that house for reasons only it understands. A {group_difficulty} thing, grown too comfortable with our street. End it, so those children can open a window without their mother holding her breath.

**conflict1TurnInText**
> Their door opened an hour ago. The smallest one came out first and just stood in the sun. I've never been so glad to see a child look bored.

**resolutionText**
> They sent this over for you. Take {reward_item} with their thanks, and mine, and whatever else a person can send through a wall. The mother says come by when you next pass through {settlement_name}, she wants to look at you properly.

**targetNpcOpener / targetNpcCloser**
> opener: They sent you, then. Good. I've watched three families lose their peace to this kind of thing and every time it starts the same way, with a house that goes quiet. The family next to them is right to be frightened.
>
> closer: Tell them to latch the shutters on the side closest to the fields and to stop leaving the lamp lit past dark. And tell them I'm thinking of them.

**skillCheck:** PERSUASION · DC 12
> pass: I'm ashamed to say it, but I almost packed a bag yesterday. Almost left the whole row of houses to handle itself. I'm glad you came before I gave in to it.
>
> fail: I'll hold the line here. Just go, please, we don't have the evenings for talking.

---

### `self_sacrifice_for_kindred_15` — *What The Mill Needs*
**Chain:** FETCH+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Mill owner trading stores to a bargaining boss. Most internally complex confession arc of the batch. Check whether 'bargaining' boss behavior reads for non-humanoid.

**expositionText**
> The mill is the last thing my family owns outright and I'm close to losing it. I had a {quest_item} on order with a trader who passes through {other_settlement}, and if I don't get it here soon the whole season's work grinds to a stop. I can't leave the mill unattended long enough to go myself, and my wife and the children need the flour more than they need me on the road. Fetch it back for me, friend, and we'll talk about the harder thing after.

**acceptText** / **declineText**
> ✓ Thank you. I've been carrying this in my chest and I can feel it start to sit lower. That's your doing.
>
> ✗ Then the mill goes and we go with it. I won't ask twice, but I'll remember the asking.

**expositionTurnInText**
> You brought it back and the mill can run again, which means my family eats this winter. Sit a moment, because the reason I couldn't leave it alone is the part I've been holding back.

**conflict1Text** *(boss reveal)*
> {boss_name} has been bargaining with me. That's the word for it. It comes at dusk and it takes one thing and it leaves, and if I give it the one thing it leaves quietly, and if I don't it takes two. I've been feeding a {group_difficulty} predator from my own stores to keep it off my family, and I can't do it any longer. End it, so I can stop trading pieces of us away.

**conflict1TurnInText**
> I put the full ration on the table tonight. My youngest asked why there was so much and I said because we have it, and I watched her believe me for the first time in months.

**resolutionText**
> Take {reward_item}, please. It was part of what I'd been setting aside for the bargain, and I'd rather it go to the one who ended the bargaining. Come through {settlement_name} again and the mill will be running for you.

**skillCheck:** INVESTIGATION · DC 13
> pass: It's been longer than I said. A full year, going all the way back to last winter. I've been handing things over since then and lying to my wife about where the stores keep going. She thinks I'm a worse manager than I am, and I'd rather she think that than know what's actually been happening.
>
> fail: It's been hard. That's as much of it as I feel like putting into words right now.

---

## Proactive

### `pursuit_15` — *A Name From The Road*
**Chain:** single · **Valence:** neutral · **Role affinity:** Traveler

**Review focus:** Traveler-trader who carried a name home from another settlement. Tests whether the 'I'd rather see it finished before it spreads over the roads I actually use' framing holds against any boss type.

**expositionText**
> I trade through {other_settlement} a few times a year, and the same name keeps coming up over their cups and over mine. {boss_name}. The people there have been putting up with a pattern long enough to learn it by heart, and I'd rather see it finished before it spreads over the roads I actually use. I've carried that name home. Time it stopped moving.

**acceptText** / **declineText**
> ✓ Good. I had a feeling you'd say yes, and I'm glad to be right for once.
>
> ✗ Fair enough. I'll keep asking down the road. The name doesn't stop moving just because you did.

**expositionTurnInText**
> So it's done. I'll be able to go back through {other_settlement} next season without hearing {boss_name} for the tenth time in a row. That's a trip I don't have to dread anymore.

**resolutionText**
> Take {reward_item}, you earned it going in alone on a name I brought home. I can get back to trading now and stop carrying something that was never mine to carry.

**skillCheck:** INVESTIGATION · DC 13
> pass: You want specifics, you can have them. Three settlements on my loop have stories now. Same pattern in every one, just different seasons and different faces at the funerals.
>
> fail: The details run together after a while. Enough that I'm sure, which is why I'm asking.

---

### `pursuit_19` — *A Second Try*
**Chain:** TALK+BOSS · **Valence:** neutral · **Role affinity:** (any)

**Review focus:** 'Second try' after a prior failed attempt. Target NPC is the one who pulled them out last time. Pronoun test + check whether 'growing into itself' reads for any boss type.

**expositionText**
> I went after this myself a year and change ago, and I came back a lot worse than I went out. Before I ask you anything else, I'd like you to go see {target_npc} over in {target_npc_settlement}. They pulled me out of it last time and they know the ground better than anyone I could point you to. A {target_npc_role}'s eyes on this will tell you more than my mouth ever could.

**acceptText** / **declineText**
> ✓ Thank you. I've been sitting on this longer than I wanted to, and it helps just knowing someone else is moving on it.
>
> ✗ Your call. I understand, and I'll find another way around to it. The problem doesn't go anywhere while I wait.

**expositionTurnInText**
> So you found {target_npc}. Good. I wanted you to hear it from someone who was on the ground that day instead of just from the person who got carried off it. Now you've got what I couldn't give you myself.

**conflict1Text** *(boss reveal)*
> The name is {boss_name}. Same name that put me on my back last year, and I'd like it gone for good this time. Someone steadier than I am needs to finish it, and I want it done properly.

**conflict1TurnInText**
> Quiet, isn't it. I thought hearing it finished would land harder than it did. Mostly I just feel like I can breathe without bracing for next season.

**resolutionText**
> Here, take {reward_item}. I had it set aside in case I ever got a second run at this, and a second run is what you gave me. I can walk past a mirror again without the old anger looking back.

**targetNpcOpener / targetNpcCloser**
> opener: So they finally asked for help. Good. I carried them out of that fight with my own hands, and I've been waiting to see if they'd ever be ready to try again. They're not the same person they were going in, and I think that's why they're ready now.
>
> closer: Tell them I said it's about time. And tell them I'll come down and see them when it's done.

**skillCheck:** HISTORY · DC 13
> pass: You've seen this kind of thing before, haven't you. Then you know they don't get weaker with time. I went in thinking I'd catch it while it was still growing into itself. I won't make that mistake for you.
>
> fail: It's old business. What matters is finishing it, not dressing it up with stories.

---

## Desperate

### `supplication_15` — *For Someone Sick*
**Chain:** FETCH+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Sick partner needing a {quest_item} to stay steady. Pool surface: 'keep them steady through the next stretch' — works for medicinal, tool, food, cloth?

**expositionText**
> My partner hasn't been able to stand for more than a few minutes since the fever took hold. I need {quest_item} to keep them steady through the next stretch, and I can't leave the house long enough to go looking myself. Everything I own has bent around keeping them alive, and I've got nothing left to bend. Could you bring that back for me.

**acceptText** / **declineText**
> ✓ Thank you. Truly. I'll be sitting with them until you come back, and knowing you're out there doing that is the closest I've felt to rest in weeks.
>
> ✗ Alright. I'll think of something else, or I'll go without sleep another night and figure it out. I don't hold it against you, everyone's got their own to carry.

**expositionTurnInText**
> You brought it. Oh, you brought it. I can settle them properly tonight and finally close my own eyes for an hour. Which means I can tell you the part I couldn't bring myself to say when I was this close to breaking.

**conflict1Text** *(boss reveal)*
> There's more keeping me in that chair than the fever. {boss_name} has been prowling the paths I used to take for supplies, and I stopped going out the day they were first sighted. If you could put an end to them, I could actually leave the house again and do my share. I can't face them on my own in the shape I'm in.

**conflict1TurnInText**
> It's done. I almost don't know how to sit with that. I can walk to the well in the morning and not count my steps for the first time since this started.

**resolutionText**
> My partner asked about you this morning, which is the first thing they've asked about that wasn't water or pain. Take {reward_item}, please. You gave two of us our days back, not just one.

**skillCheck:** INSIGHT · DC 13
> pass: I haven't let myself cry in front of them once. They'd hear it, and then they'd start apologizing for being ill, and I can't take that from them on top of everything else. I do it in the garden when the wind's loud enough to cover it.
>
> fail: We're getting by. That's all anyone can ask right now.

---

### `disaster_15` — *What The Collapse Let Out*
**Chain:** COLLECT+BOSS · **Valence:** negative · **Role affinity:** (any)

**Review focus:** Mine collapse + something came out of the hole. Two-beat disaster: physical collapse then the thing it released. Tests chain-integrity on a layered setup.

**expositionText**
> The old working up the hill caved in on itself last week and took two of our people with it before we got them out. We're patching what equipment survived and we need {gather_count} {quest_item} before we can get back to any proper work. Everyone here has been pulling double since, and a borrowed pair of hands would go a long way toward catching us up. Would you lend a hand.

**acceptText** / **declineText**
> ✓ That's a kindness. I'll pass it along to the crew so they can stop worrying about that part for a night.
>
> ✗ That's fair. We'll find another way, we always do. You didn't cause this and I won't pretend you owe us for it.

**expositionTurnInText**
> That's enough to get the tools squared away and the crew back on their feet. I can breathe past the first layer of this. Which brings me to the layer underneath.

**conflict1Text** *(boss reveal)*
> The collapse buried our people, and it opened something up underneath that none of us knew was there. {boss_name} came up out of that hole the same night, and we've been taking turns watching the approach ever since. None of us here is equipped to face a {group_difficulty} thing like that after what the week has already cost us. If you could end it, we could grieve properly instead of pacing.

**conflict1TurnInText**
> It's done, then. I'll sleep tonight, and so will the rest of the crew. We can finally hold a proper wake for the two we lost without one ear always on the ridge.

**resolutionText**
> We'll rebuild the working when hands have steadied, and the rest will keep. Take {reward_item}, please, it's what we could put together from the stores that survived. You gave us room to mourn.

---

## Mysterious

### `conflict_with_fate_14` — *An Old Habit*
**Chain:** FETCH+BOSS · **Valence:** neutral · **Role affinity:** (any)

**Review focus:** Folk-superstitious NPC keeping old warding habits. {quest_item} framed as a ward or threshold token. HISTORY skillCheck about grandmother's tradition.

**expositionText**
> The nights around {settlement_name} have been off their rhythm for weeks, and I keep catching myself reaching for habits my grandmother taught me. Before I ask anything bigger, I want a {quest_item} to keep near the door. It's a small old thing and probably won't do much, but my hands want it done, and I've learned to listen to my hands.

**acceptText** / **declineText**
> ✓ Good. It eases me that someone else thinks this is worth an afternoon.
>
> ✗ Well, I've been looking after myself for a long time, and I'll keep on. No hard feelings.

**expositionTurnInText**
> Thank you. Having this by the door has quieted something in my chest that I couldn't settle any other way. I feel steadier now, and steady is what I need for the harder thing.

**conflict1Text** *(boss reveal)*
> The travelers through here have been saying a name, and the name is {boss_name}. The old ways tell you to put a thing at your threshold and then go meet the threat at a distance, and that's what I'm asking of you. I won't pretend the warding did the work. I'll ask you to do the rest.

**conflict1TurnInText**
> Then the threshold held and the field did too. I feel my shoulders drop for the first time in a while, and I didn't realize how long they'd been up.

**resolutionText**
> I'll keep the thing by the door a while longer. Old habits are good for seasons like the one we just had, and I don't think we've seen the last of strangeness around here. Take {reward_item} with my thanks, and know an old habit was repaid tonight.

**skillCheck:** HISTORY · DC 13
> pass: The warding I asked for isn't a secret, but nobody young asks about it anymore, so I don't bring it up. My grandmother kept one through a season like this one, and she said afterwards that it mattered less what it was and more that she kept it. I've come around to thinking she was right.
>
> fail: It's just something my family used to do. I'd rather not make more of it than that.

---

### `conflict_with_fate_15` — *New To This*
**Chain:** TALK+BOSS · **Valence:** neutral · **Role affinity:** (any)

**Review focus:** Newcomer NPC consulting an elder. Target NPC is the generational-knowledge voice. Pronoun test plus framing check on 'everyone here acts like the last two months are normal'.

**expositionText**
> I moved to {settlement_name} this spring and everyone here acts like the last two months are normal, and I can't get my footing. Before I do anything about it, I want to hear from someone who's been through a season like this before. Go find {target_npc} over in {target_npc_settlement} and ask them what they remember. I need to know if the people around me are calm because they understand it or because they've just stopped trying.

**acceptText** / **declineText**
> ✓ Thank you. Everyone here kept telling me to let it alone, and you're the first person who didn't.
>
> ✗ Alright. I'll keep asking around. Someone will eventually give me a straight answer.

**expositionTurnInText**
> So it has happened before, and people did get through it. That's more than I had this morning, and it's enough to stand on. I think I'm ready to stop waiting for it to pass on its own.

**conflict1Text** *(boss reveal)*
> {target_npc} told you the name, and now I'll say it back to you. It's {boss_name}, and whatever it actually is, it's the part of this I can point at. The folks here who've seen a bad season before will know what to do about the rest. I'd like to be useful for the piece that can actually be closed.

**conflict1TurnInText**
> Then it's done, and I feel something I wasn't sure I'd feel again, which is settled. The older folks were right that a season like this passes, and I was right that you don't have to only wait it out.

**resolutionText**
> I think I'll stay. This morning I wasn't sure, and tonight I am, and that's mostly because of you. Take {reward_item} with my thanks, and if anyone else turns up here looking as lost as I was, point them my way.

**targetNpcOpener / targetNpcCloser**
> opener: Oh, they sent you to me about this. I've lived long enough to remember the last bad stretch like this one, and I'll tell you what I told them back then. You never quite understand it. You outlast it, and you lean on people who've seen it before.
>
> closer: Tell them the pattern holds. It felt like the end of the world the first time too, and it wasn't.

**skillCheck:** INSIGHT · DC 12
> pass: Honestly, I haven't told anyone here that I almost went back home last week. I packed a bag and sat with it for an hour. I'm still here because I wanted to see how people who belong to a place handle something like this, and I'm trying to be one of them.
>
> fail: I'm fine, I'm just still settling in. Everyone has a rough first season somewhere.

---
