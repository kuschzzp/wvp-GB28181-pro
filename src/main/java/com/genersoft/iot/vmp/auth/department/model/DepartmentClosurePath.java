package com.genersoft.iot.vmp.auth.department.model;

public record DepartmentClosurePath(
        long ancestorId,
        long descendantId,
        int depth) {
}
