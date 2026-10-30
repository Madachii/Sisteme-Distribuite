package ro.ubm.distfs.common.message;

import java.util.UUID;

/**
 * Reads typed fields from a space-separated message body.
 */
final class FieldReader {

    private final String[] fields;
    private int index;

    FieldReader(MessageType type, String body, int minFields, int maxFields) {
        fields = body.split(" ");
        if (fields.length < minFields || fields.length > maxFields) {
            String expected = minFields == maxFields
                    ? Integer.toString(minFields)
                    : minFields + " or " + maxFields;
            throw new IllegalArgumentException(
                    type + " needs " + expected + " fields, got " + fields.length);
        }
    }

    String nextString() {
        return fields[index++];
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
}
