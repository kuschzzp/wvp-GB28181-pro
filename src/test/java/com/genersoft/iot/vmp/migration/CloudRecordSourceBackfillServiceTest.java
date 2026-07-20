package com.genersoft.iot.vmp.migration;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBinding;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBindingResolver;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudRecordSourceBackfillServiceTest {

    @Mock private CloudRecordServiceMapper mapper;
    @Mock private CloudRecordSourceBindingResolver resolver;

    private CloudRecordSourceBackfillService service;

    @BeforeEach
    void setUp() {
        service = new CloudRecordSourceBackfillService(mapper, resolver);
    }

    @Test
    void uniqueCallIdCandidateIsBoundBeforeLowerPrioritySources() {
        CloudRecordItem legacy = legacyRecord(1);
        CloudRecordSourceBinding binding = new CloudRecordSourceBinding(
                VideoResourceType.CHANNEL, 41, 41);
        when(mapper.findLegacyUnbound(100)).thenReturn(List.of(legacy));
        when(resolver.findByCallIdSession("call-1"))
                .thenReturn(Optional.of(binding));
        when(mapper.findBoundSourcesByCallId("call-1")).thenReturn(List.of());
        when(mapper.bindLegacyRecord(
                1, "CHANNEL", 41, 41, "BOUND_CALL_ID")).thenReturn(1);

        List<CloudRecordSourceBackfillResult> results = service.backfillBatch(100);

        assertEquals(1, results.size());
        assertTrue(results.get(0).bound());
        assertEquals("BOUND_CALL_ID", results.get(0).reasonCode());
        verify(resolver, never()).findByAppStreamSession("rtp", "stream-1");
        verify(resolver, never()).findByUnifiedMapping("rtp", "stream-1");
    }

    @Test
    void identicalCurrentAndHistoricalCandidatesRemainUnique() {
        CloudRecordItem legacy = legacyRecord(4);
        CloudRecordSourceBinding binding = new CloudRecordSourceBinding(
                VideoResourceType.CHANNEL, 41, 41);
        when(mapper.findLegacyUnbound(100)).thenReturn(List.of(legacy));
        when(resolver.findByCallIdSession("call-1"))
                .thenReturn(Optional.of(binding));
        when(mapper.findBoundSourcesByCallId("call-1"))
                .thenReturn(List.of(source(10, VideoResourceType.CHANNEL, 41, 41)));
        when(mapper.bindLegacyRecord(
                4, "CHANNEL", 41, 41, "BOUND_CALL_ID")).thenReturn(1);

        CloudRecordSourceBackfillResult result = service.backfillBatch(100).get(0);

        assertTrue(result.bound());
        assertEquals(1, result.candidateCount());
    }

    @Test
    void multipleHighPriorityCandidatesRemainLegacyUnbound() {
        CloudRecordItem legacy = legacyRecord(2);
        when(mapper.findLegacyUnbound(100)).thenReturn(List.of(legacy));
        when(resolver.findByCallIdSession("call-1"))
                .thenReturn(Optional.empty());
        when(mapper.findBoundSourcesByCallId("call-1"))
                .thenReturn(List.of(
                        source(10, VideoResourceType.CHANNEL, 41, 41),
                        source(11, VideoResourceType.CHANNEL, 42, 42)));
        when(mapper.markLegacyUnbound(2, "AMBIGUOUS_CALL_ID")).thenReturn(1);

        CloudRecordSourceBackfillResult result = service.backfillBatch(100).get(0);

        assertFalse(result.bound());
        assertEquals("AMBIGUOUS_CALL_ID", result.reasonCode());
        assertEquals(2, result.candidateCount());
        verify(mapper, never()).bindLegacyRecord(
                anyInt(), anyString(), anyLong(), anyLong(), anyString());
        verify(resolver, never()).findByAppStreamSession("rtp", "stream-1");
    }

    @Test
    void zeroCandidatesRemainLegacyUnboundWithAReason() {
        CloudRecordItem legacy = legacyRecord(3);
        when(mapper.findLegacyUnbound(100)).thenReturn(List.of(legacy));
        when(resolver.findByCallIdSession("call-1"))
                .thenReturn(Optional.empty());
        when(mapper.findBoundSourcesByCallId("call-1"))
                .thenReturn(List.of());
        when(resolver.findByAppStreamSession("rtp", "stream-1"))
                .thenReturn(Optional.empty());
        when(mapper.findBoundSourcesByAppStream("rtp", "stream-1"))
                .thenReturn(List.of());
        when(resolver.findByUnifiedMapping("rtp", "stream-1"))
                .thenReturn(Optional.empty());
        when(mapper.markLegacyUnbound(3, "NO_CANDIDATE")).thenReturn(1);

        CloudRecordSourceBackfillResult result = service.backfillBatch(100).get(0);

        assertFalse(result.bound());
        assertEquals("NO_CANDIDATE", result.reasonCode());
        assertEquals(0, result.candidateCount());
    }

    private CloudRecordItem legacyRecord(int id) {
        CloudRecordItem item = new CloudRecordItem();
        item.setId(id);
        item.setApp("rtp");
        item.setStream("stream-1");
        item.setCallId("call-1");
        item.setSourceType("LEGACY_UNBOUND");
        return item;
    }

    private CloudRecordSource source(
            long recordId,
            VideoResourceType type,
            long sourceId,
            long channelId) {
        return new CloudRecordSource(recordId, type.name(), sourceId, channelId);
    }
}
