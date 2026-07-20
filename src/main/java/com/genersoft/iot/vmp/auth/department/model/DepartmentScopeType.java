package com.genersoft.iot.vmp.auth.department.model;

import java.util.Locale;

public enum DepartmentScopeType {
    ALL,
    CUSTOM,
    DEPT,
    DEPT_AND_CHILD,
    SELF,
    NONE;

    public static DepartmentScopeType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
