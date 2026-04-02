package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DispositionBracket;

import java.util.*;

public class PostureResolver {

    private static final double DISPOSITION_BOOST = 1.5;
    private static final double RECENCY_ONE_PENALTY = 0.3;
    private static final double RECENCY_BOTH_PENALTY = 0.1;

    private static final Set<String> WARM_GROUPS = Set.of("attentive", "cheerful");
    private static final Set<String> COLD_GROUPS = Set.of("skeptical", "dismissive");

    private final PostureGroupRegistry registry;

    public PostureResolver(PostureGroupRegistry registry) {
        this.registry = registry;
    }

    public PostureSelection resolve(String npcValence, int disposition,
                                     List<String> recentPostures, int maxPrompts) {
        Random random = new Random();

        // Step 1: Filter by valence affinity
        List<PostureGroup> eligible = new ArrayList<>();
        for (PostureGroup group : registry.getAllGroups().values()) {
            if (group.valenceAffinity().contains(npcValence)) {
                eligible.add(group);
            }
        }
        if (eligible.isEmpty()) {
            eligible.addAll(registry.getAllGroups().values());
        }

        // Step 2: Single prompt
        if (maxPrompts <= 1 || eligible.size() < 2) {
            PostureGroup picked = eligible.get(random.nextInt(eligible.size()));
            String text = picked.prompts().get(random.nextInt(picked.prompts().size()));
            return new PostureSelection(List.of(
                new PostureSelection.ResolvedPrompt(picked.name(), text, picked.dispositionModifier())
            ));
        }

        // Step 3: Enumerate valid pairs
        List<PostureGroup[]> validPairs = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                if (canPair(eligible.get(i), eligible.get(j))) {
                    validPairs.add(new PostureGroup[]{eligible.get(i), eligible.get(j)});
                }
            }
        }

        if (validPairs.isEmpty()) {
            Collections.shuffle(eligible, random);
            PostureGroup a = eligible.get(0);
            PostureGroup b = eligible.get(1);
            return buildSelection(a, b, random);
        }

        // Step 4: Weight pairs
        DispositionBracket bracket = DispositionBracket.fromDisposition(disposition);
        Set<String> recentSet = new HashSet<>(recentPostures);

        double[] weights = new double[validPairs.size()];
        for (int i = 0; i < validPairs.size(); i++) {
            PostureGroup a = validPairs.get(i)[0];
            PostureGroup b = validPairs.get(i)[1];
            double weight = 1.0;

            // Disposition bracket influence
            if (isHighDisposition(bracket)) {
                if (WARM_GROUPS.contains(a.name()) || WARM_GROUPS.contains(b.name())) {
                    weight *= DISPOSITION_BOOST;
                }
            } else if (isLowDisposition(bracket)) {
                if (COLD_GROUPS.contains(a.name()) || COLD_GROUPS.contains(b.name())) {
                    weight *= DISPOSITION_BOOST;
                }
            }

            // Recency penalty
            boolean aRecent = recentSet.contains(a.name());
            boolean bRecent = recentSet.contains(b.name());
            if (aRecent && bRecent) {
                weight *= RECENCY_BOTH_PENALTY;
            } else if (aRecent || bRecent) {
                weight *= RECENCY_ONE_PENALTY;
            }

            weights[i] = weight;
        }

        // Step 5: Weighted random selection
        int selected = weightedRandom(weights, random);
        PostureGroup a = validPairs.get(selected)[0];
        PostureGroup b = validPairs.get(selected)[1];
        return buildSelection(a, b, random);
    }

    static boolean canPair(PostureGroup a, PostureGroup b) {
        return Math.abs(a.warmth() - b.warmth()) >= 2
            || Math.abs(a.trust() - b.trust()) >= 2;
    }

    private static PostureSelection buildSelection(PostureGroup a, PostureGroup b, Random random) {
        String textA = a.prompts().get(random.nextInt(a.prompts().size()));
        String textB = b.prompts().get(random.nextInt(b.prompts().size()));
        return new PostureSelection(List.of(
            new PostureSelection.ResolvedPrompt(a.name(), textA, a.dispositionModifier()),
            new PostureSelection.ResolvedPrompt(b.name(), textB, b.dispositionModifier())
        ));
    }

    private static boolean isHighDisposition(DispositionBracket bracket) {
        return bracket == DispositionBracket.FRIENDLY
            || bracket == DispositionBracket.TRUSTED
            || bracket == DispositionBracket.LOYAL;
    }

    private static boolean isLowDisposition(DispositionBracket bracket) {
        return bracket == DispositionBracket.HOSTILE
            || bracket == DispositionBracket.SCORNFUL
            || bracket == DispositionBracket.UNFRIENDLY;
    }

    private static int weightedRandom(double[] weights, Random random) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return i;
        }
        return weights.length - 1;
    }
}
