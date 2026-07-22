-- WVP 2.7.4 -> 2.8.0 身份与功能 RBAC expand 迁移（PostgreSQL 14+）
-- 本脚本幂等执行，不删除或重命名旧 wvp_user_role，也不删除 wvp_user.role_id。
begin;

alter table wvp_media_server add column if not exists hook_secret_hash varchar(64);
alter table wvp_media_server add column if not exists previous_hook_secret_hash varchar(64);
alter table wvp_media_server add column if not exists previous_hook_secret_expires_at timestamp;
alter table wvp_media_server add column if not exists hook_secret_version bigint not null default 0;
alter table wvp_media_server add column if not exists hook_allowed_cidrs varchar(2000);
alter table wvp_media_server add column if not exists hook_auth_enabled boolean not null default true;

alter table wvp_user alter column id type bigint;
alter table wvp_user alter column role_id type bigint;
alter table wvp_user_api_key alter column id type bigint;
alter table wvp_user_api_key alter column user_id type bigint;
alter table wvp_user_role alter column name type varchar(64);
alter table wvp_user_role alter column authority type varchar(64);
alter sequence if exists wvp_user_id_seq as bigint;
alter sequence if exists wvp_user_role_id_seq as bigint;
alter sequence if exists wvp_user_api_key_id_seq as bigint;

alter table wvp_user add column if not exists dept_id bigint;
alter table wvp_user add column if not exists nickname varchar(64);
alter table wvp_user add column if not exists status smallint not null default 0;
alter table wvp_user add column if not exists locked smallint not null default 0;
alter table wvp_user add column if not exists deleted smallint not null default 0;
alter table wvp_user add column if not exists token_version bigint not null default 0;
alter table wvp_user add column if not exists password_version smallint not null default 0;
alter table wvp_user add column if not exists last_login_at timestamp;
alter table wvp_user add column if not exists create_by bigint;
alter table wvp_user add column if not exists update_by bigint;
update wvp_user set nickname = username where nickname is null or nickname = '';

create table if not exists wvp_dept
(
    id             bigserial primary key,
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
    constraint uk_wvp_dept_code unique (dept_code),
    constraint ck_wvp_dept_status check (status in (0, 1)),
    constraint ck_wvp_dept_deleted check (deleted in (0, 1))
);
create index if not exists idx_wvp_dept_parent
    on wvp_dept (parent_id, status, deleted);

create table if not exists wvp_dept_closure
(
    ancestor_id   bigint not null references wvp_dept (id) on delete cascade,
    descendant_id bigint not null references wvp_dept (id) on delete cascade,
    depth          integer not null check (depth >= 0),
    primary key (ancestor_id, descendant_id)
);
create index if not exists idx_wvp_dept_closure_desc
    on wvp_dept_closure (descendant_id, ancestor_id);

insert into wvp_dept
    (id, parent_id, dept_name, dept_code, order_num, status, deleted, create_time, update_time)
values
    (1, 0, '总部', 'ROOT', 0, 0, 0, current_timestamp, current_timestamp)
on conflict (id) do update
set dept_name = excluded.dept_name, status = 0, deleted = 0;

insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
values (1, 1, 0)
on conflict do nothing;

update wvp_user u
   set dept_id = 1
 where u.dept_id is null
    or not exists (select 1 from wvp_dept d where d.id = u.dept_id);

alter table wvp_user alter column dept_id set not null;
create index if not exists idx_user_dept_status on wvp_user (dept_id, status, deleted);

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'fk_wvp_user_dept'
           and conrelid = 'wvp_user'::regclass
    ) then
        alter table wvp_user
            add constraint fk_wvp_user_dept foreign key (dept_id) references wvp_dept (id);
    end if;
end;
$$;

alter table wvp_user_api_key add column if not exists key_hash varchar(64);
alter table wvp_user_api_key add column if not exists key_prefix varchar(16);
alter table wvp_user_api_key add column if not exists key_last4 varchar(4);
alter table wvp_user_api_key add column if not exists status varchar(16) not null default 'LEGACY';
alter table wvp_user_api_key add column if not exists ip_allowlist varchar(2000);
alter table wvp_user_api_key add column if not exists authz_version bigint not null default 0;
alter table wvp_user_api_key add column if not exists last_used_at timestamp;
update wvp_user_api_key k
   set status = case when k.enable then 'LEGACY' else 'DISABLED' end,
       authz_version = u.token_version,
       expired_at = nullif(k.expired_at, 0)
  from wvp_user u
 where u.id = k.user_id
   and k.key_hash is null;
create unique index if not exists uk_api_key_hash on wvp_user_api_key (key_hash);
create index if not exists idx_api_key_owner_status on wvp_user_api_key (user_id, status);

create table if not exists wvp_role
(
    id               bigserial primary key,
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
    constraint uk_wvp_role_key unique (role_key),
    constraint ck_wvp_role_dept_scope check (dept_data_scope in ('ALL', 'CUSTOM', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'NONE')),
    constraint ck_wvp_role_video_scope check (video_scope_type in ('ALL', 'CUSTOM', 'NONE')),
    constraint ck_wvp_role_status check (status in (0, 1)),
    constraint ck_wvp_role_system check (system_role in (0, 1))
);

create table if not exists wvp_resource_group
(
    id          bigserial primary key,
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
    constraint uk_wvp_resource_group_code unique (group_code),
    constraint ck_wvp_resource_group_status check (status in (0, 1))
);
create index if not exists idx_wvp_resource_group_parent
    on wvp_resource_group (parent_id, status, order_num);

create table if not exists wvp_resource_group_closure
(
    ancestor_id   bigint not null references wvp_resource_group (id) on delete cascade,
    descendant_id bigint not null references wvp_resource_group (id) on delete cascade,
    depth         integer not null check (depth >= 0),
    primary key (ancestor_id, descendant_id)
);
create index if not exists idx_wvp_resource_group_closure_desc
    on wvp_resource_group_closure (descendant_id, ancestor_id);

create table if not exists wvp_resource_group_item
(
    id            bigserial primary key,
    group_id      bigint not null references wvp_resource_group (id) on delete cascade,
    resource_type varchar(32) not null,
    resource_id   bigint not null,
    create_by     bigint,
    create_time   timestamp not null default current_timestamp,
    constraint uk_wvp_resource_group_item unique (group_id, resource_type, resource_id),
    constraint ck_wvp_resource_group_item_type check (
        resource_type in ('GB_DEVICE', 'CHANNEL', 'JT_TERMINAL', 'JT_CHANNEL',
                          'STREAM_PROXY', 'STREAM_PUSH')
    )
);
create index if not exists idx_wvp_resource_item_ref
    on wvp_resource_group_item (resource_type, resource_id);

create table if not exists wvp_role_resource_group
(
    role_id           bigint not null references wvp_role (id) on delete cascade,
    resource_group_id bigint not null references wvp_resource_group (id) on delete cascade,
    primary key (role_id, resource_group_id)
);
create index if not exists idx_wvp_role_resource_group_group
    on wvp_role_resource_group (resource_group_id, role_id);

insert into wvp_resource_group
    (id, parent_id, group_code, group_name, order_num, status, description,
     create_time, update_time)
values
    (1, 0, 'ROOT_VIDEO', '全部视频资源', 0, 0, '系统内置视频授权根组',
     current_timestamp, current_timestamp)
on conflict (id) do update
set group_name = excluded.group_name, status = 0;

insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
values (1, 1, 0)
on conflict do nothing;

select setval(
    pg_get_serial_sequence('wvp_resource_group', 'id'),
    greatest(coalesce((select max(id) from wvp_resource_group), 1), 1),
    true);

alter table wvp_cloud_record
    add column if not exists source_type varchar(32) not null default 'LEGACY_UNBOUND';
alter table wvp_cloud_record
    add column if not exists source_id bigint;
alter table wvp_cloud_record
    add column if not exists channel_id bigint;
alter table wvp_cloud_record
    add column if not exists source_reason varchar(64);
update wvp_cloud_record
   set source_type = 'LEGACY_UNBOUND'
 where source_type is null or source_type = '';
create index if not exists idx_wvp_cloud_record_channel
    on wvp_cloud_record (channel_id, start_time);

create table if not exists wvp_alarm (
    id serial primary key,
    channel_id integer,
    description character varying(255),
    snap_path character varying(255),
    record_path character varying(255),
    longitude double precision,
    latitude double precision,
    alarm_type integer,
    alarm_time bigint
);
comment on column wvp_alarm.id is '主键ID';
comment on column wvp_alarm.channel_id is '关联通道的数据库id';
comment on column wvp_alarm.description is '报警描述';
comment on column wvp_alarm.snap_path is '报警快照路径';
comment on column wvp_alarm.record_path is '报警录像路径';
comment on column wvp_alarm.longitude is '报警附带的经度';
comment on column wvp_alarm.latitude is '报警附带的纬度';
comment on column wvp_alarm.alarm_type is '报警类别';
comment on column wvp_alarm.alarm_time is '报警时间';


create table if not exists wvp_role_dept
(
    role_id bigint not null references wvp_role (id) on delete cascade,
    dept_id bigint not null references wvp_dept (id) on delete cascade,
    primary key (role_id, dept_id)
);
create index if not exists idx_wvp_role_dept_dept
    on wvp_role_dept (dept_id, role_id);

insert into wvp_role
    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role,
     create_time, update_time)
select legacy_role.id,
       coalesce(nullif(legacy_role.name, ''), '旧角色-' || legacy_role.id),
       case when legacy_role.id = (
           select admin_user.role_id from wvp_user admin_user
            where admin_user.username = 'admin' order by admin_user.id limit 1
       ) then 'super_admin' else 'legacy_role_' || legacy_role.id end,
       legacy_role.id,
       case when legacy_role.id = (
           select admin_user.role_id from wvp_user admin_user
            where admin_user.username = 'admin' order by admin_user.id limit 1
       ) then 'ALL' else 'NONE' end,
       case when legacy_role.id = (
           select admin_user.role_id from wvp_user admin_user
            where admin_user.username = 'admin' order by admin_user.id limit 1
       ) then 'ALL' else 'NONE' end,
       0,
       case when legacy_role.id = (
           select admin_user.role_id from wvp_user admin_user
            where admin_user.username = 'admin' order by admin_user.id limit 1
       ) then 1 else 0 end,
       case when legacy_role.create_time ~ '^\d{4}-\d{2}-\d{2} ' then legacy_role.create_time::timestamp else current_timestamp end,
       case when legacy_role.update_time ~ '^\d{4}-\d{2}-\d{2} ' then legacy_role.update_time::timestamp else current_timestamp end
  from wvp_user_role legacy_role
on conflict (id) do update set
    name = excluded.name,
    update_time = excluded.update_time;

create table if not exists wvp_user_role_rel
(
    user_id bigint not null references wvp_user (id) on delete cascade,
    role_id bigint not null references wvp_role (id) on delete cascade,
    primary key (user_id, role_id)
);
create index if not exists idx_user_role_rel_role on wvp_user_role_rel (role_id, user_id);

insert into wvp_user_role_rel (user_id, role_id)
select u.id, u.role_id
  from wvp_user u
  join wvp_role r on r.id = u.role_id
 where u.role_id is not null
on conflict do nothing;

create table if not exists wvp_menu
(
    id          bigserial primary key,
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
    constraint uk_wvp_menu_perms unique (perms),
    constraint ck_wvp_menu_type check (menu_type in ('M', 'C', 'F'))
);
create index if not exists idx_menu_parent on wvp_menu (parent_id, status, visible);

create table if not exists wvp_role_menu
(
    role_id bigint not null references wvp_role (id) on delete cascade,
    menu_id bigint not null references wvp_menu (id) on delete cascade,
    primary key (role_id, menu_id)
);
create index if not exists idx_role_menu_menu on wvp_role_menu (menu_id, role_id);

create table if not exists wvp_api_key_permission
(
    api_key_id bigint not null references wvp_user_api_key (id) on delete cascade,
    menu_id bigint not null references wvp_menu (id) on delete cascade,
    primary key (api_key_id, menu_id)
);
create index if not exists idx_api_key_permission_menu
    on wvp_api_key_permission (menu_id, api_key_id);

create table if not exists wvp_api_key_resource_group
(
    api_key_id bigint not null references wvp_user_api_key (id) on delete cascade,
    resource_group_id bigint not null references wvp_resource_group (id) on delete cascade,
    primary key (api_key_id, resource_group_id)
);
create index if not exists idx_api_key_resource_group_group
    on wvp_api_key_resource_group (resource_group_id, api_key_id);

create table if not exists wvp_authz_audit
(
    id                bigserial primary key,
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
    constraint ck_authz_audit_decision check (decision in ('ALLOW', 'DENY'))
);
create index if not exists idx_authz_audit_principal on wvp_authz_audit (principal_type, principal_id, create_time);
create index if not exists idx_authz_audit_dept on wvp_authz_audit (principal_dept_id, create_time);
create index if not exists idx_authz_audit_resource on wvp_authz_audit (resource_type, resource_id, create_time);

create table if not exists wvp_stream_share
(
    share_id        varchar(64) primary key,
    creator_user_id bigint not null references wvp_user (id) on delete cascade,
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
    )
);
create index if not exists idx_stream_share_creator
    on wvp_stream_share (creator_user_id, status, expires_at);
create index if not exists idx_stream_share_resource
    on wvp_stream_share (resource_type, resource_id);

insert into wvp_menu
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
    (490, 300, '录制计划', 'C', 'RecordPlan', '/recordPlan', 'record-plan/index', 'recordPlan', 12, 0, 0, 0, 0, 1)
on conflict (id) do nothing;

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

select setval(pg_get_serial_sequence('wvp_menu', 'id'), greatest((select max(id) from wvp_menu), 1), true);

with permission_seed(parent_id, permission_code) as (
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
        (440, 'video:stream:share'), (440, 'video:stream:revoke-share')
)
insert into wvp_menu
    (parent_id, menu_name, menu_type, perms, order_num, visible, status, cacheable, external, system_menu)
select parent_id,
       permission_code,
       'F',
       permission_code,
       row_number() over (partition by parent_id order by permission_code),
       0, 0, 0, 0, 1
  from permission_seed
on conflict (perms) do nothing;

-- 普通旧角色先显式继承 2.7.4 可访问的业务菜单和本人 API Key 权限。
-- 管理员首次保存角色后 update_by 非空，角色菜单由新配置整体替换，重复执行升级脚本也不会重新放大权限。
insert into wvp_role_menu (role_id, menu_id)
select r.id, m.id
  from wvp_role r
 cross join wvp_menu m
 where r.role_key ~ '^legacy_role_[0-9]+$'
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
   )
on conflict do nothing;

do $$
begin
    if exists (
        select 1
          from wvp_user u
          left join wvp_role r on r.id = u.role_id
         where u.role_id is not null and r.id is null
    ) then
        raise exception 'RBAC expand failed: orphan legacy user role';
    end if;
    if exists (
        select 1
          from wvp_user u
          left join wvp_dept d on d.id = u.dept_id
         where u.dept_id is null or d.id is null
    ) then
        raise exception 'RBAC expand failed: invalid user department';
    end if;
end;
$$;

select setval(pg_get_serial_sequence('wvp_role', 'id'), greatest((select max(id) from wvp_role), 1), true);
select setval(pg_get_serial_sequence('wvp_dept', 'id'), greatest((select max(id) from wvp_dept), 1), true);
select setval(pg_get_serial_sequence('wvp_user', 'id'), greatest((select max(id) from wvp_user), 1), true);
select setval(pg_get_serial_sequence('wvp_user_role', 'id'), greatest((select max(id) from wvp_user_role), 1), true);
select setval(pg_get_serial_sequence('wvp_user_api_key', 'id'), greatest((select max(id) from wvp_user_api_key), 1), true);
commit;

-- expand 脚本永不自动删除兼容表；停机收缩请单独执行 contract-postgresql-2.8.0.sql。
