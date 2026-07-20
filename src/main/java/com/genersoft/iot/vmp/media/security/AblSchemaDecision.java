package com.genersoft.iot.vmp.media.security;

/**
 * Explicit result of mapping an ABL network type to a media schema.
 */
public record AblSchemaDecision(
        MediaSchema schema,
        boolean legacyAuditCompatibility) {

    public static AblSchemaDecision mapped(MediaSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        return new AblSchemaDecision(schema, false);
    }

    public static AblSchemaDecision legacyAudit() {
        return new AblSchemaDecision(null, true);
    }

    public static AblSchemaDecision deny() {
        return new AblSchemaDecision(null, false);
    }

    public boolean mapped() {
        return schema != null;
    }
}
