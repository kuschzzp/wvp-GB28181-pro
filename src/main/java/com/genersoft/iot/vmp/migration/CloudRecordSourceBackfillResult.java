package com.genersoft.iot.vmp.migration;

public record CloudRecordSourceBackfillResult(
        long recordId,
        boolean bound,
        String reasonCode,
        int candidateCount) {
}
