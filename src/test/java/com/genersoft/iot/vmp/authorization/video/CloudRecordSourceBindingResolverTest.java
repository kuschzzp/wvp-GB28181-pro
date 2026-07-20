package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBinding;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceResolutionException;
import com.genersoft.iot.vmp.authorization.video.resolver.DefaultCloudRecordSourceBindingResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceLookupMapper;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.vmp.gb28181.session.SipInviteSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudRecordSourceBindingResolverTest {

    @Mock private SipInviteSessionManager sessionManager;
    @Mock private ResourceResolverRegistry resourceResolverRegistry;
    @Mock private VideoResourceLookupMapper lookupMapper;

    private DefaultCloudRecordSourceBindingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultCloudRecordSourceBindingResolver(
                sessionManager, resourceResolverRegistry, lookupMapper);
    }

    @Test
    void callIdSessionTakesPriorityAndBindsTheUnifiedChannel() {
        when(sessionManager.getSsrcTransactionByCallId("call-1"))
                .thenReturn(transaction(41));
        when(lookupMapper.findChannelId(41)).thenReturn(41L);

        CloudRecordSourceBinding binding =
                resolver.resolveForNewRecord("call-1", "rtp", "stream-1");

        assertEquals(
                new CloudRecordSourceBinding(VideoResourceType.CHANNEL, 41, 41),
                binding);
        verify(sessionManager, never()).getSsrcTransactionByStream("rtp", "stream-1");
        verifyNoInteractions(resourceResolverRegistry);
    }

    @Test
    void appStreamSessionTakesPriorityOverStableStreamMapping() {
        when(sessionManager.getSsrcTransactionByStream("rtp", "stream-2"))
                .thenReturn(transaction(42));
        when(lookupMapper.findChannelId(42)).thenReturn(42L);

        CloudRecordSourceBinding binding =
                resolver.resolveForNewRecord(null, "rtp", "stream-2");

        assertEquals(
                new CloudRecordSourceBinding(VideoResourceType.CHANNEL, 42, 42),
                binding);
        verifyNoInteractions(resourceResolverRegistry);
    }

    @Test
    void stablePushOrProxySourceRequiresOneUnifiedChannelMapping() {
        when(sessionManager.getSsrcTransactionByStream("live", "camera-1"))
                .thenReturn(null);
        when(resourceResolverRegistry.resolveAppStream("live", "camera-1"))
                .thenReturn(new VideoResourceRef(VideoResourceType.STREAM_PROXY, 9));
        when(lookupMapper.findChannelIdsBySource(3, 9)).thenReturn(List.of(55L));

        CloudRecordSourceBinding binding =
                resolver.resolveForNewRecord(null, "live", "camera-1");

        assertEquals(
                new CloudRecordSourceBinding(VideoResourceType.STREAM_PROXY, 9, 55),
                binding);
    }

    @Test
    void multipleUnifiedChannelMappingsAreRejectedInsteadOfGuessed() {
        when(sessionManager.getSsrcTransactionByStream("live", "camera-2"))
                .thenReturn(null);
        when(resourceResolverRegistry.resolveAppStream("live", "camera-2"))
                .thenReturn(new VideoResourceRef(VideoResourceType.STREAM_PUSH, 10));
        when(lookupMapper.findChannelIdsBySource(2, 10))
                .thenReturn(List.of(61L, 62L));

        assertThrows(
                CloudRecordSourceResolutionException.class,
                () -> resolver.resolveForNewRecord(null, "live", "camera-2"));
    }

    @Test
    void invalidSessionChannelIsRejectedWithoutFallingThrough() {
        when(sessionManager.getSsrcTransactionByCallId("call-invalid"))
                .thenReturn(transaction(99));
        when(lookupMapper.findChannelId(99)).thenReturn(null);

        assertThrows(
                CloudRecordSourceResolutionException.class,
                () -> resolver.resolveForNewRecord(
                        "call-invalid", "live", "camera-3"));
        verify(sessionManager, never())
                .getSsrcTransactionByStream("live", "camera-3");
        verifyNoInteractions(resourceResolverRegistry);
    }

    @Test
    void aRecordWithoutAnyTraceableSourceIsRejected() {
        when(sessionManager.getSsrcTransactionByStream("live", "missing"))
                .thenReturn(null);
        when(resourceResolverRegistry.resolveAppStream("live", "missing"))
                .thenThrow(new VideoResourceNotFoundException("missing"));

        assertThrows(
                CloudRecordSourceResolutionException.class,
                () -> resolver.resolveForNewRecord(null, "live", "missing"));
    }

    private SsrcTransaction transaction(int channelId) {
        SsrcTransaction transaction = new SsrcTransaction();
        transaction.setChannelId(channelId);
        return transaction;
    }
}
