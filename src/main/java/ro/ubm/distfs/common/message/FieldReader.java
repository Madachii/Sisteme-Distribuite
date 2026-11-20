package ro.ubm.distfs.common.message;

import java.util.Arrays;
import java.util.UUID;

/**
 * Reads typed fields from a space-separated message body.
 */
final class FieldReader {

    private static final String[] NO_FIELDS = new String[0];

    private final String[] fields;
    private int index;

    FieldReader(MessageType type, String body, int minFields, int maxFields) {
        fields = body.isEmpty() ? NO_FIELDS : body.split(" ");
        if (fields.length < minFields || fields.length > maxFields) {
            throw new IllegalArgumentException(
                    type + " needs " + minFields + ".." + maxFields + " fields, got " + fields.length);
        }
    }

    /**
     * Rejects a body on a message that carries no fields at all.
     */
    static void expectEmpty(MessageType type, String body) {
        if (!body.isEmpty()) {
            throw new IllegalArgumentException(type + " takes no fields, got: " + body);
        }
    }

    String nextString() {
        return fields[index++];
    }

    int nextInt() {
        return Integer.parseInt(nextString());
    }

    long nextLong() {
        return Long.parseLong(nextString());
    }

    UUID nextUUID() {
        return UUID.fromString(nextString());
    }

    String nextOptionalString() {
        return index < fields.length ? fields[index++] : null;
    }

    /**
     * Every field not read yet, joined with single spaces. Used by the messages whose
     * last field is free text, i.e. the rest of the line.
     */
    String nextRest() {
        String rest = String.join(" ", Arrays.asList(fields).subList(index, fields.length));
        index = fields.length;
        return rest;
    }
}
