package com.genersoft.iot.vmp.authorization.video.impl;

import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.video.VideoResourceMigrationIssueRecorder;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class DefaultVideoResourceMigrationIssueRecorder
        implements VideoResourceMigrationIssueRecorder {

    private static final String PERMISSION_CODE =
            "internal:video-resource:migrate";

    private final AuthzAuditMapper auditMapper;

    public DefaultVideoResourceMigrationIssueRecorder(
            AuthzAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            VideoResourceRef source,
            Collection<VideoResourceRef> candidates,
            String stableSource,
            Long operatorId,
            String reasonCode) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        String normalizedReason = requireReasonCode(reasonCode);
        String normalizedStableSource = normalizeStableSource(stableSource);
        List<String> candidateRefs = candidates.stream()
                .filter(Objects::nonNull)
                .map(candidate -> candidate.type() + ":" + candidate.id())
                .sorted()
                .toList();

        log.error(
                "[视频资源授权迁移待人工处理] source={}:{}, stableSource={}, candidates={}, reason={}",
                source.type(),
                source.id(),
                stableSource,
                candidateRefs,
                normalizedReason);
        AuthzAuditRecord record = new AuthzAuditRecord(
                PrincipalType.INTERNAL,
                operatorId == null ? 0L : operatorId,
                null,
                PERMISSION_CODE,
                source.type().name(),
                source.id(),
                false,
                normalizedReason,
                normalizedStableSource,
                null,
                LocalDateTime.now());
        if (auditMapper.insert(record) != 1) {
            throw new IllegalStateException("视频资源授权迁移人工处理项记录失败");
        }
    }

    private String normalizeStableSource(String stableSource) {
        String normalized = Objects.requireNonNullElse(stableSource, "UNKNOWN")
                .trim();
        if (normalized.isEmpty()) {
            normalized = "UNKNOWN";
        }
        return normalized.length() <= 64
                ? normalized
                : normalized.substring(0, 64);
    }

    private String requireReasonCode(String reasonCode) {
        if (reasonCode == null
                || reasonCode.isBlank()
                || reasonCode.length() > 64) {
            throw new IllegalArgumentException("迁移人工处理原因编码非法");
        }
        return reasonCode.trim();
    }
}
