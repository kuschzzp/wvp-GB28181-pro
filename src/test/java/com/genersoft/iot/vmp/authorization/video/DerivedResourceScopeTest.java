package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.authorization.video.provider.VideoOverviewProvider;
import com.genersoft.iot.vmp.gb28181.dao.provider.ChannelProvider;
import com.genersoft.iot.vmp.gb28181.dao.provider.MobilePositionProvider;
import com.genersoft.iot.vmp.jt1078.dao.provider.JTChannelProvider;
import com.genersoft.iot.vmp.jt1078.dao.provider.JTTerminalProvider;
import com.genersoft.iot.vmp.storager.dao.provider.AlarmProvider;
import com.genersoft.iot.vmp.storager.dao.provider.CloudRecordProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedResourceScopeTest {

    @Test
    void jtTerminalAndChannelListsSupportSourceAndFineGrainedGrants() {
        VideoScopeQuery scope = customScope();

        String terminals = new JTTerminalProvider().getDeviceList(params(scope));
        assertBoundRoleIds(terminals);
        assertTrue(terminals.contains("rgi.resource_type = 'JT_TERMINAL'"));
        assertTrue(terminals.contains("authorized_jt_channel.terminal_db_id = jt.id"));

        String channels = new JTChannelProvider().selectAll(params(scope));
        assertBoundRoleIds(channels);
        assertTrue(channels.contains("rgi.resource_type = 'JT_CHANNEL'"));
        assertTrue(channels.contains("rgi.resource_id = jc.id"));
        assertTrue(channels.contains("rgi.resource_type = 'JT_TERMINAL'"));
        assertTrue(channels.contains("rgi.resource_id = jc.terminal_db_id"));
    }

    @Test
    void internalJtQueriesDoNotRequireOrForgeUserScope() {
        String terminals = new JTTerminalProvider()
                .getDeviceListForInternalMaintenance(new HashMap<>());
        Map<String, Object> channelParams = new HashMap<>();
        channelParams.put("terminalDbId", 7);
        String channels = new JTChannelProvider()
                .selectAllForInternalMaintenance(channelParams);

        assertUnscopedInternalQuery(terminals);
        assertUnscopedInternalQuery(channels);
    }

    @Test
    void alarmAndPositionQueriesReuseUnifiedChannelAuthorization() {
        VideoScopeQuery scope = customScope();

        String alarms = new AlarmProvider().getAlarms(params(scope));
        assertBoundRoleIds(alarms);
        assertChannelMatchers(alarms, "wdc");

        MobilePositionProvider positions = new MobilePositionProvider();
        String history = positions.queryPositionByDeviceIdAndTime(params(scope));
        String latest = positions.queryLatestPosition(params(scope));
        assertBoundRoleIds(history);
        assertBoundRoleIds(latest);
        assertChannelMatchers(history, "wdc");
        assertChannelMatchers(latest, "wdc");
        assertTrue(history.contains("mp.timestamp &gt;= #{startTime}"));
        assertTrue(history.contains("mp.timestamp &lt;= #{endTime}"));
    }

    @Test
    void cloudRecordsCombineChannelAndStableSourceAuthorization() {
        CloudRecordProvider provider = new CloudRecordProvider();
        String sql = provider.getList(params(customScope()));
        String exportSql = provider.queryRecordFilePathList(params(customScope()));

        assertBoundRoleIds(sql);
        assertBoundRoleIds(exportSql);
        assertChannelMatchers(sql, "record_channel");
        assertChannelMatchers(exportSql, "record_channel");
        assertTrue(sql.contains("rgi.resource_type = cr.source_type"));
        assertTrue(sql.contains("rgi.resource_id = cr.source_id"));
        assertTrue(exportSql.contains("rgi.resource_type = cr.source_type"));
        assertTrue(exportSql.contains("cr.file_path"));
        assertTrue(exportSql.contains("cr.end_time &gt;= #{startTimeStamp}"));
        assertTrue(exportSql.contains("cr.start_time &lt;= #{endTimeStamp}"));
        assertTrue(sql.contains("coalesce(cr.source_type, 'LEGACY_UNBOUND')"));
        assertFalse(sql.contains("${"));
        assertFalse(exportSql.contains("${"));
    }

    @Test
    void cloudRecordMergeTasksVerifyTheirSourceAgainstTheEffectiveScope() {
        CloudRecordProvider provider = new CloudRecordProvider();
        String sql = provider.countAuthorizedTaskSources(params(customScope()));

        assertBoundRoleIds(sql);
        assertChannelMatchers(sql, "record_channel");
        assertTrue(sql.contains("cr.app = #{app}"));
        assertTrue(sql.contains("cr.stream = #{stream}"));
        assertTrue(sql.contains("cr.call_id = #{callId}"));
        assertTrue(sql.toLowerCase().contains("count(1)"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void legacyCloudRecordsAreVisibleOnlyToSystemUsers() {
        CloudRecordProvider provider = new CloudRecordProvider();

        String regularAll = provider.getList(params(allScope(false)));
        assertTrue(regularAll.contains("&lt;&gt; 'LEGACY_UNBOUND'"));

        String systemAll = provider.getList(params(allScope(true)));
        assertFalse(systemAll.contains("LEGACY_UNBOUND"));

        String regularNone = provider.getList(params(noneScope(false)));
        assertTrue(regularNone.contains("AND 1=0"));

        String systemNone = provider.getList(params(noneScope(true)));
        assertTrue(systemNone.contains("= 'LEGACY_UNBOUND'"));
        assertFalse(systemNone.contains("AND 1=0"));
    }

    @Test
    void cloudRecordInternalQueryIsExplicitAndUnscoped() {
        String sql = new CloudRecordProvider()
                .getListForInternalMaintenance(new HashMap<>());

        assertUnscopedInternalQuery(sql);
        assertFalse(sql.contains("LEGACY_UNBOUND"));
    }

    @Test
    void recordPlanChannelQueriesApplyTheSameUnifiedChannelScope() {
        ChannelProvider provider = new ChannelProvider();
        Map<String, Object> parameters = params(customScope());
        parameters.put("planId", 7);
        parameters.put("hasLink", true);

        String listSql = provider.queryForRecordPlanForWebList(parameters);
        String idSql = provider.queryRecordPlanChannelIds(parameters);

        for (String sql : Set.of(listSql, idSql)) {
            assertBoundRoleIds(sql);
            assertChannelMatchers(sql, "wdc");
            assertTrue(sql.contains("wdc.record_plan_id = #{planId}"));
            assertFalse(sql.contains("${"));
        }
    }

    @Test
    void overviewCountsReuseTheSameDatabaseScopeAsTheirLists() {
        VideoOverviewProvider provider = new VideoOverviewProvider();
        Map<String, Object> parameters = params(customScope());

        String devices = provider.deviceOverview(parameters);
        String channels = provider.channelOverview(parameters);
        String pushes = provider.pushOverview(parameters);
        String proxies = provider.proxyOverview(parameters);

        for (String sql : Set.of(devices, channels, pushes, proxies)) {
            assertBoundRoleIds(sql);
            assertTrue(sql.toLowerCase().contains("count(distinct"));
            assertFalse(sql.contains("${"));
        }
        assertTrue(devices.contains("rgi.resource_type = 'GB_DEVICE'"));
        assertChannelMatchers(channels, "dc");
        assertTrue(pushes.contains("rgi.resource_type = 'STREAM_PUSH'"));
        assertTrue(pushes.contains("rgi.resource_id = wdc.id"));
        assertTrue(proxies.contains("rgi.resource_type = 'STREAM_PROXY'"));
        assertTrue(proxies.contains("rgi.resource_id = wdc.id"));
    }

    @Test
    void noneOverviewScopeReturnsZeroRowsAtTheDatabaseBoundary() {
        VideoOverviewProvider provider = new VideoOverviewProvider();
        Map<String, Object> parameters = params(noneScope(false));

        assertTrue(provider.deviceOverview(parameters).contains("AND 1=0"));
        assertTrue(provider.channelOverview(parameters).contains("AND 1=0"));
        assertTrue(provider.pushOverview(parameters).contains("AND 1=0"));
        assertTrue(provider.proxyOverview(parameters).contains("AND 1=0"));
    }

    private Map<String, Object> params(VideoScopeQuery scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("scope", scope);
        return params;
    }

    private VideoScopeQuery customScope() {
        return new VideoScopeQuery(
                false,
                Set.of(17L, 29L),
                Set.of(new VideoResourceRef(VideoResourceType.JT_TERMINAL, 31L)),
                Set.of(101L),
                "video:jt:list",
                12L,
                false);
    }

    private VideoScopeQuery allScope(boolean systemUser) {
        return new VideoScopeQuery(
                true, Set.of(), Set.of(), Set.of(),
                "video:cloud-record:list", 12L, systemUser);
    }

    private VideoScopeQuery noneScope(boolean systemUser) {
        return new VideoScopeQuery(
                false, Set.of(), Set.of(), Set.of(),
                "video:cloud-record:list", 12L, systemUser);
    }

    private void assertBoundRoleIds(String sql) {
        assertTrue(sql.contains("collection=\"scope.grantingRoleIds\""));
        assertTrue(sql.contains("#{scopeRoleId}"));
        assertFalse(sql.contains("17"));
        assertFalse(sql.contains("29"));
        assertFalse(sql.contains("${"));
    }

    private void assertChannelMatchers(String sql, String alias) {
        assertTrue(sql.contains("rgi.resource_type = 'CHANNEL'"));
        assertTrue(sql.contains("rgi.resource_id = " + alias + ".id"));
        assertTrue(sql.contains("rgi.resource_type = 'GB_DEVICE'"));
        assertTrue(sql.contains(alias + ".data_type = 1"));
        assertTrue(sql.contains("rgi.resource_type = 'STREAM_PUSH'"));
        assertTrue(sql.contains(alias + ".data_type = 2"));
        assertTrue(sql.contains("rgi.resource_type = 'STREAM_PROXY'"));
        assertTrue(sql.contains(alias + ".data_type = 3"));
    }

    private void assertUnscopedInternalQuery(String sql) {
        assertFalse(sql.contains("wvp_role_resource_group"));
        assertFalse(sql.contains("scope."));
    }
}
