package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyProximityEvictorTest {

    // Position tuples are 3 doubles via a small record for the test.
    record Pos(double x, double y, double z) {}

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    UUID carol = UUID.randomUUID();

    Pos aliceAt = new Pos(0, 0, 0);
    Pos bobNear = new Pos(30, 0, 0);
    Pos bobFar = new Pos(1000, 0, 0);
    Pos origin = aliceAt;

    @Test
    void allInRange_noEvictions() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{30,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty());
    }

    @Test
    void farMemberEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{1000,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }

    @Test
    void offlineMemberEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            Optional.empty();  // bob is offline

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }

    @Test
    void triggeringPlayerNeverEvicted() {
        // Even if the position resolver fails for the triggering player, they stay.
        Function<UUID, Optional<double[]>> positions = u -> Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty());
    }

    @Test
    void exactlyAtRadius_notEvicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{80,0,0}) :  // exactly 80
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertTrue(evicted.isEmpty(), "exactly-at-radius is inclusive");
    }

    @Test
    void justBeyondRadius_evicted() {
        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{80.001,0,0}) :
            Optional.empty();

        Set<UUID> evicted = Nat20PartyProximityEvictor.sweep(
            Set.of(alice, bob), alice, new double[]{0,0,0}, positions, 80.0);

        assertEquals(Set.of(bob), evicted);
    }
}
