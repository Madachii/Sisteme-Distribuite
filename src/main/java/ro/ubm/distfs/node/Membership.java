package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Role;
import ro.ubm.distfs.common.message.Heartbeat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table of the nodes this node has heard from, built from heartbeats.
 * A node is ACTIV while its heartbeats arrive, SUSPECT once it has been silent for
 * longer than {@code suspectAfter} and CAZUT once silent for longer than {@code failAfter}.
 * Any new heartbeat brings it back to ACTIV.
 */
public final class Membership {

    public static final Duration DEFAULT_SUSPECT_AFTER = Duration.ofSeconds(4);
    public static final Duration DEFAULT_FAIL_AFTER = Duration.ofSeconds(6);

    public enum Health { ACTIV, SUSPECT, CAZUT }

    public record Member(Heartbeat heartbeat, Instant lastSeen, Health health) {
    }

    private final Duration suspectAfter;
    private final Duration failAfter;
    private final Map<UUID, Member> members = new ConcurrentHashMap<>();

    public Membership(Duration suspectAfter, Duration failAfter) {
        Objects.requireNonNull(suspectAfter, "suspectAfter");
        Objects.requireNonNull(failAfter, "failAfter");

        if (suspectAfter.isNegative() || suspectAfter.isZero()) {
            throw new IllegalArgumentException("suspectAfter must be positive");
        }
        if (failAfter.compareTo(suspectAfter) <= 0) {
            throw new IllegalArgumentException("failAfter must be greater than suspectAfter");
        }

        this.suspectAfter = suspectAfter;
        this.failAfter = failAfter;
    }

    public void onHeartbeat(Heartbeat heartbeat) {
        Instant now = Instant.now();
        Member previous = members.put(
                heartbeat.node().id(),
                new Member(heartbeat, now, Health.ACTIV)
        );

        if (previous == null) {
            log(heartbeat, "ACTIV: nod nou");
        } else if (previous.health() == Health.SUSPECT) {
            log(heartbeat, "ACTIV: a revenit din SUSPECT dupa " + silenceMs(previous, now) + " ms");
        } else if (previous.health() == Health.CAZUT) {
            // A restarted node gets a new id, so the same id coming back means it never stopped.
            // Log messages stay ASCII: the Windows console code page has no ă/ș/ț.
            log(heartbeat, "ACTIV: a revenit din CAZUT dupa " + silenceMs(previous, now)
                    + " ms, fara repornire -> declaratia CAZUT a fost gresita");
        }
    }

    /**
     * Moves every node whose last heartbeat is too old to SUSPECT or CAZUT.
     */
    public void sweep() {
        Instant now = Instant.now();

        for (Member member : members.values()) {
            Duration silence = Duration.between(member.lastSeen(), now);
            Health next = healthAfter(silence);

            if (next.compareTo(member.health()) <= 0) {
                continue; // only ACTIV -> SUSPECT -> CAZUT here; heartbeats bring nodes back
            }

            Member updated = new Member(member.heartbeat(), member.lastSeen(), next);
            if (members.replace(member.heartbeat().node().id(), member, updated)) {
                String reason = next + ": niciun heartbeat de " + silence.toMillis() + " ms";
                log(member.heartbeat(), reason);
            }
        }
    }

    /**
     * Snapshot of all known nodes, oldest node ID first.
     */
    public List<Member> members() {
        return members.values().stream()
                .sorted(Comparator.comparing(member -> member.heartbeat().node().id()))
                .toList();
    }

    private Health healthAfter(Duration silence) {
        if (silence.compareTo(failAfter) > 0) {
            return Health.CAZUT;
        }
        if (silence.compareTo(suspectAfter) > 0) {
            return Health.SUSPECT;
        }
        return Health.ACTIV;
    }

    private static long silenceMs(Member member, Instant now) {
        return Duration.between(member.lastSeen(), now).toMillis();
    }

    private static void log(Heartbeat heartbeat, String event) {
        System.out.printf("[membership] %s %s:%d%s %s%n",
                heartbeat.node().id(),
                heartbeat.node().host(),
                heartbeat.node().tcpPort(),
                heartbeat.role() == Role.COORDINATOR ? " (coordinator)" : "",
                event);
    }
}
