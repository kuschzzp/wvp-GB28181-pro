package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamResourceAccessAuthorizerTest {

    @Mock
    private AuthorizationFacade authorizationFacade;

    @Mock
    private ResourceResolverRegistry resolverRegistry;

    private StreamResourceAccessAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new StreamResourceAccessAuthorizer(
                authorizationFacade, resolverRegistry);
    }

    @Test
    void resolvesTypedLegacyAppStreamBeforeAuthorization() {
        VideoResourceRef proxy = ref(VideoResourceType.STREAM_PROXY, 41);
        VideoResourceRef push = ref(VideoResourceType.STREAM_PUSH, 42);
        when(resolverRegistry.resolveStreamProxy("live", "proxy-1"))
                .thenReturn(proxy);
        when(resolverRegistry.resolveStreamPush("live", "push-1"))
                .thenReturn(push);

        assertEquals(proxy, authorizer.requireProxy(
                "video:proxy:remove", "live", "proxy-1"));
        assertEquals(push, authorizer.requirePush(
                "video:push:stop", "live", "push-1"));

        verify(authorizationFacade).requireVideoResource(
                "video:proxy:remove", VideoResourceType.STREAM_PROXY, 41L);
        verify(authorizationFacade).requireVideoResource(
                "video:push:stop", VideoResourceType.STREAM_PUSH, 42L);
    }

    @Test
    void stableIdsAndBatchUseExactResourceTypes() {
        assertEquals(ref(VideoResourceType.STREAM_PROXY, 41),
                authorizer.requireProxyId("video:proxy:edit", 41));
        assertEquals(ref(VideoResourceType.STREAM_PUSH, 42),
                authorizer.requirePushId("video:push:play", 42));

        authorizer.requireAllPushIds(
                "video:push:remove", List.of(42, 43, 42));

        verify(authorizationFacade).requireVideoResource(
                "video:proxy:edit", VideoResourceType.STREAM_PROXY, 41L);
        verify(authorizationFacade).requireVideoResource(
                "video:push:play", VideoResourceType.STREAM_PUSH, 42L);
        verify(authorizationFacade).requireAllVideoResources(
                "video:push:remove",
                List.of(
                        ref(VideoResourceType.STREAM_PUSH, 42),
                        ref(VideoResourceType.STREAM_PUSH, 43)));
    }

    @Test
    void genericAppStreamUsesTheSharedResolverEntry() {
        VideoResourceRef channel = ref(VideoResourceType.CHANNEL, 44);
        when(resolverRegistry.resolveAppStream("live", "stream-44"))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireAppStream(
                "video:channel:play", "live", "stream-44"));

        verify(authorizationFacade).requireVideoResource(
                "video:channel:play", VideoResourceType.CHANNEL, 44L);
    }

    private VideoResourceRef ref(VideoResourceType type, long id) {
        return new VideoResourceRef(type, id);
    }
}
