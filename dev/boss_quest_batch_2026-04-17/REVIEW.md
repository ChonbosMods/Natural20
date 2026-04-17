# Phase 2 Batch 1 — 18 Boss Templates for Review

**After two rounds of revision (structural rule sharpening + targeted prose fixes per user notes).**

## Cold Revenge

### `vengeance_11` — *A Silver Debt*
**Chain:** KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> Nine winters ago {boss_name} came through here and took my father for the silver on his belt. I kept the belt on a nail above my workbench, and I have looked at it every morning since. I am not a young woman anymore, and I am done waiting. If you are willing, I want that name crossed off.

**acceptText** / **declineText**
> ✓ Good. I had my answer ready if you said no, and I do not need it now. Go.
>
> ✗ Of course. Everyone has their own dead to carry, and mine is not your burden. Close the door behind you.

**expositionTurnInText**
> So it is done. {boss_name} is gone, and the world does not feel lighter the way I thought it would. Sit down a moment. I want to look at someone who saw it through.

**resolutionText**
> Take the belt off the nail for me, would you. I cannot look at it anymore, and I do not want it thrown out either. Take {reward_item} too. I set it aside for whoever finished this, and I would rather it buy you a good meal than sit here another winter.

**skillCheck:** INSIGHT · DC 13
> pass: The worst part is I heard it from the road, close enough to count the steps it took away from what it had done. I have carried that sound through every quiet night since.
>
> fail: It was a long time ago. The details do not matter anymore, only the ending does.

---

### `vengeance_12` — *What They Took*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> I have been putting off a piece of work for a year because I did not have the means, and I am tired of putting it off. If you are willing to help, I need {gather_count} {quest_item} to start with. Bring those and I will tell you what I am actually asking. I will only get one shot at what comes after, and I want to be ready.

**acceptText** / **declineText**
> ✓ Alright. Do not come back empty-handed and I will not waste your time either. We will talk properly once the {quest_item} is on my table.
>
> ✗ Figures. Polite people always have somewhere better to be. Walk on, then.

**expositionTurnInText**
> That is enough {quest_item}. I have not touched a project like this since before it happened, but my hands remember. Sit down, I owe you the rest of it now.

**conflict1Text** *(boss reveal)*
> {boss_name} made a show of me in front of my own people here in {settlement_name}. Took something of my mother's off my own neck and laughed while I stood there. They keep {enemy_type_plural} close, so you will have to cut through them to get at the real problem. I am not asking you to be quiet about it either.

**conflict1TurnInText**
> It is done. They cannot flaunt what they took from me anymore, and I can close my eyes without hearing them laugh in the dark. That is enough.

**resolutionText**
> I can stand in my own settlement again without flinching, and that is more than I thought I would have. Take {reward_item}, please. I am quieter than I was yesterday, and that is worth more to me than the silver ever was.

**skillCheck:** PERCEPTION · DC 12
> pass: You can see the mark on my collarbone where they tore the chain off. I keep my scarf over it, but I cannot stop touching it. Every time my fingers find it, I remember the sound the clasp made when it broke.
>
> fail: They took something of mine. That is all you need to know.

---

### `remorse_12` — *My Brother's Door*
**Chain:** TALK_TO_NPC + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> Before I can say any of the harder things out loud, I need someone to go see my brother for me. {target_npc} lives in {target_npc_settlement}, and he has not spoken to me in a long time for reasons that are mine to carry. Tell him I am finally trying to set something right. He deserves to hear it from a steady voice, and mine has not been steady in a year.

**acceptText** / **declineText**
> ✓ Thank you. I have been holding onto this for a long time, and I did not know I was allowed to put it down.
>
> ✗ I understand. If I were you, I would not want any part of this either. I am sorry I even asked.

**expositionTurnInText**
> He heard you out. That is more than I had any right to hope for. Sit with me a moment, because what I am about to ask is harder, and I owe you the whole of it now.

**conflict1Text** *(boss reveal)*
> I led {boss_name} to my brother's door. Not on purpose, but I kept the company I kept and I talked when I should not have, and he barely lived through what came after. I want to finish what I started by ending the one I brought down on him. A {target_npc_role} like my brother will never ask me for this, but I owe him the doing.

**conflict1TurnInText**
> {boss_name} is dead. I thought I would feel more than I do, but maybe this is what finishing looks like. My hands are quiet for the first time in a year.

**resolutionText**
> My brother answered my letter this morning. He has not forgiven me and he may never, but he answered, and that is more than I had yesterday. Please take {reward_item}. It is overdue in more ways than one, and I would rather it leave this house in good hands.

**skillCheck:** INSIGHT · DC 14
> pass: I drank with them for weeks, flattered they listened to me. When my brother's name came out of my mouth I watched their faces change, and I did nothing. That is the part I cannot forgive.
>
> fail: I said too much to the wrong person once. That is the short of it.

---

## Hot Grief

### `loss_of_loved_ones_12` — *A Name I Can't Put Down*
**Chain:** FETCH_ITEM + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> My husband didn't come home last spring, and I've spent the year since learning how to eat a meal and sleep through the night again. Before I do anything else, I need the {quest_item} he had on him when he went out. I can't rest knowing it's still out there somewhere. If you can bring that back to me, I can bear whatever comes next.

**acceptText** / **declineText**
> ✓ Thank you. I've been carrying this alone for a long time, and it helps just knowing someone else will touch it.
>
> ✗ Oh, alright. I shouldn't have asked, maybe. It's a lot to lay at a stranger's feet.

**expositionTurnInText**
> That's it. I can feel where he wore it soft, and that alone undoes me a little. I'll hold onto it while I tell you the rest.

**conflict1Text** *(boss reveal)*
> Holding this made it real again, and I may as well say what I've been holding back. A traveler gave me the name of the thing that took him. {boss_name}. I say it in the dark sometimes just to make sure I still can, and I need the name to stop living in my head. Put {boss_name} down for me, please. I know it's a {group_difficulty} thing and I wouldn't ask if I could sleep any other way.

**conflict1TurnInText**
> You did it. You really did. I thought hearing it would feel like something bigger, but mostly it just feels quiet, and that's what I wanted.

**resolutionText**
> Take {reward_item} with my thanks. It was his, and he'd want it used by someone who finished what he couldn't. Tonight I think I'll sleep, and tomorrow I'll start learning what the rest of my life is going to look like.

**skillCheck:** INSIGHT · DC 13
> pass: The hardest part isn't missing him. It's the anger. I didn't know I had this much of it in me, and I'm tired of carrying it around the house like a second body.
>
> fail: It's been a long year. That's the best I can say about it right now.

---

### `loss_of_loved_ones_13` — *For My Sister*
**Chain:** TALK_TO_NPC + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> My sister was the steady one. She ran with me since we were children, and she was gone before I understood what had happened. Before I move on anything else, I'd like you to go see {target_npc} over in {target_npc_settlement}. She was my sister's closest friend, and she deserves to hear what I'm about to do from a real person before I set any of it in motion.

**acceptText** / **declineText**
> ✓ I appreciate that. She was soft with my sister in a way I never quite was, and I don't want to do this without her knowing.
>
> ✗ Fair enough. It's a strange errand to hand someone. I'll find another way to say what needs saying.

**expositionTurnInText**
> You spoke with her. Good. She always took longer than the rest of us to say what she meant, but when she said it, she meant it. I feel steadier knowing she's been told.

**conflict1Text** *(boss reveal)*
> Now the part I couldn't send in a message. The thing that took her goes by {boss_name}, and I've known that name for months without being able to do anything with it. My sister would have told me that hate is a poor reason for anything, but she isn't here to tell me. Go end it, and come back when {boss_name} is no longer a name anyone has to fear.

**conflict1TurnInText**
> Quiet, isn't it. I thought I'd have more to say when this day came. Mostly I just want to sit for a while and think about her without the other thing crowding in.

**resolutionText**
> {reward_item}, please. She would have wanted it with someone who earned it, and you did. I can talk about her now without every sentence ending at the same dark place, and that's more than I expected to have.

**targetNpcOpener / targetNpcCloser**
> opener: They told you why, didn't they. She and I were like siblings ourselves by the end, you know. I'm glad they're finally moving on what's been eating them, though I worry about what they'll be like on the other side of whatever they're planning.
>
> closer: Tell them I said go on, then. And tell them to come see me after. They shouldn't be alone when the quiet settles in.

**skillCheck:** HISTORY · DC 12
> pass: We grew up in a house with thin walls and a stubborn mother, and my sister was the one who kept me from breaking things I couldn't fix. Half the good choices I ever made were really hers. I don't know who I am yet without her pulling me back from the edge.
>
> fail: She was my sister. There isn't a short version of what that meant.

---

### `loss_of_loved_ones_14` — *The Old Grave*
**Chain:** KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> This is going to sound strange. My brother died nine years ago, and until last month I didn't know what killed him. A traveler came through {settlement_name} and said the name without knowing what it meant to me. {boss_name}. I sat with it for a week before I could even say it out loud. I'm too old to chase anything down myself, but if you'd end {boss_name} for me, I think I could finally put him properly to rest.

**acceptText** / **declineText**
> ✓ That's kind of you. I thought I'd made my peace with the not-knowing, and then the knowing came and undid all of it at once.
>
> ✗ No, no, it's fine. You've got your own life, and I've had nine years already. Another few won't matter.

**expositionTurnInText**
> It's done, then. {boss_name}, finally. I keep waiting to feel the thing I thought I'd feel, and instead I just feel tired in a way that isn't bad. Like I've been holding my breath for nine years and someone's told me I can stop.

**resolutionText**
> Please take {reward_item}. It belonged to my brother, and I've had no reason to part with it until today. I'll visit his stone this evening and tell him the news, and I think for the first time since he went I'll have something to say that isn't an apology.

**skillCheck:** INSIGHT · DC 14
> pass: I wasn't with him, you see. I was supposed to be, and I wasn't, and for nine years I told myself it wouldn't have mattered. Learning the name has made that lie harder to tell. This is the closest I'll ever come to being there for him.
>
> fail: It's an old wound. I'd rather not scratch at it any more than I have to.

---

## Protective

### `deliverance_12` — *My Son, Please*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> My boy is hurt and he's asleep in the back room, and I need him settled before anything else happens. Before I do another thing I need {gather_count} {quest_item} so I can take proper care of him through the night. My leg won't carry me more than the length of the house anymore, or I'd be out there gathering it myself. Please, I want him to wake up to a better morning than the one he went to sleep on.

**acceptText** / **declineText**
> ✓ Thank the stars. Hurry, please, every hour he's lying like that is an hour I can't breathe.
>
> ✗ Please, he's just a boy. If nobody helps me settle him then I don't know what I'll do, I really don't.

**expositionTurnInText**
> You brought it. Good. Set it there and come close, because the harder part is the reason he's in that bed at all.

**conflict1Text** *(boss reveal)*
> {boss_name} is what dragged him off and what sent him back half-dead. It keeps to the {group_difficulty} kind of company, but {boss_name} is the one that matters. End it, so he can wake up to a world that isn't waiting for it.

**conflict1TurnInText**
> He opened his eyes an hour ago. He's breathing easy and he's asleep again now, in the bed you helped me get ready. I don't have words for this yet, give me a moment.

**resolutionText**
> Take {reward_item} with my thanks. It was meant for him one day but he'd want you to have it, and so do I. Come and see him when he's up, he'll want to thank the one who finished {boss_name}.

**skillCheck:** INSIGHT · DC 12
> pass: I haven't told anyone, but I heard him crying the first night. On the wind. I've been sitting by the door since, in case he got close enough to call for me.
>
> fail: I'm holding up. Please, just go, there isn't time for sitting and talking.

---

### `deliverance_13` — *The Girl I Took In*
**Chain:** FETCH_ITEM + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> There's a girl staying with me, a traveler who came through on her way somewhere and hasn't had the strength to move on since. She left a {quest_item} with a trader when she passed through {settlement_name}, and she's been asking after it in her sleep. If I can put it in her hand she'll rest easier than she has in weeks. I need you to get it back for her before anything else, because that's the part she'd ask for herself if she had the voice for it.

**acceptText** / **declineText**
> ✓ You're a kinder soul than most. She doesn't know I'm asking, so keep it quiet when you come back.
>
> ✗ Oh. Oh, I see. Well, if nobody helps then she fades a little more, and I sit here watching it happen.

**expositionTurnInText**
> You found it. She went quiet when I set it beside her and then slept for the first real stretch I've seen in weeks. Now there's a harder part, and I couldn't say it with her listening earlier.

**conflict1Text** *(boss reveal)*
> {boss_name} is what's been starving her out. It's been circling the edges of {settlement_name} for weeks, eating her stores before she could reach them, cutting her off from the road she meant to take. A {group_difficulty} thing, from what the traders say, but a thing that dies like any other. End it and she eats again, she sleeps again, she walks out of here on her own feet.

**conflict1TurnInText**
> She's at the window. I told her what you did and she wanted to come find you herself, but her legs still won't carry her. She's smiling though. First time in weeks.

**resolutionText**
> I've nothing grand, but please take {reward_item}. She says she'll remember your name for the rest of her days, and I'll make sure she does.

**skillCheck:** PERSUASION · DC 13
> pass: She's not really a stranger, if I'm honest. She's my cousin's daughter and my cousin is gone, and I'm the only one she had left to find. I couldn't say it in front of her, she hates being a burden.
>
> fail: She's just someone who needed a door to knock on. That's all I'll say about it.

---

### `self_sacrifice_for_kindred_12` — *Finally Some Rest*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> I've been preparing for something around the house for months now, and I'm close to finished. I need {gather_count} {quest_item} to round out the last of the work, and my hands are shaking too much lately to go gather it myself. My wife thinks I'm out checking snares. She doesn't need to know different, not yet. Just help me finish what I started, friend, and I'll tell you the rest after.

**acceptText** / **declineText**
> ✓ You have no idea what this means. I might actually sleep tonight, just knowing someone else is carrying a piece of it.
>
> ✗ I understand. I'll keep holding. I always do. I just hoped, for a moment, that I wouldn't have to.

**expositionTurnInText**
> That's everything I needed. Sit down. I owe you the truth about what all this was for, and why I couldn't finish it alone.

**conflict1Text** *(boss reveal)*
> {boss_name} has been coming for my family since the leaves turned last year. Every time, I meet it at the door with whatever I can hold, and every time, it leaves and comes back a week later. It's only a {group_difficulty} thing, but it's learned us, it knows when I'm tired and which window my daughter sleeps by. End it, so I can put the axe down for good.

**conflict1TurnInText**
> You did it. You really did it. I went inside and looked at them sleeping, all of them, and I just stood there. I haven't stood still like that in a year.

**resolutionText**
> Here, take {reward_item}. I'm going to sleep tonight next to my wife for the first time in too long, and that's because of you, friend.

**skillCheck:** PERCEPTION · DC 12
> pass: You see these, don't you. Four scars on my forearm, one for each time something got past me. The last one was close to my daughter's room. I haven't told anyone in the house. I never will.
>
> fail: I'm tired. That's all. Just tired.

---

## Proactive

### `pursuit_12` — *Time To End It*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** neutral · **Role affinity:** Guard

**expositionText**
> I've been cataloguing the raids on {settlement_name} for months, and I'm done sitting with a notebook. I want to go at this properly, and that means supplies first. Bring me {gather_count} {quest_item} so I'm not walking out there short on anything that matters.

**acceptText** / **declineText**
> ✓ Good. I was hoping someone would have the sense to say yes. Let's get to work.
>
> ✗ Fine. Your call. I'll ask the next capable hand that walks through {settlement_name}, and the pattern keeps getting worse in the meantime.

**expositionTurnInText**
> That's a solid haul. We go in prepared or we don't go in at all, and now we're prepared. I can tell you the rest now.

**conflict1Text** *(boss reveal)*
> The name at the center of every raid is {boss_name}. I've watched their {enemy_type_plural} bleed us dry long enough, and I want the source gone. One clean kill and the pattern breaks.

**conflict1TurnInText**
> Dead. Really dead. I kept waiting for bad news to come back with you and it didn't.

**resolutionText**
> That's settled. Take {reward_item}, you earned it twice over. I can finally stop cataloging raids in my head at night. Good.

**skillCheck:** INVESTIGATION · DC 13
> pass: Since you're asking, I kept a tally. Three livestock runs, two supply trains, one clean patrol gone quiet. Every strike comes after a quiet week, so we've got maybe six days before the next one.
>
> fail: Let's just say I've seen enough to know it's time. That's all you need from me.

---

### `pursuit_13` — *My Father's Work*
**Chain:** TALK_TO_NPC + KILL_BOSS · **Valence:** neutral · **Role affinity:** Guard

**expositionText**
> My father spent the last years of his life on this, and I took up his post in {settlement_name} knowing his work wasn't finished. Before I say another word, I need you to go speak with {target_npc} over in {target_npc_settlement}. They were on this trail before I was old enough to hold a blade, and I want to hear my father's name come out of a {target_npc_role}'s mouth one more time.

**acceptText** / **declineText**
> ✓ Thank you for taking this seriously. I've carried this long enough.
>
> ✗ Alright. I'll find someone else, or I'll go myself when the season turns. One way or another it ends.

**expositionTurnInText**
> So you found {target_npc}. Good. I wanted you to hear it from someone who's been on this trail longer than I've been alive, not just from me. Now you know what I know.

**conflict1Text** *(boss reveal)*
> Finish it. Find {boss_name} and put an end to this. I've been handed every scrap of my father's work and a {group_difficulty} threat's worth of warnings, and I want to be the one who finally closes the book.

**conflict1TurnInText**
> You actually did it. I sat down when you told me. I haven't sat down in weeks.

**resolutionText**
> My father would have liked you. Here, take {reward_item}, it belongs with whoever finished this. The name stops with us, and that's enough.

**skillCheck:** HISTORY · DC 12
> pass: You know the old stories about this one, don't you. Then you understand why every {target_npc_role} in the region has a file on them. My father's was the thickest.
>
> fail: The details don't matter much. What matters is that it ends.

---

### `pursuit_14` — *A Quiet Decision*
**Chain:** KILL_BOSS · **Valence:** positive · **Role affinity:** (any)

**expositionText**
> We talked it over this week, all of us in {settlement_name}, and we agreed. {boss_name} has been a shadow over this place for too long. I'm the one asking because I drew the short straw, but every voice was the same. It's time.

**acceptText** / **declineText**
> ✓ I'm grateful. I'll tell the others tonight that it's in motion.
>
> ✗ I understand. We'll keep asking around. The decision's made, whether it's you or the next traveler through.

**expositionTurnInText**
> So it's done. I wasn't sure I'd live to say those words in that order. Thank you for going where the rest of us couldn't.

**resolutionText**
> Take {reward_item}. It belongs with whoever finally closed this for us. The children can play past the walls again, and that's what I'll remember.

**skillCheck:** INSIGHT · DC 13
> pass: You can see it, can't you. I'm tired, and so is everyone else here. Anger burns out, and what we have left is the quiet kind of resolve that finally moved us.
>
> fail: We've simply made up our minds. That's all there is to it.

---

## Desperate

### `supplication_12` — *No One Left To Send*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> I've been barely holding things together here for weeks and I can't keep patching it with spit and prayers. I need a hand pulling {gather_count} {quest_item} so I can shore up what I've got before anything else goes wrong. Everyone I'd normally ask is stretched as thin as I am, and I've run out of ways to stall. Will you help me get that far.

**acceptText** / **declineText**
> ✓ Oh. Alright. I wasn't sure you'd say yes, and I'd already started thinking about what I'd do when you didn't.
>
> ✗ I see. I'll try to figure something out. I don't blame you for walking away, there's only so much one person can take on.

**expositionTurnInText**
> That's a real help, more than you know. I can keep the roof on and the stores full for a while now. Which means I can finally say the part I've been dreading to say out loud.

**conflict1Text** *(boss reveal)*
> There's a reason I've been running out of people to ask. {boss_name} has been picking us apart since the frost broke, and I can't face them, but you might. They've made a {group_difficulty} game out of us. Please, end it before they come for what I've just put back together.

**conflict1TurnInText**
> It's done. It's actually done. I keep waiting to hear them outside, and the quiet is the strangest part.

**resolutionText**
> I never thought I'd see the end of this. Take {reward_item} with my thanks, and know you gave me my life back in pieces I can finally use. I owe you more than I can say.

**skillCheck:** INSIGHT · DC 13
> pass: I haven't slept through a night since things started falling apart. Every creak in the beams and I'm up with a knife I barely know how to hold. I'm ashamed of how relieved I am that someone else is going to handle this.
>
> fail: It's been rough. That's really all I want to say about it.

---

### `supplication_13` — *Afraid To Go Out*
**Chain:** KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> My daughter hasn't been past the gate in a month. It's {boss_name}, that's the name the other parents whisper, and they've made the fields too dangerous for any of our children to walk. We're running thin on what we can grow inside the walls, and she's started asking why I look so tired. I can't send her out and I can't go myself, so I'm asking you.

**acceptText** / **declineText**
> ✓ Thank you. I've been holding that ask in my throat for days, afraid of how it would sound out loud.
>
> ✗ Alright. I don't blame you, they've got a reputation for a reason. We'll manage a little longer somehow.

**expositionTurnInText**
> You did it. You really did it. I haven't told her yet because I want to watch her face when she realizes she can run outside again.

**resolutionText**
> I watched her chase a bird out past the fence this morning and she didn't look back once. {reward_item}, please. You gave her back the rest of her childhood.

**skillCheck:** PERCEPTION · DC 12
> pass: You're looking at my hands. Yes, they shake like that most mornings now. And you've seen how thin she's gotten, haven't you. I've been putting half my plate on hers and she still asks if I'm feeling well.
>
> fail: We're managing. Everyone around here is.

---

### `disaster_12` — *What The Raid Left*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** neutral · **Role affinity:** (any)

**expositionText**
> The raid passed through {settlement_name} two weeks ago and we're still picking through what's left. We're rebuilding as fast as we can, but I need {gather_count} {quest_item} before the rains to patch the worst of it. Everyone here is already doing the work of two, so any hand we can borrow counts. Will you help us get that far, at least.

**acceptText** / **declineText**
> ✓ Good. That's good. I'll tell the others we've got someone willing to lift with us.
>
> ✗ That's fair. We'll find another way, same as we always do. You didn't make this mess and I won't pretend you owe us for it.

**expositionTurnInText**
> That's enough to patch the worst of it before the weather turns. We can breathe a little now, and that means I can finally talk about the part nobody around here wants to say out loud.

**conflict1Text** *(boss reveal)*
> The ones who hit us didn't all leave. {boss_name} is still out there, the one who led them in, and every night we go to sleep wondering if they're coming back to finish what they started. None of us can face a {group_difficulty} threat like that, not after what we've been through. If you could end that for us, we could actually start calling this recovery.

**conflict1TurnInText**
> It's over, then. You don't know what it means to hear that. I'll pass the word around tonight and watch people's shoulders come down for the first time in weeks.

**resolutionText**
> We can rebuild properly now, without one eye always on the treeline. {reward_item}, please. You gave {settlement_name} its evenings back.

**skillCheck:** HISTORY · DC 13
> pass: You've seen this before, haven't you. That look on your face, you know how these things usually end when the leader walks away alive. That's why I'm asking instead of waiting it out, I know what comes next if we let it.
>
> fail: It's been a hard season. We're getting through it one day at a time.

---

## Mysterious

### `conflict_with_fate_11` — *A Name At Last*
**Chain:** FETCH_ITEM + KILL_BOSS · **Valence:** negative · **Role affinity:** (any)

**expositionText**
> For about a month now things haven't been right around {settlement_name}, and I couldn't put a name to any of it. Folks have been asking around for a {quest_item}, something to hold onto while the rest of this sorts itself out, and I've been asking too. If you turn one up, bring it to me. I'd rather hold something than hold nothing right now.

**acceptText** / **declineText**
> ✓ Thank you. I wasn't sure anybody would want to hear this one, let alone answer it.
>
> ✗ I suppose I expected that. Well. Nothing for it, then.

**expositionTurnInText**
> You found it. I didn't really think you would, and I'm grateful. I'll keep it close, for whatever good that does. It's a small thing, but a small thing is more than I had yesterday.

**conflict1Text** *(boss reveal)*
> Now the harder part, and the part I was putting off. The quiet has a name now, and the name is {boss_name}. I don't care what it is, I care that it stops. Whatever you have to do.

**conflict1TurnInText**
> You did it. You actually did it. I keep waiting to feel more afraid than I do, and the feeling isn't coming.

**resolutionText**
> I don't think I'll ever understand what that was, and I've stopped trying. Take {reward_item} with my thanks. You gave us a quiet night, and that's more than I thought any of us would get.

**skillCheck:** INSIGHT · DC 12
> pass: I haven't slept in my own bed in weeks. I can't tell you why. I just know I can't be alone with a thought after dark, so I sit up with whoever else is awake and I wait for morning.
>
> fail: It's been a strange month. That's all I've got for it.

---

### `conflict_with_fate_12` — *What Travelers Say*
**Chain:** COLLECT_RESOURCES + KILL_BOSS · **Valence:** neutral · **Role affinity:** (any)

**expositionText**
> It's been a bad season around {settlement_name}, and the travelers coming through from {other_settlement} speak carefully, like nobody quite wants to say what they mean. I'm old enough not to chase what I can't fix. Before I ask anything harder of you, I need {gather_count} {quest_item} to keep things running in this house while the rest of it plays out. Having my own work in order is the only honest ground I've got to stand on.

**acceptText** / **declineText**
> ✓ Good of you. I've lived long enough to know nothing is certain, but I'll take a willing pair of hands over certainty any day.
>
> ✗ Fair enough. I'll manage the way I've managed everything else, slowly and with worse knees than I'd like.

**expositionTurnInText**
> That'll see us through. I know it seems small next to the rest, but it's settled something in me to have the house in order again. Now I have to ask you for the thing I've been putting off asking.

**conflict1Text** *(boss reveal)*
> Travelers from {other_settlement} have been saying a name for weeks, and it's {boss_name}. That's the piece of this I can point at, and that makes it the piece that can be ended. I don't know if this fixes what's wrong out there, and I won't lie and say it does. But a named thing can die, and I want this one to.

**conflict1TurnInText**
> Then that's that. I said a named thing can die, and I meant it, but hearing it's done feels different than saying it would feel.

**resolutionText**
> I'm not going to pretend I understand any of what the last season has been. I just know it's quieter tonight than it has been. Take {reward_item} with my thanks, and know that an old woman slept easier because of you.

**skillCheck:** HISTORY · DC 13
> pass: My grandmother used to talk about a bad year like this one, long before my time. She never named what caused it either. She just said you get through by naming what you can and feeding the people you've got.
>
> fail: It's an old pattern. That's all I'll say.

---

### `conflict_with_fate_13` — *Just A Beast*
**Chain:** KILL_BOSS · **Valence:** neutral · **Role affinity:** (any)

**expositionText**
> People around {settlement_name} have been whispering the name {boss_name} like it means something, and I'm tired of it. It has a name, and a name means it has a body, and a body can be put in the ground. I'd like that to be done soon, and I'd like the one doing it to be you.

**acceptText** / **declineText**
> ✓ Thank you. A straight answer is the first sensible thing I've heard about this all week.
>
> ✗ Well. The name's out there whether you go or not. Something will sort it eventually.

**expositionTurnInText**
> Then it's done. {boss_name} is a name people won't have to keep saying, and that alone is worth something. I told you it would end like anything else ends, and I was right.

**resolutionText**
> I don't want to hear any more stories about it. It was a {group_difficulty} thing and it's finished, and that's the whole of what I need to know. Take {reward_item} and let's all go back to talking about the weather like normal people.

**skillCheck:** INSIGHT · DC 12
> pass: Fine, I'll grant you the pattern isn't ordinary. Whatever it was, it didn't behave the way things around here usually do. But a strange thing is still a thing, and I'd rather act like I know what I'm doing than stand around wondering.
>
> fail: It had a name. That's the beginning and end of it.

---
