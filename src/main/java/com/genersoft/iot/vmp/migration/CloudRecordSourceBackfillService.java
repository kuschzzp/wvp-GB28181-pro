package com.genersoft.iot.vmp.migration;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBinding;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBindingResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceResolutionException;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class CloudRecordSourceBackfillService {

    private static final int MAX_BATCH_SIZE = 1000;

    private final CloudRecordServiceMapper mapper;
    private final CloudRecordSourceBindingResolver resolver;

    public CloudRecordSourceBackfillService(
            CloudRecordServiceMapper mapper,
            CloudRecordSourceBindingResolver resolver) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.resolver = Objects.requireNonNull(
                resolver, "resolver must not be null");
    }

    @Transactional
    public List<CloudRecordSourceBackfillResult> backfillBatch(int limit) {
        if (limit <= 0 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_BATCH_SIZE);
        }
        List<CloudRecordItem> records = mapper.findLegacyUnbound(limit);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::backfillOne).toList();
    }

    private CloudRecordSourceBackfillResult backfillOne(CloudRecordItem record) {
        try {
            Set<CloudRecordSourceBinding> callIdCandidates =
                    candidatesByCallId(record.getCallId());
            if (callIdCandidates.size() > 1) {
                return markUnbound(
                        record, "AMBIGUOUS_CALL_ID", callIdCandidates.size());
            }
            if (callIdCandidates.size() == 1) {
                return bind(
                        record,
                        callIdCandidates.iterator().next(),
                        "BOUND_CALL_ID",
                        1);
            }

            Set<CloudRecordSourceBinding> appStreamCandidates =
                    candidatesByAppStream(record.getApp(), record.getStream());
            if (appStreamCandidates.size() > 1) {
                return markUnbound(
                        record,
                        "AMBIGUOUS_APP_STREAM",
                        appStreamCandidates.size());
            }
            if (appStreamCandidates.size() == 1) {
                return bind(
                        record,
                        appStreamCandidates.iterator().next(),
                        "BOUND_APP_STREAM",
                        1);
            }

            Optional<CloudRecordSourceBinding> unified =
                    resolver.findByUnifiedMapping(record.getApp(), record.getStream());
            if (unified.isPresent()) {
                return bind(
                        record, unified.get(), "BOUND_UNIFIED_MAPPING", 1);
            }
            return markUnbound(record, "NO_CANDIDATE", 0);
        } catch (CloudRecordSourceResolutionException | IllegalArgumentException e) {
            log.warn(
                    "[旧云录像来源回填失败] recordId: {}, reason: {}",
                    record.getId(), e.getMessage());
            return markUnbound(record, "RESOLUTION_ERROR", 0);
        }
    }

    private Set<CloudRecordSourceBinding> candidatesByCallId(String callId) {
        LinkedHashSet<CloudRecordSourceBinding> candidates = new LinkedHashSet<>();
        if (callId == null || callId.isBlank()) {
            return candidates;
        }
        addOptional(candidates, resolver.findByCallIdSession(callId));
        addHistorical(candidates, mapper.findBoundSourcesByCallId(callId));
        return candidates;
    }

    private Set<CloudRecordSourceBinding> candidatesByAppStream(
            String app,
            String stream) {
        LinkedHashSet<CloudRecordSourceBinding> candidates = new LinkedHashSet<>();
        if (app == null || app.isBlank() || stream == null || stream.isBlank()) {
            return candidates;
        }
        addOptional(candidates, resolver.findByAppStreamSession(app, stream));
        addHistorical(
                candidates, mapper.findBoundSourcesByAppStream(app, stream));
        return candidates;
    }

    private void addOptional(
            Set<CloudRecordSourceBinding> candidates,
            Optional<CloudRecordSourceBinding> candidate) {
        Objects.requireNonNull(candidate, "resolver result must not be null")
                .ifPresent(candidates::add);
    }

    private void addHistorical(
            Set<CloudRecordSourceBinding> candidates,
            List<CloudRecordSource> sources) {
        if (sources == null) {
            return;
        }
        for (CloudRecordSource source : sources) {
            if (source == null
                    || source.sourceType() == null
                    || source.sourceId() == null
                    || source.channelId() == null) {
                continue;
            }
            candidates.add(new CloudRecordSourceBinding(
                    VideoResourceType.valueOf(source.sourceType()),
                    source.sourceId(),
                    source.channelId()));
        }
    }

    private CloudRecordSourceBackfillResult bind(
            CloudRecordItem record,
            CloudRecordSourceBinding binding,
            String reasonCode,
            int candidateCount) {
        int updated = mapper.bindLegacyRecord(
                record.getId(),
                binding.sourceType().name(),
                binding.sourceId(),
                binding.channelId(),
                reasonCode);
        requireSingleUpdate(record.getId(), updated, "绑定");
        log.info(
                "[旧云录像来源回填成功] recordId: {}, sourceType: {}, sourceId: {}, channelId: {}, reason: {}",
                record.getId(), binding.sourceType(), binding.sourceId(),
                binding.channelId(), reasonCode);
        return new CloudRecordSourceBackfillResult(
                record.getId(), true, reasonCode, candidateCount);
    }

    private CloudRecordSourceBackfillResult markUnbound(
            CloudRecordItem record,
            String reasonCode,
            int candidateCount) {
        int updated = mapper.markLegacyUnbound(record.getId(), reasonCode);
        requireSingleUpdate(record.getId(), updated, "标记 LEGACY_UNBOUND");
        log.warn(
                "[旧云录像无法回填] recordId: {}, app: {}, stream: {}, callId: {}, reason: {}, candidateCount: {}",
                record.getId(), record.getApp(), record.getStream(),
                record.getCallId(), reasonCode, candidateCount);
        return new CloudRecordSourceBackfillResult(
                record.getId(), false, reasonCode, candidateCount);
    }

    private void requireSingleUpdate(long recordId, int updated, String action) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "旧云录像" + action + "数量异常: recordId="
                            + recordId + ", updated=" + updated);
        }
    }
}
