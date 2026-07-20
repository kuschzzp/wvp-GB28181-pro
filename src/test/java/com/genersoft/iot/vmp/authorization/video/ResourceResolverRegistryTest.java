package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.AlarmResourceResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.AmbiguousVideoResourceException;
import com.genersoft.iot.vmp.authorization.video.resolver.ChannelResourceResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.ChannelAuthorizationSource;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource;
import com.genersoft.iot.vmp.authorization.video.resolver.DefaultResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.RecordResourceResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.StreamResourceResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceLookupMapper;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.common.InviteInfo;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceResolverRegistryTest {

    @Mock
    private VideoResourceLookupMapper lookupMapper;

    @Mock
    private IInviteStreamService inviteStreamService;

    private ResourceResolverRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultResourceResolverRegistry(List.of(
                new ChannelResourceResolver(lookupMapper),
                new StreamResourceResolver(lookupMapper, inviteStreamService),
                new RecordResourceResolver(lookupMapper),
                new AlarmResourceResolver(lookupMapper)));
    }

    @Test
    void requiresAllSixBindableResourcesByStableDatabaseId() {
        when(lookupMapper.findGbDeviceId(11)).thenReturn(11L);
        when(lookupMapper.findChannelId(12)).thenReturn(12L);
        when(lookupMapper.findJtTerminalId(13)).thenReturn(13L);
        when(lookupMapper.findJtChannelId(14)).thenReturn(14L);
        when(lookupMapper.findStreamProxyId(15)).thenReturn(15L);
        when(lookupMapper.findStreamPushId(16)).thenReturn(16L);

        List<VideoResourceRef> resources = List.of(
                ref(VideoResourceType.GB_DEVICE, 11),
                ref(VideoResourceType.CHANNEL, 12),
                ref(VideoResourceType.JT_TERMINAL, 13),
                ref(VideoResourceType.JT_CHANNEL, 14),
                ref(VideoResourceType.STREAM_PROXY, 15),
                ref(VideoResourceType.STREAM_PUSH, 16));

        for (VideoResourceRef resource : resources) {
            assertEquals(resource, registry.requireExisting(resource));
            assertEquals(resource, registry.resolveToAuthorizable(resource));
        }
    }

    @Test
    void resolvesGbChannelWithBothDeviceAndChannelNumberAndRejectsAmbiguity() {
        when(lookupMapper.findGbChannelIds("device-a", "same-channel"))
                .thenReturn(List.of(101L));
        when(lookupMapper.findGbChannelIds("device-b", "same-channel"))
                .thenReturn(List.of(202L));
        when(lookupMapper.findGbChannelIds("broken-device", "same-channel"))
                .thenReturn(List.of(101L, 202L));

        assertEquals(ref(VideoResourceType.CHANNEL, 101),
                registry.resolveGbChannel("device-a", "same-channel"));
        assertEquals(ref(VideoResourceType.CHANNEL, 202),
                registry.resolveGbChannel("device-b", "same-channel"));
        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveGbChannel("broken-device", "same-channel"));
    }

    @Test
    void resolvesExternalGbDeviceOnlyWhenItsPlatformIdIsUnique() {
        when(lookupMapper.findGbDeviceIdsByDeviceId("device-a"))
                .thenReturn(List.of(11L));
        when(lookupMapper.findGbDeviceIdsByDeviceId("duplicate"))
                .thenReturn(List.of(11L, 22L));

        assertEquals(ref(VideoResourceType.GB_DEVICE, 11),
                registry.resolveGbDevice(" device-a "));
        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveGbDevice("duplicate"));
        assertThrows(VideoResourceNotFoundException.class,
                () -> registry.resolveGbDevice("missing"));
    }

    @Test
    void resolvesUnifiedCompatibilityChannelOnlyWhenTheExternalPairIsUnique() {
        when(lookupMapper.findUnifiedChannelIds("same-channel", "device-a"))
                .thenReturn(List.of(101L));
        when(lookupMapper.findUnifiedChannelIds("proxy-channel", null))
                .thenReturn(List.of(404L));
        when(lookupMapper.findUnifiedChannelIds("same-channel", null))
                .thenReturn(List.of(101L, 202L));

        assertEquals(ref(VideoResourceType.CHANNEL, 101),
                registry.resolveUnifiedChannel("same-channel", "device-a"));
        assertEquals(ref(VideoResourceType.CHANNEL, 404),
                registry.resolveUnifiedChannel("proxy-channel", null));
        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveUnifiedChannel("same-channel", null));
    }

    @Test
    void resolvesJtExternalIdentifiersOnlyWhenTheyAreUnique() {
        when(lookupMapper.findJtTerminalIdsByPhoneNumber("13800138000"))
                .thenReturn(List.of(31L));
        when(lookupMapper.findJtChannelIdsByPhoneNumberAndChannelId(
                "13800138000", 2))
                .thenReturn(List.of(32L));

        assertEquals(ref(VideoResourceType.JT_TERMINAL, 31),
                registry.resolveJtTerminal(" 13800138000 "));
        assertEquals(ref(VideoResourceType.JT_CHANNEL, 32),
                registry.resolveJtChannel("13800138000", 2));

        when(lookupMapper.findJtTerminalIdsByPhoneNumber("duplicate"))
                .thenReturn(List.of(31L, 33L));
        when(lookupMapper.findJtChannelIdsByPhoneNumberAndChannelId(
                "duplicate", 2))
                .thenReturn(List.of(32L, 34L));

        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveJtTerminal("duplicate"));
        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveJtChannel("duplicate", 2));
        assertThrows(VideoResourceNotFoundException.class,
                () -> registry.resolveJtChannel("13800138000", 0));
    }

    @Test
    void resolvesTypedProxyAndPushSourcesWithoutCrossTypeGuessing() {
        when(lookupMapper.findStreamProxyIdByAppStream("live", "proxy-1"))
                .thenReturn(301L);
        when(lookupMapper.findStreamProxyId(301)).thenReturn(301L);
        when(lookupMapper.findStreamPushIdByAppStream("live", "push-1"))
                .thenReturn(302L);
        when(lookupMapper.findStreamPushId(302)).thenReturn(302L);

        assertEquals(ref(VideoResourceType.STREAM_PROXY, 301),
                registry.resolveStreamProxy(" live ", " proxy-1 "));
        assertEquals(ref(VideoResourceType.STREAM_PUSH, 302),
                registry.resolveStreamPush("live", "push-1"));

        assertThrows(VideoResourceNotFoundException.class,
                () -> registry.resolveStreamProxy("live", "missing"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolveStreamPush(" ", "push-1"));
    }

    @Test
    void resolvesAppStreamOnlyWhenItHasOneCurrentSource() {
        when(inviteStreamService.getAllInviteInfo()).thenReturn(List.of());
        when(lookupMapper.findStreamProxyIdByAppStream("live", "camera-1"))
                .thenReturn(301L);
        when(lookupMapper.findStreamProxyId(301)).thenReturn(301L);

        assertEquals(ref(VideoResourceType.STREAM_PROXY, 301),
                registry.resolveAppStream("live", "camera-1"));

        when(lookupMapper.findStreamPushIdByAppStream("live", "camera-1"))
                .thenReturn(302L);

        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveAppStream("live", "camera-1"));
    }

    @Test
    void resolvesCurrentGbMediaStreamToItsUnifiedChannel() {
        InviteInfo invite = invite("rtp", "play-stream", 101);
        when(inviteStreamService.getAllInviteInfo()).thenReturn(List.of(invite));
        when(lookupMapper.findChannelId(101)).thenReturn(101L);

        assertEquals(ref(VideoResourceType.CHANNEL, 101),
                registry.resolveAppStream("rtp", "play-stream"));

        when(inviteStreamService.getAllInviteInfo()).thenReturn(List.of(
                invite,
                invite("rtp", "play-stream", 102)));

        assertThrows(AmbiguousVideoResourceException.class,
                () -> registry.resolveAppStream("rtp", "play-stream"));
    }

    @Test
    void resolvesRecordsAlarmsAndEphemeralResourcesBackToTheirSource() {
        when(lookupMapper.findCloudRecordSource(401))
                .thenReturn(new CloudRecordSource(401, "CHANNEL", 101L, 101L));
        when(lookupMapper.findCloudRecordSource(402))
                .thenReturn(new CloudRecordSource(402, "STREAM_PROXY", 301L, null));
        when(lookupMapper.findCloudRecordSource(403))
                .thenReturn(new CloudRecordSource(403, "LEGACY_UNBOUND", null, null));
        when(lookupMapper.findAlarmChannelId(501)).thenReturn(101L);
        when(lookupMapper.findChannelId(101)).thenReturn(101L);
        when(lookupMapper.findStreamProxyId(301)).thenReturn(301L);

        assertEquals(ref(VideoResourceType.CHANNEL, 101), registry.resolveRecord(401));
        assertEquals(ref(VideoResourceType.STREAM_PROXY, 301), registry.resolveRecord(402));
        assertEquals(ref(VideoResourceType.LEGACY_UNBOUND, 403), registry.resolveRecord(403));
        assertEquals(ref(VideoResourceType.CHANNEL, 101), registry.resolveAlarm(501));
        assertEquals(ref(VideoResourceType.CHANNEL, 101), registry.resolveToAuthorizable(
                ref(VideoResourceType.DEVICE_RECORD, 101)));
        assertEquals(ref(VideoResourceType.CHANNEL, 101), registry.resolveToAuthorizable(
                ref(VideoResourceType.MEDIA_STREAM, 101)));
    }

    @Test
    void authorizationCandidatesIncludeUnifiedChannelSourcesAndDerivedResourcesInheritThem() {
        when(lookupMapper.findChannelId(101)).thenReturn(101L);
        when(lookupMapper.findChannelAuthorizationSource(101))
                .thenReturn(new ChannelAuthorizationSource(1, 11L, null));
        when(lookupMapper.findGbDeviceId(11)).thenReturn(11L);
        when(lookupMapper.findCloudRecordSource(401))
                .thenReturn(new CloudRecordSource(401, "CHANNEL", 101L, 101L));
        when(lookupMapper.findAlarmChannelId(501)).thenReturn(101L);

        Set<VideoResourceRef> expected = Set.of(
                ref(VideoResourceType.CHANNEL, 101),
                ref(VideoResourceType.GB_DEVICE, 11));

        assertEquals(expected, registry.resolveAuthorizationCandidates(
                ref(VideoResourceType.CHANNEL, 101)));
        assertEquals(expected, registry.resolveAuthorizationCandidates(
                ref(VideoResourceType.CLOUD_RECORD, 401)));
        assertEquals(expected, registry.resolveAuthorizationCandidates(
                ref(VideoResourceType.ALARM, 501)));
    }

    @Test
    void jtChannelAuthorizationCandidatesIncludeChannelAndTerminal() {
        when(lookupMapper.findChannelId(303)).thenReturn(303L);
        when(lookupMapper.findChannelAuthorizationSource(303))
                .thenReturn(new ChannelAuthorizationSource(200, 32L, 31L));
        when(lookupMapper.findJtChannelId(32)).thenReturn(32L);
        when(lookupMapper.findJtTerminalId(31)).thenReturn(31L);

        assertEquals(Set.of(
                        ref(VideoResourceType.CHANNEL, 303),
                        ref(VideoResourceType.JT_CHANNEL, 32),
                        ref(VideoResourceType.JT_TERMINAL, 31)),
                registry.resolveAuthorizationCandidates(
                        ref(VideoResourceType.CHANNEL, 303)));
    }

    @Test
    void failsClosedWhenResourceOrDerivedSourceDoesNotExist() {
        assertThrows(VideoResourceNotFoundException.class,
                () -> registry.requireExisting(ref(VideoResourceType.CHANNEL, 999)));

        when(lookupMapper.findCloudRecordSource(404))
                .thenReturn(new CloudRecordSource(404, "STREAM_PROXY", 999L, null));

        assertThrows(VideoResourceNotFoundException.class,
                () -> registry.resolveRecord(404));
    }

    @Test
    void rejectsDuplicateResolversAndMissingBindableTypesAtStartup() {
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> new DefaultResourceResolverRegistry(List.of(
                        fixedResolver(Set.of(VideoResourceType.GB_DEVICE)),
                        fixedResolver(Set.of(VideoResourceType.GB_DEVICE)))));
        assertTrue(duplicate.getMessage().contains("GB_DEVICE"));

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> new DefaultResourceResolverRegistry(List.of(
                        fixedResolver(Set.of(VideoResourceType.GB_DEVICE)))));
        assertTrue(missing.getMessage().contains("可绑定资源"));
    }

    private InviteInfo invite(String app, String stream, int channelId) {
        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setApp(app);
        streamInfo.setStream(stream);
        streamInfo.setChannelId(channelId);
        InviteInfo invite = new InviteInfo();
        invite.setStream(stream);
        invite.setChannelId(channelId);
        invite.setStreamInfo(streamInfo);
        return invite;
    }

    private ResourceResolver fixedResolver(Set<VideoResourceType> types) {
        return new ResourceResolver() {
            @Override
            public Set<VideoResourceType> supportedTypes() {
                return types;
            }

            @Override
            public VideoResourceRef requireExisting(VideoResourceRef resource) {
                return resource;
            }

            @Override
            public VideoResourceRef resolveToAuthorizable(VideoResourceRef resource) {
                return resource;
            }
        };
    }

    private VideoResourceRef ref(VideoResourceType type, long id) {
        return new VideoResourceRef(type, id);
    }
}
