package com.chonbosmods.npc;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class Nat20NameGenerator {

    private static final List<String> NAMES = List.of(
            "Roderick", "Elara", "Theron", "Isolde", "Garrick",
            "Seraphina", "Aldric", "Brenna", "Caelum", "Dahlia",
            "Emeric", "Fiora", "Godwin", "Helena", "Idris",
            "Jorin", "Kestrel", "Liora", "Magnus", "Nerys",
            "Osmund", "Petra", "Quillan", "Rowan", "Sable",
            "Tormund", "Ursa", "Vesper", "Wren", "Xander",
            "Yvaine", "Zephyr", "Anselm", "Blythe", "Cedric",
            "Darian", "Elspeth", "Fenwick", "Gwendolyn", "Hadrian",
            "Ingrid", "Jasper", "Katarin", "Leander", "Mirabel",
            "Norwood", "Opal", "Percival", "Rosmund", "Silas",
            "Tamsin", "Ulric", "Viola", "Warrick", "Ysolde",
            "Alaric", "Bryony", "Corwin", "Desmond", "Elowen",
            "Finnian", "Griselda", "Hector", "Ilene", "Jareth",
            "Kerensa", "Lysander", "Maren", "Niall", "Ondine",
            "Phelan", "Rhiannon", "Sterling", "Talbot", "Una",
            "Vaughn", "Winifred", "Yorick", "Zara", "Ambrose",
            "Callista", "Dorian", "Evander", "Freya", "Galen"
    );

    private Nat20NameGenerator() {
    }

    public static String generate(long seed) {
        Random rng = new Random(seed);
        return NAMES.get(rng.nextInt(NAMES.size()));
    }

    public static String generate() {
        return NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size()));
    }
}
