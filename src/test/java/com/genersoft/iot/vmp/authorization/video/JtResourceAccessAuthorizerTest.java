package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JtResourceAccessAuthorizerTest {

    @Mock
    private AuthorizationFacade authorizationFacade;

    @Mock
    private ResourceResolverRegistry resolverRegistry;

    private JtResourceAccessAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new JtResourceAccessAuthorizer(
                authorizationFacade, resolverRegistry);
    }

    @Test
    void resolvesExternalTerminalBeforeAuthorization() {
        VideoResourceRef terminal = ref(VideoResourceType.JT_TERMINAL, 31);
        when(resolverRegistry.resolveJtTerminal("13800138000"))
                .thenReturn(terminal);

        assertEquals(terminal, authorizer.requireTerminal(
                "video:jt:control", "13800138000"));

        InOrder order = inOrder(resolverRegistry, authorizationFacade);
        order.verify(resolverRegistry).resolveJtTerminal("13800138000");
        order.verify(authorizationFacade).requireVideoResource(
                "video:jt:control", VideoResourceType.JT_TERMINAL, 31L);
    }

    @Test
    void resolvesExternalChannelBeforeAuthorization() {
        VideoResourceRef channel = ref(VideoResourceType.JT_CHANNEL, 32);
        when(resolverRegistry.resolveJtChannel("13800138000", 2))
                .thenReturn(channel);

        assertEquals(channel, authorizer.requireChannel(
                "video:jt:play", "13800138000", 2));

        verify(authorizationFacade).requireVideoResource(
                "video:jt:play", VideoResourceType.JT_CHANNEL, 32L);
    }

    @Test
    void wildcardChannelRequiresTheWholeTerminal() {
        VideoResourceRef terminal = ref(VideoResourceType.JT_TERMINAL, 31);
        when(resolverRegistry.resolveJtTerminal("13800138000"))
                .thenReturn(terminal);

        assertEquals(terminal, authorizer.requireChannelOrTerminal(
                "video:jt:list", "13800138000", 0));

        verify(resolverRegistry, never()).resolveJtChannel(
                "13800138000", 0);
    }

    @Test
    void stableDatabaseIdsDoNotUseExternalResolvers() {
        assertEquals(ref(VideoResourceType.JT_TERMINAL, 31),
                authorizer.requireTerminalId("video:jt:edit", 31));
        assertEquals(ref(VideoResourceType.JT_CHANNEL, 32),
                authorizer.requireChannelId("video:jt:edit", 32));

        verify(resolverRegistry, never()).resolveJtTerminal(
                org.mockito.ArgumentMatchers.anyString());
        verify(resolverRegistry, never()).resolveJtChannel(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private VideoResourceRef ref(VideoResourceType type, long id) {
        return new VideoResourceRef(type, id);
    }
}
