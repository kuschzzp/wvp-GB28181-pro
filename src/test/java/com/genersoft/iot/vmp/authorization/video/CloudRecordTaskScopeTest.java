package com.genersoft.iot.vmp.authorization.video;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.zlm.AssistRESTfulUtils;
import com.genersoft.iot.vmp.service.impl.CloudRecordServiceImpl;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudRecordTaskScopeTest {

    @Mock private CloudRecordServiceMapper cloudRecordServiceMapper;
    @Mock private IMediaServerService mediaServerService;
    @Mock private AssistRESTfulUtils assistRESTfulUtils;

    @Test
    void taskListDropsRowsWhoseRecordSourceIsOutsideTheCurrentScope() {
        CloudRecordServiceImpl service = new CloudRecordServiceImpl();
        ReflectionTestUtils.setField(
                service, "cloudRecordServiceMapper", cloudRecordServiceMapper);
        ReflectionTestUtils.setField(
                service, "mediaServerService", mediaServerService);
        ReflectionTestUtils.setField(
                service, "assistRESTfulUtils", assistRESTfulUtils);

        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("media-a");
        when(mediaServerService.getDefaultMediaServer()).thenReturn(mediaServer);

        JSONObject response = new JSONObject();
        response.put("code", 0);
        JSONArray tasks = new JSONArray();
        tasks.add(task("live", "allowed", "call-a"));
        tasks.add(task("live", "denied", "call-b"));
        tasks.add(task(null, null, null));
        response.put("data", tasks);
        when(assistRESTfulUtils.queryTaskList(
                mediaServer, null, null, null, null, null, "http"))
                .thenReturn(response);

        VideoScopeQuery scope = new VideoScopeQuery(
                false,
                Set.of(17L),
                Set.of(new VideoResourceRef(VideoResourceType.GB_DEVICE, 11L)),
                Set.of(),
                "video:cloud-record:list",
                9L,
                false);
        when(cloudRecordServiceMapper.countAuthorizedTaskSources(
                "live", "allowed", "call-a", scope)).thenReturn(1);
        when(cloudRecordServiceMapper.countAuthorizedTaskSources(
                "live", "denied", "call-b", scope)).thenReturn(0);

        JSONArray result = service.queryTask(
                null, null, null, null, null, null, "http", scope);

        assertEquals(1, result.size());
        assertEquals("allowed", result.getJSONObject(0).getString("stream"));
    }

    private JSONObject task(String app, String stream, String callId) {
        JSONObject task = new JSONObject();
        task.put("app", app);
        task.put("stream", stream);
        task.put("callId", callId);
        return task;
    }
}
