-- WVP 2.8.0 身份与功能 RBAC 初始化结构（MySQL 8.0+）
-- 业务媒体表继续沿用 2.7.4 初始化结构；本文件替换其中的用户、角色、API Key 部分并新增 RBAC 表。
set names utf8mb4;

delimiter //
drop procedure if exists wvp_add_column_if_missing//
create procedure wvp_add_column_if_missing(
    in target_table varchar(64),
    in target_column varchar(64),
    in column_definition text
)
begin
    if not exists (
        select 1
          from information_schema.columns
         where table_schema = database()
           and table_name = target_table
           and column_name = target_column
    ) then
        set @wvp_add_column_sql = concat(
            'alter table `', replace(target_table, '`', '``'),
            '` add column `', replace(target_column, '`', '``'), '` ', column_definition
        );
        prepare wvp_add_column_stmt from @wvp_add_column_sql;
        execute wvp_add_column_stmt;
        deallocate prepare wvp_add_column_stmt;
    end if;
end//
delimiter ;

call wvp_add_column_if_missing('wvp_media_server', 'hook_secret_hash', 'varchar(64) null');
call wvp_add_column_if_missing('wvp_media_server', 'previous_hook_secret_hash', 'varchar(64) null');
call wvp_add_column_if_missing('wvp_media_server', 'previous_hook_secret_expires_at', 'timestamp null');
call wvp_add_column_if_missing('wvp_media_server', 'hook_secret_version', 'bigint not null default 0');
call wvp_add_column_if_missing('wvp_media_server', 'hook_allowed_cidrs', 'varchar(2000) null');
call wvp_add_column_if_missing('wvp_media_server', 'hook_auth_enabled', 'boolean not null default true');

create table if not exists wvp_dept
(
    id             bigint not null auto_increment,
    parent_id      bigint not null default 0,
    dept_name      varchar(64) not null,
    dept_code      varchar(64) not null,
    order_num      integer not null default 0,
    leader_user_id bigint,
    status         smallint not null default 0,
    deleted        smallint not null default 0,
    create_by      bigint,
    create_time    timestamp not null default current_timestamp,
    update_by      bigint,
    update_time    timestamp not null default current_timestamp,
    primary key (id),
    unique key uk_wvp_dept_code (dept_code),
    index idx_wvp_dept_parent (parent_id, status, deleted),
    constraint ck_wvp_dept_status check (status in (0, 1)),
    constraint ck_wvp_dept_deleted check (deleted in (0, 1))
) engine = InnoDB default charset = utf8mb4 comment = '组织部门';

create table if not exists wvp_dept_closure
(
    ancestor_id   bigint not null,
    descendant_id bigint not null,
    depth          integer not null,
    primary key (ancestor_id, descendant_id),
    index idx_wvp_dept_closure_desc (descendant_id, ancestor_id),
    constraint ck_wvp_dept_closure_depth check (depth >= 0),
    constraint fk_wvp_dept_closure_ancestor foreign key (ancestor_id)
        references wvp_dept (id) on delete cascade,
    constraint fk_wvp_dept_closure_descendant foreign key (descendant_id)
        references wvp_dept (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '部门闭包关系';

create table if not exists wvp_user
(
    id               bigint not null auto_increment comment '主键ID',
    username         varchar(255) not null comment '用户名',
    password         varchar(255) not null comment '带版本的密码摘要',
    role_id          bigint comment '兼容旧版本的主角色ID',
    dept_id          bigint not null default 1 comment '主部门ID',
    nickname         varchar(64) comment '显示名称',
    status           smallint not null default 0 comment '0正常，1停用',
    locked           smallint not null default 0 comment '0未锁定，1锁定',
    deleted          smallint not null default 0 comment '逻辑删除',
    token_version    bigint not null default 0 comment '认证授权版本',
    password_version smallint not null default 0 comment '0旧MD5，1BCrypt',
    last_login_at    timestamp null comment '最近登录时间',
    create_by        bigint comment '创建人',
    update_by        bigint comment '更新人',
    create_time      timestamp not null default current_timestamp comment '创建时间',
    update_time      timestamp not null default current_timestamp comment '更新时间',
    push_key         varchar(50) comment '推送密钥',
    primary key (id),
    constraint uk_user_username unique (username),
    constraint fk_wvp_user_dept foreign key (dept_id) references wvp_dept (id),
    index idx_user_dept_status (dept_id, status, deleted)
) engine = InnoDB default charset = utf8mb4 comment = '平台用户';

-- expand 阶段保留旧角色主表，供 2.7.4 应用回退。
create table if not exists wvp_user_role
(
    id          bigint not null auto_increment comment '主键ID',
    name        varchar(64) comment '旧角色名称',
    authority   varchar(64) comment '旧权限标识，不再参与新授权',
    create_time varchar(50) comment '创建时间',
    update_time varchar(50) comment '更新时间',
    primary key (id)
) engine = InnoDB default charset = utf8mb4 comment = '2.7.4兼容角色主表';

create table if not exists wvp_user_api_key
(
    id            bigint not null auto_increment comment '主键ID',
    user_id       bigint not null comment '所属用户ID',
    app           varchar(255) comment '应用标识',
    api_key       text comment '仅供2.7.4迁移的旧明文Key',
    key_hash      varchar(64) comment 'HMAC-SHA256',
    key_prefix    varchar(16) comment '展示前缀',
    key_last4     varchar(4) comment '展示尾号',
    expired_at    bigint comment '绝对UTC epoch millis',
    remark        varchar(255) comment '备注',
    status        varchar(16) not null default 'ACTIVE' comment 'ACTIVE/DISABLED/REVOKED/LEGACY',
    ip_allowlist  varchar(2000) comment '可选IPv4/IPv6 CIDR白名单，逗号分隔',
    authz_version bigint not null default 0 comment '创建或轮换时用户tokenVersion',
    last_used_at  timestamp null comment '最近使用时间',
    create_time   varchar(50) comment '创建时间',
    update_time   varchar(50) comment '更新时间',
    primary key (id),
    unique key uk_api_key_hash (key_hash),
    index idx_api_key_user (user_id, status),
    constraint ck_api_key_status check (status in ('ACTIVE', 'DISABLED', 'REVOKED', 'LEGACY'))
) engine = InnoDB default charset = utf8mb4 comment = '用户API Key';

create table if not exists wvp_role
(
    id               bigint not null auto_increment,
    name             varchar(64) not null,
    role_key         varchar(64) not null,
    role_sort        integer not null default 0,
    dept_data_scope  varchar(24) not null default 'NONE',
    video_scope_type varchar(16) not null default 'NONE',
    status           smallint not null default 0,
    system_role      smallint not null default 0,
    create_by        bigint,
    create_time      timestamp not null default current_timestamp,
    update_by        bigint,
    update_time      timestamp not null default current_timestamp,
    primary key (id),
    constraint uk_wvp_role_key unique (role_key),
    constraint ck_wvp_role_dept_scope check (dept_data_scope in ('ALL', 'CUSTOM', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'NONE')),
    constraint ck_wvp_role_video_scope check (video_scope_type in ('ALL', 'CUSTOM', 'NONE')),
    constraint ck_wvp_role_status check (status in (0, 1)),
    constraint ck_wvp_role_system check (system_role in (0, 1))
) engine = InnoDB default charset = utf8mb4 comment = 'RBAC角色';

create table if not exists wvp_resource_group
(
    id          bigint not null auto_increment,
    parent_id   bigint not null default 0,
    group_code  varchar(64) not null,
    group_name  varchar(64) not null,
    order_num   integer not null default 0,
    status      smallint not null default 0,
    description varchar(500),
    create_by   bigint,
    create_time timestamp not null default current_timestamp,
    update_by   bigint,
    update_time timestamp not null default current_timestamp,
    primary key (id),
    unique key uk_wvp_resource_group_code (group_code),
    index idx_wvp_resource_group_parent (parent_id, status, order_num),
    constraint ck_wvp_resource_group_status check (status in (0, 1))
) engine = InnoDB default charset = utf8mb4 comment = '视频授权资源组';

create table if not exists wvp_resource_group_closure
(
    ancestor_id   bigint not null,
    descendant_id bigint not null,
    depth         integer not null,
    primary key (ancestor_id, descendant_id),
    index idx_wvp_resource_group_closure_desc (descendant_id, ancestor_id),
    constraint ck_wvp_resource_group_closure_depth check (depth >= 0),
    constraint fk_wvp_resource_group_closure_ancestor foreign key (ancestor_id)
        references wvp_resource_group (id) on delete cascade,
    constraint fk_wvp_resource_group_closure_descendant foreign key (descendant_id)
        references wvp_resource_group (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '视频资源组闭包关系';

create table if not exists wvp_resource_group_item
(
    id            bigint not null auto_increment,
    group_id      bigint not null,
    resource_type varchar(32) not null,
    resource_id   bigint not null,
    create_by     bigint,
    create_time   timestamp not null default current_timestamp,
    primary key (id),
    unique key uk_wvp_resource_group_item (group_id, resource_type, resource_id),
    index idx_wvp_resource_item_ref (resource_type, resource_id),
    constraint ck_wvp_resource_group_item_type check (
        resource_type in ('GB_DEVICE', 'CHANNEL', 'JT_TERMINAL', 'JT_CHANNEL',
                          'STREAM_PROXY', 'STREAM_PUSH')
    ),
    constraint fk_wvp_resource_group_item_group foreign key (group_id)
        references wvp_resource_group (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '视频资源组资源项';

create table if not exists wvp_role_resource_group
(
    role_id           bigint not null,
    resource_group_id bigint not null,
    primary key (role_id, resource_group_id),
    index idx_wvp_role_resource_group_group (resource_group_id, role_id),
    constraint fk_wvp_role_resource_group_role foreign key (role_id)
        references wvp_role (id) on delete cascade,
    constraint fk_wvp_role_resource_group_group foreign key (resource_group_id)
        references wvp_resource_group (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '角色视频资源组关系';

insert into wvp_resource_group
    (id, parent_id, group_code, group_name, order_num, status, description,
     create_time, update_time)
values
    (1, 0, 'ROOT_VIDEO', '全部视频资源', 0, 0, '系统内置视频授权根组',
     current_timestamp, current_timestamp)
on duplicate key update
    group_name = values(group_name), status = 0;

insert ignore into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
values (1, 1, 0);

call wvp_add_column_if_missing(
    'wvp_cloud_record', 'source_type',
    'varchar(32) not null default ''LEGACY_UNBOUND'' comment ''录像来源类型''');
call wvp_add_column_if_missing(
    'wvp_cloud_record', 'source_id', 'bigint null comment ''录像来源内部主键''');
call wvp_add_column_if_missing(
    'wvp_cloud_record', 'channel_id', 'bigint null comment ''统一通道内部主键''');
call wvp_add_column_if_missing(
    'wvp_cloud_record', 'source_reason', 'varchar(64) null comment ''录像来源解析结果''');
drop procedure wvp_add_column_if_missing;
update wvp_cloud_record
   set source_type = 'LEGACY_UNBOUND'
 where source_type is null or source_type = '';

set @wvp_index_sql = (
    select if(count(*) = 0,
              'create index idx_wvp_cloud_record_channel on wvp_cloud_record (channel_id, start_time)',
              'select 1')
      from information_schema.statistics
     where table_schema = database()
       and table_name = 'wvp_cloud_record'
       and index_name = 'idx_wvp_cloud_record_channel'
);
prepare wvp_index_stmt from @wvp_index_sql;
execute wvp_index_stmt;
deallocate prepare wvp_index_stmt;

create table if not exists wvp_alarm (
    id serial primary key comment '主键ID',
    channel_id integer comment '关联通道的数据库id',
    description character varying(255) comment '报警描述',
    snap_path character varying(255) comment '报警快照路径',
    record_path character varying(255) comment '报警录像路径',
    longitude double precision comment '报警附带的经度',
    latitude double precision comment '报警附带的纬度',
    alarm_type integer comment '报警类别',
    alarm_time bigint comment '报警时间'
);


create table if not exists wvp_role_dept
(
    role_id bigint not null,
    dept_id bigint not null,
    primary key (role_id, dept_id),
    index idx_wvp_role_dept_dept (dept_id, role_id),
    constraint fk_wvp_role_dept_role foreign key (role_id)
        references wvp_role (id) on delete cascade,
    constraint fk_wvp_role_dept_dept foreign key (dept_id)
        references wvp_dept (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '角色自定义部门范围';

create table if not exists wvp_user_role_rel
(
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id),
    index idx_user_role_rel_role (role_id, user_id),
    constraint fk_user_role_rel_user foreign key (user_id) references wvp_user (id) on delete cascade,
    constraint fk_user_role_rel_role foreign key (role_id) references wvp_role (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = 'expand阶段用户角色关系';

create table if not exists wvp_menu
(
    id          bigint not null auto_increment,
    parent_id   bigint not null default 0,
    menu_name   varchar(64) not null,
    menu_type   char(1) not null,
    route_name  varchar(64),
    path        varchar(255),
    component   varchar(255),
    perms       varchar(128),
    icon        varchar(100),
    order_num   integer not null default 0,
    visible     smallint not null default 0,
    status      smallint not null default 0,
    cacheable   smallint not null default 0,
    external    smallint not null default 0,
    system_menu smallint not null default 0,
    create_by   bigint,
    create_time timestamp not null default current_timestamp,
    update_by   bigint,
    update_time timestamp not null default current_timestamp,
    primary key (id),
    constraint uk_wvp_menu_perms unique (perms),
    constraint ck_wvp_menu_type check (menu_type in ('M', 'C', 'F')),
    index idx_menu_parent (parent_id, status, visible)
) engine = InnoDB default charset = utf8mb4 comment = '菜单和功能权限';

create table if not exists wvp_role_menu
(
    role_id bigint not null,
    menu_id bigint not null,
    primary key (role_id, menu_id),
    index idx_role_menu_menu (menu_id, role_id),
    constraint fk_role_menu_role foreign key (role_id) references wvp_role (id) on delete cascade,
    constraint fk_role_menu_menu foreign key (menu_id) references wvp_menu (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = '角色菜单关系';

create table if not exists wvp_api_key_permission
(
    api_key_id bigint not null,
    menu_id    bigint not null,
    primary key (api_key_id, menu_id),
    index idx_api_key_permission_menu (menu_id, api_key_id),
    constraint fk_api_key_permission_key foreign key (api_key_id)
        references wvp_user_api_key (id) on delete cascade,
    constraint fk_api_key_permission_menu foreign key (menu_id)
        references wvp_menu (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = 'API Key功能权限范围';

create table if not exists wvp_api_key_resource_group
(
    api_key_id       bigint not null,
    resource_group_id bigint not null,
    primary key (api_key_id, resource_group_id),
    index idx_api_key_resource_group_group (resource_group_id, api_key_id),
    constraint fk_api_key_resource_group_key foreign key (api_key_id)
        references wvp_user_api_key (id) on delete cascade,
    constraint fk_api_key_resource_group_group foreign key (resource_group_id)
        references wvp_resource_group (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = 'API Key视频资源组范围';

create table if not exists wvp_authz_audit
(
    id                bigint not null auto_increment,
    principal_type    varchar(16) not null,
    principal_id      bigint not null,
    principal_dept_id bigint,
    permission_code   varchar(128),
    resource_type     varchar(32),
    resource_id       bigint,
    decision          varchar(8) not null,
    reason_code       varchar(64) not null,
    request_id        varchar(64),
    client_ip         varchar(64),
    create_time       timestamp not null default current_timestamp,
    primary key (id),
    constraint ck_authz_audit_decision check (decision in ('ALLOW', 'DENY')),
    index idx_authz_audit_principal (principal_type, principal_id, create_time),
    index idx_authz_audit_dept (principal_dept_id, create_time),
    index idx_authz_audit_resource (resource_type, resource_id, create_time)
) engine = InnoDB default charset = utf8mb4 comment = '授权决策审计';

create table if not exists wvp_stream_share
(
    share_id        varchar(64) primary key,
    creator_user_id bigint not null,
    resource_type   varchar(32) not null,
    resource_id     bigint not null,
    expires_at      timestamp not null,
    max_views       bigint,
    view_count      bigint not null default 0,
    passcode_hash   varchar(100),
    status          varchar(16) not null,
    authz_version   bigint not null,
    create_time     timestamp not null default current_timestamp,
    update_time     timestamp not null default current_timestamp,
    constraint ck_stream_share_status check (status in ('ACTIVE', 'REVOKED')),
    constraint ck_stream_share_views check (
        view_count >= 0 and (max_views is null or max_views > 0)
    ),
    constraint fk_stream_share_creator foreign key (creator_user_id)
        references wvp_user (id) on delete cascade,
    index idx_stream_share_creator (creator_user_id, status, expires_at),
    index idx_stream_share_resource (resource_type, resource_id)
) engine = InnoDB default charset = utf8mb4 comment = '可撤销媒体流分享';

insert into wvp_dept
    (id, parent_id, dept_name, dept_code, order_num, status, deleted, create_time, update_time)
values
    (1, 0, '总部', 'ROOT', 0, 0, 0, current_timestamp, current_timestamp)
on duplicate key update
    dept_name = values(dept_name), status = 0, deleted = 0;

insert ignore into wvp_dept_closure (ancestor_id, descendant_id, depth)
values (1, 1, 0);

insert into wvp_user
    (id, username, password, role_id, dept_id, nickname, status, locked, deleted, token_version,
     password_version, create_time, update_time, push_key)
values
    (1, 'admin', '21232f297a57a5a743894a0e4a801fc3', 1, 1, '超级管理员', 0, 0, 0, 0, 0,
     '2021-04-13 14:14:57', '2021-04-13 14:14:57', '3e80d1762a324d5b0ff636e0bd16f1e3')
on duplicate key update username = values(username), dept_id = 1;

insert into wvp_user_role (id, name, authority, create_time, update_time)
values (1, 'admin', '0', '2021-04-13 14:14:57', '2021-04-13 14:14:57')
on duplicate key update name = values(name);

insert into wvp_role
    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role,
     create_time, update_time)
values
    (1, '超级管理员', 'super_admin', 0, 'ALL', 'ALL', 0, 1,
     '2021-04-13 14:14:57', '2021-04-13 14:14:57')
on duplicate key update system_role = 1, dept_data_scope = 'ALL', video_scope_type = 'ALL';

insert ignore into wvp_user_role_rel (user_id, role_id) values (1, 1);

insert ignore into wvp_menu
    (id, parent_id, menu_name, menu_type, route_name, path, component, icon, order_num,
     visible, status, cacheable, external, system_menu)
values
    (100, 0, '系统管理', 'M', 'System', '/system', 'Layout', 'system', 10, 0, 0, 0, 0, 1),
    (110, 100, '用户管理', 'C', 'SystemUser', 'user', 'system/user/index', 'user', 1, 0, 0, 0, 0, 1),
    (120, 100, '角色管理', 'C', 'SystemRole', 'role', 'system/role/index', 'group', 2, 0, 0, 0, 0, 1),
    (130, 100, '部门管理', 'C', 'SystemDept', 'dept', 'system/dept/index', 'tree', 3, 0, 0, 0, 0, 1),
    (140, 100, '菜单管理', 'C', 'SystemMenu', 'menu', 'system/menu/index', 'table', 4, 0, 0, 0, 0, 1),
    (150, 100, 'API Key', 'C', 'SystemApiKey', 'api-key', 'system/api-key/index', 'key', 5, 0, 1, 0, 0, 1),
    (160, 100, '视频资源组', 'C', 'SystemResourceGroup', 'resource-group', 'system/resource-group/index', 'nested', 6, 0, 0, 0, 0, 1),
    (200, 0, '运维管理', 'M', 'Operations', '/operations', 'Layout', 'monitor', 20, 0, 0, 0, 0, 1),
    (210, 200, '操作日志', 'C', 'OperationsLog', 'log', 'operations/log/index', 'documentation', 1, 0, 0, 0, 0, 1),
    (220, 200, '平台状态', 'C', 'OperationsSystem', 'system', 'operations/system/index', 'dashboard', 2, 0, 0, 0, 0, 1),
    (300, 0, '视频管理', 'M', 'Video', '/video', 'Layout', 'video-camera', 30, 0, 0, 0, 0, 1),
    (310, 300, 'GB设备', 'C', 'VideoDevice', 'device', 'device/index', 'device', 1, 0, 0, 0, 0, 1),
    (320, 300, '通道', 'C', 'VideoChannel', 'channel', 'channel/index', 'list', 2, 0, 0, 0, 0, 1),
    (330, 300, '云台控制', 'C', 'VideoPtz', 'ptz', 'ptz/index', 'control', 3, 0, 0, 0, 0, 1),
    (340, 300, '设备录像', 'C', 'VideoRecord', 'record', 'record/index', 'video-play', 4, 0, 0, 0, 0, 1),
    (350, 300, '云端录像', 'C', 'VideoCloudRecord', 'cloud-record', 'cloud-record/index', 'cloudy', 5, 0, 0, 0, 0, 1),
    (360, 300, '告警', 'C', 'VideoAlarm', 'alarm', 'alarm/index', 'warning', 6, 0, 0, 0, 0, 1),
    (370, 300, '移动位置', 'C', 'VideoPosition', 'position', 'position/index', 'location', 7, 0, 0, 0, 0, 1),
    (380, 300, '拉流代理', 'C', 'VideoProxy', 'proxy', 'proxy/index', 'link', 8, 0, 0, 0, 0, 1),
    (390, 300, '推流', 'C', 'VideoPush', 'push', 'push/index', 'upload', 9, 0, 0, 0, 0, 1),
    (400, 300, 'JT1078', 'C', 'VideoJt', 'jt1078', 'jt1078/index', 'car', 10, 0, 0, 0, 0, 1),
    (410, 300, '国标级联', 'C', 'VideoPlatform', 'platform', 'platform/index', 'connection', 11, 0, 0, 0, 0, 1),
    (420, 300, '媒体节点', 'C', 'VideoMediaServer', 'media-server', 'media-server/index', 'server', 12, 0, 0, 0, 0, 1),
    (430, 300, '媒体工具', 'C', 'VideoTools', 'tools', 'tools/index', 'tool', 13, 0, 0, 0, 0, 1),
    (440, 300, '流分享', 'C', 'VideoShare', 'share', 'share/index', 'share', 14, 0, 0, 0, 0, 1),
    (230, 200, '实时日志', 'C', 'OperationsRealLog', '/operations/realLog', 'operations/real-log/index', 'realLog', 3, 0, 0, 0, 0, 1),
    (450, 300, '控制台', 'C', 'Dashboard', '/dashboard', 'dashboard/index', 'dashboard', 1, 0, 0, 0, 0, 1),
    (460, 300, '分屏监控', 'C', 'Live', '/live', 'live/index', 'live', 2, 0, 0, 0, 0, 1),
    (470, 300, '行政区划', 'C', 'Region', '/commonChannel/region', 'channel/region/index', 'region', 9, 0, 0, 0, 0, 1),
    (480, 300, '业务分组', 'C', 'Group', '/commonChannel/group', 'channel/group/index', 'tree', 10, 0, 0, 0, 0, 1),
    (490, 300, '录制计划', 'C', 'RecordPlan', '/recordPlan', 'record-plan/index', 'recordPlan', 12, 0, 0, 0, 0, 1);

update wvp_menu
set route_name = case id
        when 110 then 'User' when 210 then 'OperationsHistoryLog' when 220 then 'OperationsSystemInfo'
        when 310 then 'Device' when 320 then 'Channel' when 350 then 'CloudRecord'
        when 360 then 'AlarmManage' when 370 then 'Map' when 380 then 'Proxy'
        when 390 then 'PushList' when 400 then 'JTDevice' when 410 then 'Platform'
        when 420 then 'MediaServer' else route_name end,
    path = case id
        when 110 then '/user' when 210 then '/operations/historyLog' when 220 then '/operations/systemInfo'
        when 310 then '/device' when 320 then '/channel' when 350 then '/cloudRecord'
        when 360 then '/alarm' when 370 then '/map' when 380 then '/proxy'
        when 390 then '/push' when 400 then '/jtDevice' when 410 then '/platform'
        when 420 then '/mediaServer' else path end,
    icon = case id
        when 210 then 'historyLog' when 220 then 'systemInfo' when 310 then 'device'
        when 320 then 'channelManger' when 350 then 'cloudRecord' when 360 then 'el-icon-bell'
        when 370 then 'map' when 380 then 'streamProxy' when 390 then 'streamPush'
        when 400 then 'jtDevice' when 410 then 'platform' when 420 then 'mediaServerList'
        else icon end,
    order_num = case id
        when 100 then 30 when 200 then 20 when 300 then 10 when 310 then 5 when 320 then 3
        when 350 then 13 when 360 then 11 when 370 then 4 when 380 then 8 when 390 then 7
        when 400 then 6 when 410 then 15 when 420 then 14 else order_num end,
    status = case when id in (150, 330, 340, 430, 440) then 1 else status end
where system_menu = 1
  and id in (100, 110, 120, 130, 140, 150, 160, 200, 210, 220, 300, 310, 320,
             330, 340, 350, 360, 370, 380, 390, 400, 410, 420, 430, 440);

drop temporary table if exists wvp_permission_seed;
create temporary table wvp_permission_seed
(
    parent_id       bigint not null,
    permission_code varchar(128) not null primary key
);
insert into wvp_permission_seed (parent_id, permission_code)
values
    (110, 'system:user:list'), (110, 'system:user:add'), (110, 'system:user:edit'),
    (110, 'system:user:remove'), (110, 'system:user:resetPwd'), (110, 'system:user:changeStatus'),
    (120, 'system:role:list'), (120, 'system:role:add'), (120, 'system:role:edit'),
    (120, 'system:role:remove'), (120, 'system:role:changeStatus'), (120, 'system:role:assign'),
    (130, 'system:dept:list'), (130, 'system:dept:add'), (130, 'system:dept:edit'), (130, 'system:dept:remove'),
    (140, 'system:menu:list'), (140, 'system:menu:add'), (140, 'system:menu:edit'), (140, 'system:menu:remove'),
    (150, 'system:apikey:list'), (150, 'system:apikey:add'), (150, 'system:apikey:rotate'),
    (150, 'system:apikey:disable'), (150, 'system:apikey:remove'), (150, 'system:apikey:manageOthers'),
    (160, 'system:resource-group:list'), (160, 'system:resource-group:add'),
    (160, 'system:resource-group:edit'), (160, 'system:resource-group:remove'),
    (160, 'system:resource-group:assign'),
    (210, 'operations:log:view'), (210, 'operations:log:download'), (220, 'operations:system:view'),
    (310, 'video:device:list'), (310, 'video:device:add'), (310, 'video:device:edit'),
    (310, 'video:device:remove'), (310, 'video:device:sync'), (310, 'video:device:config'),
    (320, 'video:channel:list'), (320, 'video:channel:edit'), (320, 'video:channel:play'),
    (320, 'video:channel:stop'), (320, 'video:channel:talk'), (320, 'video:channel:broadcast'),
    (330, 'video:ptz:control'), (330, 'video:ptz:preset'), (330, 'video:ptz:cruise'),
    (340, 'video:record:list'), (340, 'video:record:play'), (340, 'video:record:download'),
    (340, 'video:record:remove'),
    (350, 'video:cloud-record:list'), (350, 'video:cloud-record:play'),
    (350, 'video:cloud-record:download'), (350, 'video:cloud-record:remove'),
    (350, 'video:cloud-record:collect'),
    (360, 'video:alarm:list'), (360, 'video:alarm:remove'), (360, 'video:alarm:clear'),
    (370, 'video:position:list'), (370, 'video:position:subscribe'),
    (380, 'video:proxy:list'), (380, 'video:proxy:add'), (380, 'video:proxy:edit'),
    (380, 'video:proxy:play'), (380, 'video:proxy:stop'), (380, 'video:proxy:remove'),
    (390, 'video:push:list'), (390, 'video:push:add'), (390, 'video:push:edit'),
    (390, 'video:push:play'), (390, 'video:push:stop'), (390, 'video:push:remove'),
    (400, 'video:jt:list'), (400, 'video:jt:edit'), (400, 'video:jt:play'),
    (400, 'video:jt:talk'), (400, 'video:jt:ptz'), (400, 'video:jt:record'), (400, 'video:jt:control'),
    (410, 'video:platform:list'), (410, 'video:platform:manage'), (410, 'video:platform:share-channel'),
    (420, 'video:media-server:list'), (420, 'video:media-server:manage'),
    (430, 'video:rtp:manage'), (430, 'video:ps:manage'),
    (440, 'video:stream:share'), (440, 'video:stream:revoke-share');

insert ignore into wvp_menu
    (parent_id, menu_name, menu_type, perms, order_num, visible, status, cacheable, external, system_menu)
select parent_id,
       permission_code,
       'F',
       permission_code,
       row_number() over (partition by parent_id order by permission_code),
       0, 0, 0, 0, 1
  from wvp_permission_seed;
drop temporary table wvp_permission_seed;
