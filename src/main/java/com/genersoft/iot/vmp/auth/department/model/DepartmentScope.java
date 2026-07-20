package com.genersoft.iot.vmp.auth.department.model;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record DepartmentScope(
        boolean all,
        Set<Long> deptIds,
        boolean includeSelf,
        long userId) {

    public DepartmentScope {
        if (all) {
            deptIds = Set.of();
            includeSelf = false;
        } else if (deptIds == null || deptIds.isEmpty()) {
            deptIds = Set.of();
        } else {
            deptIds = deptIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public static DepartmentScope all(long userId) {
        return new DepartmentScope(true, Set.of(), false, userId);
    }

    public static DepartmentScope none(long userId) {
        return new DepartmentScope(false, Set.of(), false, userId);
    }

    public static DepartmentScope self(long userId) {
        return new DepartmentScope(false, Set.of(), true, userId);
    }

    public boolean isNone() {
        return !all && deptIds.isEmpty() && !includeSelf;
    }
}
