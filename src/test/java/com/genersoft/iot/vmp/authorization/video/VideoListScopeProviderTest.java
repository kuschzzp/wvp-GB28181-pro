package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.dao.provider.ChannelProvider;
import com.genersoft.iot.vmp.gb28181.dao.provider.DeviceChannelProvider;
import com.genersoft.iot.vmp.gb28181.dao.provider.DeviceProvider;
import com.genersoft.iot.vmp.streamProxy.dao.provider.StreamProxyProvider;
import com.genersoft.iot.vmp.streamPush.dao.provider.StreamPushProvider;
import com.genersoft.iot.vmp.web.custom.bean.CameraGroup;
import com.genersoft.iot.vmp.web.custom.bean.Point;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoListScopeProviderTest {

    @Test
    void allScopeKeepsLegacyListQueriesWithoutAnAuthorizationPredicate() {
        VideoScopeQuery scope = allScope();

        assertUnrestricted(new DeviceProvider().getDeviceList(params(scope)));
        assertUnrestricted(new DeviceChannelProvider().queryChannels(params(scope)));
        assertUnrestricted(new DeviceChannelProvider().queryChannelsWithDeviceInfo(params(scope)));
        assertUnrestricted(new ChannelProvider().queryList(params(scope)));
        assertUnrestricted(new StreamProxyProvider().selectAll(params(scope)));
        assertUnrestricted(new StreamPushProvider().selectAll(params(scope)));
    }

    @Test
    void noneScopeFailsClosedForEveryCoreVideoList() {
        VideoScopeQuery scope = noneScope();

        assertDenied(new DeviceProvider().getDeviceList(params(scope)));
        assertDenied(new DeviceChannelProvider().queryChannels(params(scope)));
        assertDenied(new DeviceChannelProvider().queryChannelsWithDeviceInfo(params(scope)));
        assertDenied(new ChannelProvider().queryList(params(scope)));
        assertDenied(new StreamProxyProvider().selectAll(params(scope)));
        assertDenied(new StreamPushProvider().selectAll(params(scope)));
    }

    @Test
    void customScopeUsesBoundRoleIdsAndSupportsSourceOrExplicitChannelGrants() {
        VideoScopeQuery scope = customScope();

        String devices = new DeviceProvider().getDeviceList(params(scope));
        assertBoundScope(devices);
        assertTrue(devices.contains("rgi.resource_type = 'GB_DEVICE'"));
        assertTrue(devices.contains("authorized_channel.data_device_id = de.id"));

        String deviceChannels = new DeviceChannelProvider().queryChannels(params(scope));
        assertBoundScope(deviceChannels);
        assertChannelMatchers(deviceChannels, "dc");

        String deviceChannelsWithDevice =
                new DeviceChannelProvider().queryChannelsWithDeviceInfo(params(scope));
        assertBoundScope(deviceChannelsWithDevice);
        assertChannelMatchers(deviceChannelsWithDevice, "dc");

        String unifiedChannels = new ChannelProvider().queryList(params(scope));
        assertBoundScope(unifiedChannels);
        assertChannelMatchers(unifiedChannels, "wvp_device_channel");

        String proxies = new StreamProxyProvider().selectAll(params(scope));
        assertBoundScope(proxies);
        assertTrue(proxies.contains("rgi.resource_type = 'STREAM_PROXY'"));
        assertTrue(proxies.contains("rgi.resource_id = st.id"));
        assertTrue(proxies.contains("rgi.resource_id = wdc.id"));

        String pushes = new StreamPushProvider().selectAll(params(scope));
        assertBoundScope(pushes);
        assertTrue(pushes.contains("rgi.resource_type = 'STREAM_PUSH'"));
        assertTrue(pushes.contains("rgi.resource_id = st.id"));
        assertTrue(pushes.contains("rgi.resource_id = wdc.id"));
    }

    @Test
    void secondaryChannelListsAndMapQueriesShareTheSameScopeContract() {
        ChannelProvider provider = new ChannelProvider();

        for (String sql : secondaryChannelQueries(provider, noneScope())) {
            assertDenied(sql);
        }
        for (String sql : secondaryChannelQueries(provider, customScope())) {
            assertBoundScope(sql);
        }
        for (String sql : secondaryChannelQueries(provider, allScope())) {
            assertUnrestricted(sql);
        }
    }

    @Test
    void customCameraListsCountsAndSpatialQueriesCannotBypassVideoScope() {
        ChannelProvider provider = new ChannelProvider();

        for (String sql : customCameraQueries(provider, noneScope())) {
            assertDenied(sql);
        }
        for (String sql : customCameraQueries(provider, customScope())) {
            assertBoundScope(sql);
        }
        for (String sql : customCameraQueries(provider, allScope())) {
            assertUnrestricted(sql);
        }
    }

    @Test
    void platformChannelPickerUsesTheSameChannelScopeContract() {
        ChannelProvider provider = new ChannelProvider();

        assertDenied(provider.queryForPlatformForWebList(params(noneScope())));
        String customSql = provider.queryForPlatformForWebList(
                params(customScope()));
        assertBoundScope(customSql);
        assertChannelMatchers(customSql, "wdc");
        assertTrue(customSql.contains(
                "wpgc.platform_id = #{platformId}"));
        assertUnrestricted(provider.queryForPlatformForWebList(
                params(allScope())));
    }

    private List<String> secondaryChannelQueries(
            ChannelProvider provider,
            VideoScopeQuery scope) {
        Map<String, Object> parameters = params(scope);
        return List.of(
                provider.queryListByCivilCode(parameters),
                provider.queryListByParentId(parameters),
                provider.queryListByCivilCodeForUnusual(parameters),
                provider.queryListByParentForUnusual(parameters),
                provider.queryAllForUnusualCivilCode(parameters),
                provider.queryAllForUnusualParent(parameters),
                provider.queryCameraChannelInBox(parameters),
                provider.queryAllWithPosition(parameters),
                provider.queryListInExtent(parameters));
    }

    private List<String> customCameraQueries(
            ChannelProvider provider,
            VideoScopeQuery scope) {
        Map<String, Object> parameters = params(scope);
        CameraGroup group = new CameraGroup();
        group.setDeviceId("group-a");
        Point point = new Point();
        point.setLng(120D);
        point.setLat(30D);
        parameters.put("groupList", List.of(group));
        parameters.put("pointList", List.of(point, point, point));
        parameters.put("deviceIds", List.of("channel-a"));
        return List.of(
                provider.queryListForSy(parameters),
                provider.queryListWithChildForSy(parameters),
                provider.queryCountWithChildForSy(parameters),
                provider.queryListByDeviceIds(parameters),
                provider.queryListByAddressAndDirectionType(parameters),
                provider.queryListInBox(parameters),
                provider.queryListInCircleForMysql(parameters),
                provider.queryListInCircleForKingBase(parameters),
                provider.queryListInPolygonForMysql(parameters),
                provider.queryListInPolygonForKingBase(parameters),
                provider.queryListForSyMobile(parameters),
                provider.queryMeetingChannelList(parameters));
    }

    private Map<String, Object> params(VideoScopeQuery scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("scope", scope);
        return params;
    }

    private VideoScopeQuery allScope() {
        return new VideoScopeQuery(
                true, Set.of(), Set.of(), Set.of(), "video:channel:list", 12L);
    }

    private VideoScopeQuery noneScope() {
        return new VideoScopeQuery(
                false, Set.of(), Set.of(), Set.of(), "video:channel:list", 12L);
    }

    private VideoScopeQuery customScope() {
        return new VideoScopeQuery(
                false,
                Set.of(17L, 29L),
                Set.of(
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 11L),
                        new VideoResourceRef(VideoResourceType.STREAM_PROXY, 41L),
                        new VideoResourceRef(VideoResourceType.STREAM_PUSH, 42L)),
                Set.of(101L),
                "video:channel:list",
                12L);
    }

    private void assertUnrestricted(String sql) {
        assertFalse(sql.contains("wvp_role_resource_group"));
        assertFalse(sql.contains("1=0"));
    }

    private void assertDenied(String sql) {
        assertTrue(sql.contains("AND 1=0"));
        assertFalse(sql.contains("wvp_role_resource_group"));
    }

    private void assertBoundScope(String sql) {
        assertTrue(sql.contains("wvp_role_resource_group"));
        assertTrue(sql.contains("wvp_resource_group_closure"));
        assertTrue(sql.contains("collection=\"scope.grantingRoleIds\""));
        assertTrue(sql.contains("#{scopeRoleId}"));
        assertTrue(sql.contains("role_scope.status = 0"));
        assertTrue(sql.contains("role_scope.video_scope_type = 'CUSTOM'"));
        assertTrue(sql.contains("ancestor.status &lt;&gt; 0"));
        assertFalse(sql.contains("17"));
        assertFalse(sql.contains("29"));
        assertFalse(sql.contains("${"));
    }

    private void assertChannelMatchers(String sql, String channelAlias) {
        assertTrue(sql.contains("rgi.resource_type = 'CHANNEL'"));
        assertTrue(sql.contains("rgi.resource_id = " + channelAlias + ".id"));
        assertTrue(sql.contains("rgi.resource_type = 'GB_DEVICE'"));
        assertTrue(sql.contains(channelAlias + ".data_type = 1"));
        assertTrue(sql.contains("rgi.resource_type = 'STREAM_PUSH'"));
        assertTrue(sql.contains(channelAlias + ".data_type = 2"));
        assertTrue(sql.contains("rgi.resource_type = 'STREAM_PROXY'"));
        assertTrue(sql.contains(channelAlias + ".data_type = 3"));
    }
}
