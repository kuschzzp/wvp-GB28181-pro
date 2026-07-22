merge into wvp_dept
    (id, parent_id, dept_name, dept_code, order_num, status, deleted, create_time, update_time)
key (id)
values
    (1, 0, '总部', 'ROOT', 0, 0, 0, current_timestamp, current_timestamp);

merge into wvp_dept_closure (ancestor_id, descendant_id, depth)
key (ancestor_id, descendant_id)
values (1, 1, 0);

merge into wvp_resource_group
    (id, parent_id, group_code, group_name, order_num, status, description,
     create_time, update_time)
key (id)
values
    (1, 0, 'ROOT_VIDEO', '全部视频资源', 0, 0, '系统内置视频授权根组',
     current_timestamp, current_timestamp);

merge into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
key (ancestor_id, descendant_id)
values (1, 1, 0);

merge into wvp_cloud_record
    (id, app, stream, start_time, source_type, source_id, channel_id)
key (id)
values (1, 'legacy', 'unbound', 0, 'LEGACY_UNBOUND', null, null);

merge into wvp_user
    (id, username, password, role_id, dept_id, nickname, status, locked, deleted,
     token_version, password_version, create_time, update_time, push_key)
key (id)
values
    (1, 'admin', '21232f297a57a5a743894a0e4a801fc3', 1, 1, '超级管理员', 0, 0, 0,
     0, 0, timestamp '2021-04-13 14:14:57', timestamp '2021-04-13 14:14:57',
     '3e80d1762a324d5b0ff636e0bd16f1e3');

-- 模拟 2.7.4 升级得到的普通旧角色，用于验证 LEGACY_COMPAT 不会让旧用户失去业务入口。
insert into wvp_user
    (id, username, password, role_id, dept_id, nickname, status, locked, deleted,
     token_version, password_version, create_time, update_time, push_key)
select 99, 'legacy_operator', '4a7d1ed414474e4033ac29ccb8653d9b', 99, 1,
       '旧版操作员', 0, 0, 0, 0, 0, timestamp '2021-04-13 14:14:57',
       timestamp '2021-04-13 14:14:57', 'legacy-push-key'
 where not exists (select 1 from wvp_user where id = 99);

update wvp_user u
   set dept_id = 1
 where u.dept_id is null
    or not exists (select 1 from wvp_dept d where d.id = u.dept_id);

merge into wvp_user_role (id, name, authority, create_time, update_time)
key (id)
values (1, 'admin', '0', '2021-04-13 14:14:57', '2021-04-13 14:14:57');

insert into wvp_user_role (id, name, authority, create_time, update_time)
select 99, 'legacy operator', '1', '2021-04-13 14:14:57', '2021-04-13 14:14:57'
 where not exists (select 1 from wvp_user_role where id = 99);

merge into wvp_role
    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role,
     create_time, update_time)
key (id)
values
    (1, '超级管理员', 'super_admin', 0, 'ALL', 'ALL', 0, 1,
     timestamp '2021-04-13 14:14:57', timestamp '2021-04-13 14:14:57');

insert into wvp_role
    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role,
     create_time, update_time)
select 99, '旧版操作员', 'legacy_role_99', 99, 'NONE', 'NONE', 0, 0,
       timestamp '2021-04-13 14:14:57', timestamp '2021-04-13 14:14:57'
 where not exists (select 1 from wvp_role where id = 99);

merge into wvp_user_role_rel (user_id, role_id) key (user_id, role_id) values (1, 1);
merge into wvp_user_role_rel (user_id, role_id) key (user_id, role_id) values (99, 99);

merge into wvp_menu
    (id, parent_id, menu_name, menu_type, route_name, path, component, perms, icon,
     order_num, visible, status, cacheable, external, system_menu, create_time, update_time)
key (id)
values
    (100, 0, '系统管理', 'M', 'System', '/system', 'Layout', null, 'system', 30, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (110, 100, '用户管理', 'C', 'SystemUser', 'user', 'system/user/index', null, 'user', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (120, 100, '角色管理', 'C', 'SystemRole', 'role', 'system/role/index', null, 'group', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (130, 100, '部门管理', 'C', 'SystemDept', 'dept', 'system/dept/index', null, 'tree', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (140, 100, '菜单管理', 'C', 'SystemMenu', 'menu', 'system/menu/index', null, 'table', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (150, 100, 'API Key', 'C', 'SystemApiKey', 'api-key', 'system/api-key/index', null, 'key', 5, 0, 1, 0, 0, 1, current_timestamp, current_timestamp),
    (160, 100, '视频资源组', 'C', 'SystemResourceGroup', 'resource-group', 'system/resource-group/index', null, 'nested', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (200, 0, '运维管理', 'M', 'Operations', '/operations', 'Layout', null, 'monitor', 20, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (210, 200, '历史日志', 'C', 'OperationsHistoryLog', '/operations/historyLog', 'operations/log/index', null, 'historyLog', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (220, 200, '平台信息', 'C', 'OperationsSystemInfo', '/operations/systemInfo', 'operations/system/index', null, 'systemInfo', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (230, 200, '实时日志', 'C', 'OperationsRealLog', '/operations/realLog', 'operations/real-log/index', null, 'realLog', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (300, 0, '视频管理', 'M', 'Video', '/video', 'Layout', null, 'video-camera', 10, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (310, 300, 'GB设备', 'C', 'Device', '/device', 'device/index', null, 'device', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (320, 300, '通道列表', 'C', 'Channel', '/channel', 'channel/index', null, 'channelManger', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (330, 300, '云台控制', 'C', 'VideoPtz', '/ptz', 'ptz/index', null, 'control', 30, 0, 1, 0, 0, 1, current_timestamp, current_timestamp),
    (340, 300, '设备录像', 'C', 'VideoRecord', '/record', 'record/index', null, 'video-play', 31, 0, 1, 0, 0, 1, current_timestamp, current_timestamp),
    (350, 300, '云端录像', 'C', 'CloudRecord', '/cloudRecord', 'cloud-record/index', null, 'cloudRecord', 13, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (360, 300, '报警管理', 'C', 'AlarmManage', '/alarm', 'alarm/index', null, 'el-icon-bell', 11, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (370, 300, '电子地图', 'C', 'Map', '/map', 'position/index', null, 'map', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (380, 300, '拉流代理', 'C', 'Proxy', '/proxy', 'proxy/index', null, 'streamProxy', 8, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (390, 300, '推流列表', 'C', 'PushList', '/push', 'push/index', null, 'streamPush', 7, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (400, 300, '部标设备', 'C', 'JTDevice', '/jtDevice', 'jt1078/index', null, 'jtDevice', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (410, 300, '国标级联', 'C', 'Platform', '/platform', 'platform/index', null, 'platform', 15, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (420, 300, '媒体节点', 'C', 'MediaServer', '/mediaServer', 'media-server/index', null, 'mediaServerList', 14, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (430, 300, '媒体工具', 'C', 'VideoTools', '/tools', 'tools/index', null, 'tool', 32, 0, 1, 0, 0, 1, current_timestamp, current_timestamp),
    (440, 300, '流分享', 'C', 'VideoShare', '/share', 'share/index', null, 'share', 33, 0, 1, 0, 0, 1, current_timestamp, current_timestamp),
    (450, 300, '控制台', 'C', 'Dashboard', '/dashboard', 'dashboard/index', null, 'dashboard', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (460, 300, '分屏监控', 'C', 'Live', '/live', 'live/index', null, 'live', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (470, 300, '行政区划', 'C', 'Region', '/commonChannel/region', 'channel/region/index', null, 'region', 9, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (480, 300, '业务分组', 'C', 'Group', '/commonChannel/group', 'channel/group/index', null, 'tree', 10, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (490, 300, '录制计划', 'C', 'RecordPlan', '/recordPlan', 'record-plan/index', null, 'recordPlan', 12, 0, 0, 0, 0, 1, current_timestamp, current_timestamp);

merge into wvp_menu
    (id, parent_id, menu_name, menu_type, perms, order_num, visible, status, cacheable,
     external, system_menu, create_time, update_time)
key (id)
values
    (11001, 110, '查询用户', 'F', 'system:user:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (11002, 110, '新增用户', 'F', 'system:user:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (11003, 110, '编辑用户', 'F', 'system:user:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (11004, 110, '删除用户', 'F', 'system:user:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (11005, 110, '重置密码', 'F', 'system:user:resetPwd', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (11006, 110, '变更用户状态', 'F', 'system:user:changeStatus', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12001, 120, '查询角色', 'F', 'system:role:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12002, 120, '新增角色', 'F', 'system:role:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12003, 120, '编辑角色', 'F', 'system:role:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12004, 120, '删除角色', 'F', 'system:role:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12005, 120, '变更角色状态', 'F', 'system:role:changeStatus', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (12006, 120, '分配角色', 'F', 'system:role:assign', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (13001, 130, '查询部门', 'F', 'system:dept:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (13002, 130, '新增部门', 'F', 'system:dept:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (13003, 130, '编辑部门', 'F', 'system:dept:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (13004, 130, '删除部门', 'F', 'system:dept:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (14001, 140, '查询菜单', 'F', 'system:menu:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (14002, 140, '新增菜单', 'F', 'system:menu:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (14003, 140, '编辑菜单', 'F', 'system:menu:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (14004, 140, '删除菜单', 'F', 'system:menu:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15001, 150, '查询API Key', 'F', 'system:apikey:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15002, 150, '新增API Key', 'F', 'system:apikey:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15003, 150, '轮换API Key', 'F', 'system:apikey:rotate', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15004, 150, '停用API Key', 'F', 'system:apikey:disable', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15005, 150, '删除API Key', 'F', 'system:apikey:remove', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (15006, 150, '管理他人API Key', 'F', 'system:apikey:manageOthers', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (16001, 160, '查询资源组', 'F', 'system:resource-group:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (16002, 160, '新增资源组', 'F', 'system:resource-group:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (16003, 160, '编辑资源组', 'F', 'system:resource-group:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (16004, 160, '删除资源组', 'F', 'system:resource-group:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (16005, 160, '分配资源组', 'F', 'system:resource-group:assign', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (21001, 210, '查看日志', 'F', 'operations:log:view', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (21002, 210, '下载日志', 'F', 'operations:log:download', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (22001, 220, '查看平台状态', 'F', 'operations:system:view', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31001, 310, '查询GB设备', 'F', 'video:device:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31002, 310, '新增GB设备', 'F', 'video:device:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31003, 310, '编辑GB设备', 'F', 'video:device:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31004, 310, '删除GB设备', 'F', 'video:device:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31005, 310, '同步GB设备', 'F', 'video:device:sync', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (31006, 310, '配置GB设备', 'F', 'video:device:config', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32001, 320, '查询通道', 'F', 'video:channel:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32002, 320, '编辑通道', 'F', 'video:channel:edit', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32003, 320, '播放通道', 'F', 'video:channel:play', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32004, 320, '停止通道', 'F', 'video:channel:stop', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32005, 320, '通道对讲', 'F', 'video:channel:talk', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (32006, 320, '通道广播', 'F', 'video:channel:broadcast', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (33001, 330, '云台控制', 'F', 'video:ptz:control', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (33002, 330, '预置位', 'F', 'video:ptz:preset', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (33003, 330, '巡航', 'F', 'video:ptz:cruise', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (34001, 340, '查询设备录像', 'F', 'video:record:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (34002, 340, '播放设备录像', 'F', 'video:record:play', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (34003, 340, '下载设备录像', 'F', 'video:record:download', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (34004, 340, '删除设备录像', 'F', 'video:record:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (35001, 350, '查询云端录像', 'F', 'video:cloud-record:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (35002, 350, '播放云端录像', 'F', 'video:cloud-record:play', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (35003, 350, '下载云端录像', 'F', 'video:cloud-record:download', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (35004, 350, '删除云端录像', 'F', 'video:cloud-record:remove', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (35005, 350, '收藏云端录像', 'F', 'video:cloud-record:collect', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (36001, 360, '查询告警', 'F', 'video:alarm:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (36002, 360, '删除告警', 'F', 'video:alarm:remove', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (36003, 360, '清空告警', 'F', 'video:alarm:clear', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (37001, 370, '查询位置', 'F', 'video:position:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (37002, 370, '订阅位置', 'F', 'video:position:subscribe', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38001, 380, '查询拉流代理', 'F', 'video:proxy:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38002, 380, '新增拉流代理', 'F', 'video:proxy:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38003, 380, '编辑拉流代理', 'F', 'video:proxy:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38004, 380, '播放拉流代理', 'F', 'video:proxy:play', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38005, 380, '停止拉流代理', 'F', 'video:proxy:stop', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (38006, 380, '删除拉流代理', 'F', 'video:proxy:remove', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39001, 390, '查询推流', 'F', 'video:push:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39002, 390, '新增推流', 'F', 'video:push:add', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39003, 390, '编辑推流', 'F', 'video:push:edit', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39004, 390, '播放推流', 'F', 'video:push:play', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39005, 390, '停止推流', 'F', 'video:push:stop', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (39006, 390, '删除推流', 'F', 'video:push:remove', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40001, 400, '查询JT1078', 'F', 'video:jt:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40002, 400, '编辑JT1078', 'F', 'video:jt:edit', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40003, 400, '播放JT1078', 'F', 'video:jt:play', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40004, 400, 'JT1078对讲', 'F', 'video:jt:talk', 4, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40005, 400, 'JT1078云台', 'F', 'video:jt:ptz', 5, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40006, 400, 'JT1078录像', 'F', 'video:jt:record', 6, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (40007, 400, 'JT1078控制', 'F', 'video:jt:control', 7, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (41001, 410, '查询国标级联', 'F', 'video:platform:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (41002, 410, '管理国标级联', 'F', 'video:platform:manage', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (41003, 410, '共享通道', 'F', 'video:platform:share-channel', 3, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (42001, 420, '查询媒体节点', 'F', 'video:media-server:list', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (42002, 420, '管理媒体节点', 'F', 'video:media-server:manage', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (43001, 430, '管理RTP工具', 'F', 'video:rtp:manage', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (43002, 430, '管理PS工具', 'F', 'video:ps:manage', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (44001, 440, '创建流分享', 'F', 'video:stream:share', 1, 0, 0, 0, 0, 1, current_timestamp, current_timestamp),
    (44002, 440, '撤销流分享', 'F', 'video:stream:revoke-share', 2, 0, 0, 0, 0, 1, current_timestamp, current_timestamp);

-- update_by 为空表示管理员尚未接管该迁移角色；第一次保存角色会替换这些关系并退出兼容态。
merge into wvp_role_menu (role_id, menu_id)
key (role_id, menu_id)
select r.id, m.id
  from wvp_role r
 cross join wvp_menu m
 where regexp_like(r.role_key, '^legacy_role_[0-9]+$')
   and r.system_role = 0
   and r.update_by is null
   and m.status = 0
   and (
       m.id in (100, 110, 200, 300)
       or m.perms in (
           'system:user:list', 'system:apikey:list', 'system:apikey:add',
           'system:apikey:rotate', 'system:apikey:disable', 'system:apikey:remove'
       )
       or m.parent_id in (200, 300)
       or exists (
           select 1
             from wvp_menu parent_menu
            where parent_menu.id = m.parent_id
              and parent_menu.parent_id in (200, 300)
       )
   );

-- H2 的显式 identity 插入不会自动推进序列；保持下一次业务插入大于种子最大 ID。
alter table wvp_dept alter column id restart with 2;
alter table wvp_user alter column id restart with 100;
alter table wvp_user_role alter column id restart with 100;
alter table wvp_user_api_key alter column id restart with 1;
alter table wvp_role alter column id restart with 100;
alter table wvp_resource_group alter column id restart with 2;
alter table wvp_resource_group_item alter column id restart with 1;
alter table wvp_cloud_record alter column id restart with 2;
alter table wvp_menu alter column id restart with 44003;
alter table wvp_authz_audit alter column id restart with 1;
