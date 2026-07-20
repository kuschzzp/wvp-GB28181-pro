package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivedResourceAccessAuthorizerTest {

    private static final String RECORD_PLAY = "video:cloud-record:play";
    private static final String RECORD_REMOVE = "video:cloud-record:remove";
    private static final String ALARM_LIST = "video:alarm:list";
    private static final String ALARM_REMOVE = "video:alarm:remove";

    @Mock
    private AuthorizationFacade authorizationFacade;

    @Mock
    private ResourceResolverRegistry resolverRegistry;

    private DerivedResourceAccessAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new DerivedResourceAccessAuthorizer(
                authorizationFacade, resolverRegistry);
    }

    @Test
    void recordIsResolvedToItsSourceBeforeHiddenObjectAuthorization() {
        VideoResourceRef channel = channel(101);
        when(resolverRegistry.resolveRecord(41)).thenReturn(channel);

        assertEquals(channel, authorizer.requireRecord(RECORD_PLAY, 41));

        InOrder order = inOrder(resolverRegistry, authorizationFacade);
        order.verify(resolverRegistry).resolveRecord(41);
        order.verify(authorizationFacade).requireVideoResource(
                RECORD_PLAY,
                VideoResourceType.CHANNEL,
                101,
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
    }

    @Test
    void alarmIsResolvedToItsChannelBeforeReadingSnapshotPath() {
        VideoResourceRef channel = channel(202);
        when(resolverRegistry.resolveAlarm(51)).thenReturn(channel);

        assertEquals(channel, authorizer.requireAlarm(ALARM_LIST, 51));

        verify(authorizationFacade).requireVideoResource(
                ALARM_LIST,
                VideoResourceType.CHANNEL,
                202,
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
    }

    @Test
    void recordBatchResolvesEveryIdBeforeOneAtomicAuthorizationCall() {
        VideoResourceRef first = channel(101);
        VideoResourceRef second = channel(202);
        when(resolverRegistry.resolveRecord(41)).thenReturn(first);
        when(resolverRegistry.resolveRecord(42)).thenReturn(second);

        authorizer.requireAllRecords(RECORD_REMOVE, List.of(41, 42));

        InOrder order = inOrder(resolverRegistry, authorizationFacade);
        order.verify(resolverRegistry).resolveRecord(41);
        order.verify(resolverRegistry).resolveRecord(42);
        order.verify(authorizationFacade).requireAllVideoResources(
                RECORD_REMOVE, List.of(first, second));
    }

    @Test
    void alarmBatchRejectsMissingMemberBeforeAnyDeleteCanRun() {
        VideoResourceRef first = channel(101);
        when(resolverRegistry.resolveAlarm(51)).thenReturn(first);
        when(resolverRegistry.resolveAlarm(52)).thenThrow(
                new VideoResourceNotFoundException("alarm missing"));

        assertThrows(VideoResourceNotFoundException.class,
                () -> authorizer.requireAllAlarms(
                        ALARM_REMOVE, List.of(51L, 52L)));

        verify(authorizationFacade, never()).requireAllVideoResources(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void emptyOrNullBatchesAreRejectedBeforeAuthorization() {
        assertThrows(IllegalArgumentException.class,
                () -> authorizer.requireAllRecords(RECORD_REMOVE, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> authorizer.requireAllAlarms(
                        ALARM_REMOVE, java.util.Arrays.asList(51L, null)));

        verify(authorizationFacade, never()).requireAllVideoResources(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyCollection());
    }

    private VideoResourceRef channel(long id) {
        return new VideoResourceRef(VideoResourceType.CHANNEL, id);
    }
}
