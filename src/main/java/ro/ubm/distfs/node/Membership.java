package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Role;
import ro.ubm.distfs.common.message.Heartbeat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table of the nodes this node has heard from, built from heartbeats.
 * A node is DOWN once no heartbeat arrived for {@link #DOWN_AFTER}.
 */
public final class Membership {

    public static final Duration DOWN_AFTER = Duration.ofSeconds(6);

    public enum Health { UP, DOWN }

    public record Member(Heartbeat heartbeat, Instant lastSeen, Health health) {
    }

    private final Map<UUID, Member> members = new ConcurrentHashMap<>();

    public void onHeartbeat(Heartbeat heartbeat) {
        Member previous = members.put(
                heartbeat.node().id(),
                new Member(heartbeat, Instant.now(), Health.UP)
        );

        if (previous == null || previous.health() == Health.DOWN) {
            log(heartbeat, Health.UP);
        }
    }

    /**
     * Marks as DOWN every UP node whose last heartbeat is older than {@link #DOWN_AFTER}.
     */
    public void sweep() {
        Instant deadline = Instant.now().minus(DOWN_AFTER);

        for (Member member : members.values()) {
            if (member.health() == Health.UP && member.lastSeen().isBefore(deadline)) {
                Member down = new Member(member.heartbeat(), member.lastSeen(), Health.DOWN);
                if (members.replace(member.heartbeat().node().id(), member, down)) {
                    log(member.heartbeat(), Health.DOWN);
                }
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

    private static void log(Heartbeat heartbeat, Health health) {
        System.out.printf("[membership] %s %s:%d%s is %s%n",
                heartbeat.node().id(),
                heartbeat.node().host(),
                heartbeat.node().tcpPort(),
                heartbeat.role() == Role.COORDINATOR ? " (coordinator)" : "",
                health);
    }
}
