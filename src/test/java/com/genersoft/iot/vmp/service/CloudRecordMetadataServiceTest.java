package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBinding;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBindingResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceResolutionException;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudRecordMetadataServiceTest {

    @Mock private CloudRecordSourceBindingResolver bindingResolver;
    @Mock private CloudRecordServiceMapper mapper;

    @Test
    void resolvedSourceFieldsAreWrittenInTheSameInsert() {
        CloudRecordMetadataService service =
                new CloudRecordMetadataService(bindingResolver, mapper);
        CloudRecordItem item = record();
        when(bindingResolver.resolveForNewRecord("call-1", "rtp", "stream-1"))
                .thenReturn(new CloudRecordSourceBinding(
                        VideoResourceType.CHANNEL, 41, 41));
        when(mapper.add(any())).thenReturn(1);

        assertTrue(service.addResolved(item));

        ArgumentCaptor<CloudRecordItem> captor =
                ArgumentCaptor.forClass(CloudRecordItem.class);
        verify(mapper).add(captor.capture());
        assertEquals("CHANNEL", captor.getValue().getSourceType());
        assertEquals(41L, captor.getValue().getSourceId());
        assertEquals(41L, captor.getValue().getChannelId());
    }

    @Test
    void unresolvedSourceDoesNotCreateLegacyMetadataForANewRecord() {
        CloudRecordMetadataService service =
                new CloudRecordMetadataService(bindingResolver, mapper);
        CloudRecordItem item = record();
        when(bindingResolver.resolveForNewRecord("call-1", "rtp", "stream-1"))
                .thenThrow(new CloudRecordSourceResolutionException("missing"));

        assertFalse(service.addResolved(item));
        verify(mapper, never()).add(any());
    }

    @Test
    void insertFailureIsNotSwallowedSoTheTransactionCanRollBack() {
        CloudRecordMetadataService service =
                new CloudRecordMetadataService(bindingResolver, mapper);
        CloudRecordItem item = record();
        when(bindingResolver.resolveForNewRecord("call-1", "rtp", "stream-1"))
                .thenReturn(new CloudRecordSourceBinding(
                        VideoResourceType.CHANNEL, 41, 41));
        when(mapper.add(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.addResolved(item));
    }

    private CloudRecordItem record() {
        CloudRecordItem item = new CloudRecordItem();
        item.setApp("rtp");
        item.setStream("stream-1");
        item.setCallId("call-1");
        return item;
    }
}
