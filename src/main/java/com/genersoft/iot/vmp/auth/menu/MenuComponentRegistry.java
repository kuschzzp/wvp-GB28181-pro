package com.genersoft.iot.vmp.auth.menu;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 后端允许发布给动态路由的前端组件标识。
 *
 * <p>这里保存的是前端显式注册的逻辑名称，不是可拼接的文件路径。</p>
 */
@Component
public class MenuComponentRegistry {

    private static final Set<String> SUPPORTED_COMPONENTS = Set.of(
            "Layout",
            "dashboard/index",
            "live/index",
            "alarm/index",
            "channel/index",
            "channel/group/index",
            "channel/region/index",
            "cloud-record/index",
            "device/index",
            "jt1078/index",
            "media-server/index",
            "operations/log/index",
            "operations/real-log/index",
            "operations/system/index",
            "platform/index",
            "position/index",
            "proxy/index",
            "push/index",
            "record-plan/index",
            "system/dept/index",
            "system/menu/index",
            "system/resource-group/index",
            "system/role/index",
            "system/user/index");

    public boolean supports(String component) {
        return component != null && SUPPORTED_COMPONENTS.contains(component);
    }

    public void requireSupported(String component) {
        if (!supports(component)) {
            throw new IllegalArgumentException("未注册的菜单组件：" + component);
        }
    }
}
