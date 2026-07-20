package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ResourceGroupController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(ResourceGroupControllerTest.MethodSecurityConfiguration.class)
@ActiveProfiles("test")
class ResourceGroupControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ResourceGroupService resourceGroupService;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean(name = "authz") private PermissionEnforcer authz;

    @Test
    void mutationUsesAlwaysEnforcePermissionAndNeverFallsBackToAuditCheck() throws Exception {
        when(authz.require("system:resource-group:add")).thenReturn(false);

        mockMvc.perform(post("/api/system/resource-group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody()))
                .andExpect(status().isForbidden());

        verify(authz).require("system:resource-group:add");
        verify(authz, never()).check("system:resource-group:add");
        verifyNoInteractions(resourceGroupService, currentPrincipalService);
    }

    @Test
    void createUsesAuthenticatedUserAsTheAuditOperator() throws Exception {
        when(authz.require("system:resource-group:add")).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal());
        when(resourceGroupService.create(org.mockito.ArgumentMatchers.any())).thenReturn(101L);

        mockMvc.perform(post("/api/system/resource-group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101));

        verify(resourceGroupService).create(argThat(command -> command.parentId() == 1L
                && "BUILDING_1".equals(command.groupCode())
                && command.operatorId() == 12L));
    }

    @Test
    void treeIsProtectedByTheDedicatedListPermission() throws Exception {
        when(authz.require("system:resource-group:list")).thenReturn(true);
        when(resourceGroupService.tree()).thenReturn(List.of(new ResourceGroupTreeNode(
                1L, 0L, "ROOT_VIDEO", "全部视频资源", 0, 0, null, List.of())));

        mockMvc.perform(get("/api/system/resource-group/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].groupCode").value("ROOT_VIDEO"));
    }

    @Test
    void assigningItemsUsesAssignPermissionAndStableInternalIds() throws Exception {
        when(authz.require("system:resource-group:assign")).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal());

        mockMvc.perform(put("/api/system/resource-group/100/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"type":"GB_DEVICE","id":11},
                                    {"type":"CHANNEL","id":101}
                                  ]
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(resourceGroupService).replaceItems(
                100L,
                Set.of(
                        new VideoResourceRef(VideoResourceType.GB_DEVICE, 11L),
                        new VideoResourceRef(VideoResourceType.CHANNEL, 101L)),
                12L);
    }

    @Test
    void assigningRoleGroupsUsesCustomScopeAndAssignPermission() throws Exception {
        when(authz.require("system:resource-group:assign")).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal());

        mockMvc.perform(put("/api/system/resource-group/role/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "videoScopeType":"CUSTOM",
                                  "resourceGroupIds":[100,101]
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(resourceGroupService).replaceRoleGroups(
                7L, VideoScopeType.CUSTOM, Set.of(100L, 101L), 12L);
    }

    private String groupBody() {
        return """
                {
                  "parentId":1,
                  "groupCode":"BUILDING_1",
                  "groupName":"一号楼",
                  "orderNum":10,
                  "status":0,
                  "description":"一号楼摄像头"
                }
                """;
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "resource-admin",
                10L,
                Set.of(7L),
                0L,
                false,
                Set.of(
                        "system:resource-group:list",
                        "system:resource-group:add",
                        "system:resource-group:assign"));
    }

    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
