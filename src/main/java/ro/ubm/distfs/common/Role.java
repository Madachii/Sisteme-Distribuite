package ro.ubm.distfs.common;

/**
 * The part a node plays in the cluster, as announced in its heartbeat.
 * The constant names are the wire tokens (docs/cerinte.md).
 */
public enum Role {
    NODE,
    COORDINATOR;

    public static Role from(String token) {
        for (Role role : values()) {
            if (role.name().equals(token)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + token);
    }
}
