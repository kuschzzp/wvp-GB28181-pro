-- WVP 2.7.4 -> 2.8.0 身份与功能 RBAC expand 迁移（MySQL 8.0+）
-- 本脚本幂等执行，不删除或重命名旧 wvp_user_role，也不删除 wvp_user.role_id。
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

-- MySQL DDL 会隐式提交，先在任何结构变更前校验旧用户角色关系，避免失败后留下半成品。
drop procedure if exists wvp_preflight_rbac_expand;
delimiter //
create procedure wvp_preflight_rbac_expand()
begin
    if exists (
        select 1
          from wvp_user u
          left join wvp_user_role legacy_role on legacy_role.id = u.role_id
         where u.role_id is not null and legacy_role.id is null
    ) then
        signal sqlstate '45000' set message_text = 'RBAC expand failed: orphan legacy user role';
    end if;
end //
delimiter ;
call wvp_preflight_rbac_expand();
drop procedure wvp_preflight_rbac_expand;

-- 2.7.4 的 serial 是 BIGINT UNSIGNED；新 RBAC 外键统一使用有符号 BIGINT。
alter table wvp_user modify column id bigint not null auto_increment;
alter table wvp_user modify column role_id bigint null;
alter table wvp_user_api_key modify column id bigint not null auto_increment;
alter table wvp_user_api_key modify column user_id bigint null;
alter table wvp_user_role modify column name varchar(64) null;
alter table wvp_user_role modify column authority varchar(64) null;

call wvp_add_column_if_missing('wvp_user', 'dept_id', 'bigint null comment ''主部门ID'' after `role_id`');
call wvp_add_column_if_missing('wvp_user', 'nickname', 'varchar(64) null comment ''显示名称'' after `dept_id`');
call wvp_add_column_if_missing('wvp_user', 'status', 'smallint not null default 0 comment ''0正常，1停用'' after `nickname`');
call wvp_add_column_if_missing('wvp_user', 'locked', 'smallint not null default 0 comment ''0未锁定，1锁定'' after `status`');
call wvp_add_column_if_missing('wvp_user', 'deleted', 'smallint not null default 0 comment ''逻辑删除'' after `locked`');
call wvp_add_column_if_missing('wvp_user', 'token_version', 'bigint not null default 0 comment ''认证授权版本'' after `deleted`');
call wvp_add_column_if_missing('wvp_user', 'password_version', 'smallint not null default 0 comment ''0旧MD5，1BCrypt'' after `token_version`');
call wvp_add_column_if_missing('wvp_user', 'last_login_at', 'timestamp null comment ''最近登录时间'' after `password_version`');
call wvp_add_column_if_missing('wvp_user', 'create_by', 'bigint null comment ''创建人'' after `last_login_at`');
call wvp_add_column_if_missing('wvp_user', 'update_by', 'bigint null comment ''更新人'' after `create_by`');
update wvp_user set nickname = username where nickname is null or nickname = '';

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

insert into wvp_dept
    (id, parent_id, dept_name, dept_code, order_num, status, deleted, create_time, update_time)
values
    (1, 0, '总部', 'ROOT', 0, 0, 0, current_timestamp, current_timestamp)
on duplicate key update
    dept_name = values(dept_name), status = 0, deleted = 0;

insert ignore into wvp_dept_closure (ancestor_id, descendant_id, depth)
values (1, 1, 0);

update wvp_user u
left join wvp_dept d on d.id = u.dept_id
   set u.dept_id = 1
 where u.dept_id is null or d.id is null;

alter table wvp_user modify column dept_id bigint not null comment '主部门ID';

set @wvp_index_sql = (
    select if(count(*) = 0,
              'create index idx_user_dept_status on wvp_user (dept_id, status, deleted)',
              'select 1')
      from information_schema.statistics
     where table_schema = database()
       and table_name = 'wvp_user'
       and index_name = 'idx_user_dept_status'
);
prepare wvp_index_stmt from @wvp_index_sql;
execute wvp_index_stmt;
deallocate prepare wvp_index_stmt;

set @wvp_fk_sql = (
    select if(count(*) = 0,
              'alter table wvp_user add constraint fk_wvp_user_dept foreign key (dept_id) references wvp_dept (id)',
              'select 1')
      from information_schema.key_column_usage
     where table_schema = database()
       and table_name = 'wvp_user'
       and column_name = 'dept_id'
       and referenced_table_name = 'wvp_dept'
       and referenced_column_name = 'id'
);
prepare wvp_fk_stmt from @wvp_fk_sql;
execute wvp_fk_stmt;
deallocate prepare wvp_fk_stmt;

call wvp_add_column_if_missing('wvp_user_api_key', 'key_hash', 'varchar(64) null comment ''HMAC-SHA256''');
call wvp_add_column_if_missing('wvp_user_api_key', 'key_prefix', 'varchar(16) null comment ''展示前缀''');
call wvp_add_column_if_missing('wvp_user_api_key', 'key_last4', 'varchar(4) null comment ''展示尾号''');
call wvp_add_column_if_missing('wvp_user_api_key', 'status', 'varchar(16) not null default ''LEGACY''');
call wvp_add_column_if_missing('wvp_user_api_key', 'ip_allowlist', 'varchar(2000) null comment ''可选IPv4/IPv6 CIDR白名单，逗号分隔''');
call wvp_add_column_if_missing('wvp_user_api_key', 'authz_version', 'bigint not null default 0');
call wvp_add_column_if_missing('wvp_user_api_key', 'last_used_at', 'timestamp null');
update wvp_user_api_key k
join wvp_user u on u.id = k.user_id
set k.status = case when k.enable then 'LEGACY' else 'DISABLED' end,
    k.authz_version = u.token_version,
    k.expired_at = nullif(k.expired_at, 0)
where k.key_hash is null;
set @wvp_index_sql = (
    select if(count(*) = 0,
              'create unique index uk_api_key_hash on wvp_user_api_key (key_hash)',
              'select 1')
      from information_schema.statistics
     where table_schema = database()
       and table_name = 'wvp_user_api_key'
       and index_name = 'uk_api_key_hash'
);
prepare wvp_index_stmt from @wvp_index_sql;
execute wvp_index_stmt;
deallocate prepare wvp_index_stmt;

set @wvp_index_sql = (
    select if(count(*) = 0,
              'create index idx_api_key_owner_status on wvp_user_api_key (user_id, status)',
              'select 1')
      from information_schema.statistics
     where table_schema = database()
       and table_name = 'wvp_user_api_key'
       and index_name = 'idx_api_key_owner_status'
);
prepare wvp_index_stmt from @wvp_index_sql;
execute wvp_index_stmt;
deallocate prepare wvp_index_stmt;

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

insert into wvp_role
    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role,
     create_time, update_time)
select legacy_role.id,
       coalesce(nullif(legacy_role.name, ''), concat('旧角色-', legacy_role.id)),
       case
           when legacy_role.id = (select admin_user.role_id from wvp_user admin_user where admin_user.username = 'admin' order by admin_user.id limit 1)
               then 'super_admin'
           else concat('legacy_role_', legacy_role.id)
       end,
       legacy_role.id,
       case
           when legacy_role.id = (select admin_user.role_id from wvp_user admin_user where admin_user.username = 'admin' order by admin_user.id limit 1)
               then 'ALL' else 'NONE'
       end,
       case
           when legacy_role.id = (select admin_user.role_id from wvp_user admin_user where admin_user.username = 'admin' order by admin_user.id limit 1)
               then 'ALL' else 'NONE'
       end,
       0,
       case
           when legacy_role.id = (select admin_user.role_id from wvp_user admin_user where admin_user.username = 'admin' order by admin_user.id limit 1)
               then 1 else 0
       end,
       coalesce(str_to_date(legacy_role.create_time, '%Y-%m-%d %H:%i:%s'), current_timestamp),
       coalesce(str_to_date(legacy_role.update_time, '%Y-%m-%d %H:%i:%s'), current_timestamp)
  from wvp_user_role legacy_role
on duplicate key update
    name = values(name),
    update_time = values(update_time);

create table if not exists wvp_user_role_rel
(
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id),
    index idx_user_role_rel_role (role_id, user_id),
    constraint fk_user_role_rel_user foreign key (user_id) references wvp_user (id) on delete cascade,
    constraint fk_user_role_rel_role foreign key (role_id) references wvp_role (id) on delete cascade
) engine = InnoDB default charset = utf8mb4 comment = 'expand阶段用户角色关系';

insert ignore into wvp_user_role_rel (user_id, role_id)
select u.id, u.role_id
  from wvp_user u
  join wvp_role r on r.id = u.role_id
 where u.role_id is not null;

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
    api_key_id         bigint not null,
    resource_group_id  bigint not null,
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

-- 先写入路由节点；按钮权限种子紧随其后，使用固定 ID 和 insert ignore 保证幂等。
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

insert ignore into wvp_menu
    (id, parent_id, menu_name, menu_type, perms, order_num, visible, status, cacheable, external, system_menu)
values
    (11001, 110, '查询用户', 'F', 'system:user:list', 1, 0, 0, 0, 0, 1),
    (11002, 110, '新增用户', 'F', 'system:user:add', 2, 0, 0, 0, 0, 1),
    (11003, 110, '编辑用户', 'F', 'system:user:edit', 3, 0, 0, 0, 0, 1),
    (11004, 110, '删除用户', 'F', 'system:user:remove', 4, 0, 0, 0, 0, 1),
    (11005, 110, '重置密码', 'F', 'system:user:resetPwd', 5, 0, 0, 0, 0, 1),
    (11006, 110, '变更用户状态', 'F', 'system:user:changeStatus', 6, 0, 0, 0, 0, 1),
    (12001, 120, '查询角色', 'F', 'system:role:list', 1, 0, 0, 0, 0, 1),
    (12002, 120, '新增角色', 'F', 'system:role:add', 2, 0, 0, 0, 0, 1),
    (12003, 120, '编辑角色', 'F', 'system:role:edit', 3, 0, 0, 0, 0, 1),
    (12004, 120, '删除角色', 'F', 'system:role:remove', 4, 0, 0, 0, 0, 1),
    (12005, 120, '变更角色状态', 'F', 'system:role:changeStatus', 5, 0, 0, 0, 0, 1),
    (12006, 120, '分配角色', 'F', 'system:role:assign', 6, 0, 0, 0, 0, 1),
    (13001, 130, '查询部门', 'F', 'system:dept:list', 1, 0, 0, 0, 0, 1),
    (13002, 130, '新增部门', 'F', 'system:dept:add', 2, 0, 0, 0, 0, 1),
    (13003, 130, '编辑部门', 'F', 'system:dept:edit', 3, 0, 0, 0, 0, 1),
    (13004, 130, '删除部门', 'F', 'system:dept:remove', 4, 0, 0, 0, 0, 1),
    (14001, 140, '查询菜单', 'F', 'system:menu:list', 1, 0, 0, 0, 0, 1),
    (14002, 140, '新增菜单', 'F', 'system:menu:add', 2, 0, 0, 0, 0, 1),
    (14003, 140, '编辑菜单', 'F', 'system:menu:edit', 3, 0, 0, 0, 0, 1),
    (14004, 140, '删除菜单', 'F', 'system:menu:remove', 4, 0, 0, 0, 0, 1),
    (15001, 150, '查询API Key', 'F', 'system:apikey:list', 1, 0, 0, 0, 0, 1),
    (15002, 150, '新增API Key', 'F', 'system:apikey:add', 2, 0, 0, 0, 0, 1),
    (15003, 150, '轮换API Key', 'F', 'system:apikey:rotate', 3, 0, 0, 0, 0, 1),
    (15004, 150, '停用API Key', 'F', 'system:apikey:disable', 4, 0, 0, 0, 0, 1),
    (15005, 150, '删除API Key', 'F', 'system:apikey:remove', 5, 0, 0, 0, 0, 1),
    (15006, 150, '管理他人API Key', 'F', 'system:apikey:manageOthers', 6, 0, 0, 0, 0, 1),
    (16001, 160, '查询资源组', 'F', 'system:resource-group:list', 1, 0, 0, 0, 0, 1),
    (16002, 160, '新增资源组', 'F', 'system:resource-group:add', 2, 0, 0, 0, 0, 1),
    (16003, 160, '编辑资源组', 'F', 'system:resource-group:edit', 3, 0, 0, 0, 0, 1),
    (16004, 160, '删除资源组', 'F', 'system:resource-group:remove', 4, 0, 0, 0, 0, 1),
    (16005, 160, '分配资源组', 'F', 'system:resource-group:assign', 5, 0, 0, 0, 0, 1),
    (21001, 210, '查看日志', 'F', 'operations:log:view', 1, 0, 0, 0, 0, 1),
    (21002, 210, '下载日志', 'F', 'operations:log:download', 2, 0, 0, 0, 0, 1),
    (22001, 220, '查看平台状态', 'F', 'operations:system:view', 1, 0, 0, 0, 0, 1),
    (31001, 310, '查询GB设备', 'F', 'video:device:list', 1, 0, 0, 0, 0, 1),
    (31002, 310, '新增GB设备', 'F', 'video:device:add', 2, 0, 0, 0, 0, 1),
    (31003, 310, '编辑GB设备', 'F', 'video:device:edit', 3, 0, 0, 0, 0, 1),
    (31004, 310, '删除GB设备', 'F', 'video:device:remove', 4, 0, 0, 0, 0, 1),
    (31005, 310, '同步GB设备', 'F', 'video:device:sync', 5, 0, 0, 0, 0, 1),
    (31006, 310, '配置GB设备', 'F', 'video:device:config', 6, 0, 0, 0, 0, 1),
    (32001, 320, '查询通道', 'F', 'video:channel:list', 1, 0, 0, 0, 0, 1),
    (32002, 320, '编辑通道', 'F', 'video:channel:edit', 2, 0, 0, 0, 0, 1),
    (32003, 320, '播放通道', 'F', 'video:channel:play', 3, 0, 0, 0, 0, 1),
    (32004, 320, '停止通道', 'F', 'video:channel:stop', 4, 0, 0, 0, 0, 1),
    (32005, 320, '通道对讲', 'F', 'video:channel:talk', 5, 0, 0, 0, 0, 1),
    (32006, 320, '通道广播', 'F', 'video:channel:broadcast', 6, 0, 0, 0, 0, 1),
    (33001, 330, '云台控制', 'F', 'video:ptz:control', 1, 0, 0, 0, 0, 1),
    (33002, 330, '预置位', 'F', 'video:ptz:preset', 2, 0, 0, 0, 0, 1),
    (33003, 330, '巡航', 'F', 'video:ptz:cruise', 3, 0, 0, 0, 0, 1),
    (34001, 340, '查询设备录像', 'F', 'video:record:list', 1, 0, 0, 0, 0, 1),
    (34002, 340, '播放设备录像', 'F', 'video:record:play', 2, 0, 0, 0, 0, 1),
    (34003, 340, '下载设备录像', 'F', 'video:record:download', 3, 0, 0, 0, 0, 1),
    (34004, 340, '删除设备录像', 'F', 'video:record:remove', 4, 0, 0, 0, 0, 1),
    (35001, 350, '查询云端录像', 'F', 'video:cloud-record:list', 1, 0, 0, 0, 0, 1),
    (35002, 350, '播放云端录像', 'F', 'video:cloud-record:play', 2, 0, 0, 0, 0, 1),
    (35003, 350, '下载云端录像', 'F', 'video:cloud-record:download', 3, 0, 0, 0, 0, 1),
    (35004, 350, '删除云端录像', 'F', 'video:cloud-record:remove', 4, 0, 0, 0, 0, 1),
    (35005, 350, '收藏云端录像', 'F', 'video:cloud-record:collect', 5, 0, 0, 0, 0, 1),
    (36001, 360, '查询告警', 'F', 'video:alarm:list', 1, 0, 0, 0, 0, 1),
    (36002, 360, '删除告警', 'F', 'video:alarm:remove', 2, 0, 0, 0, 0, 1),
    (36003, 360, '清空告警', 'F', 'video:alarm:clear', 3, 0, 0, 0, 0, 1),
    (37001, 370, '查询位置', 'F', 'video:position:list', 1, 0, 0, 0, 0, 1),
    (37002, 370, '订阅位置', 'F', 'video:position:subscribe', 2, 0, 0, 0, 0, 1),
    (38001, 380, '查询拉流代理', 'F', 'video:proxy:list', 1, 0, 0, 0, 0, 1),
    (38002, 380, '新增拉流代理', 'F', 'video:proxy:add', 2, 0, 0, 0, 0, 1),
    (38003, 380, '编辑拉流代理', 'F', 'video:proxy:edit', 3, 0, 0, 0, 0, 1),
    (38004, 380, '播放拉流代理', 'F', 'video:proxy:play', 4, 0, 0, 0, 0, 1),
    (38005, 380, '停止拉流代理', 'F', 'video:proxy:stop', 5, 0, 0, 0, 0, 1),
    (38006, 380, '删除拉流代理', 'F', 'video:proxy:remove', 6, 0, 0, 0, 0, 1),
    (39001, 390, '查询推流', 'F', 'video:push:list', 1, 0, 0, 0, 0, 1),
    (39002, 390, '新增推流', 'F', 'video:push:add', 2, 0, 0, 0, 0, 1),
    (39003, 390, '编辑推流', 'F', 'video:push:edit', 3, 0, 0, 0, 0, 1),
    (39004, 390, '播放推流', 'F', 'video:push:play', 4, 0, 0, 0, 0, 1),
    (39005, 390, '停止推流', 'F', 'video:push:stop', 5, 0, 0, 0, 0, 1),
    (39006, 390, '删除推流', 'F', 'video:push:remove', 6, 0, 0, 0, 0, 1),
    (40001, 400, '查询JT1078', 'F', 'video:jt:list', 1, 0, 0, 0, 0, 1),
    (40002, 400, '编辑JT1078', 'F', 'video:jt:edit', 2, 0, 0, 0, 0, 1),
    (40003, 400, '播放JT1078', 'F', 'video:jt:play', 3, 0, 0, 0, 0, 1),
    (40004, 400, 'JT1078对讲', 'F', 'video:jt:talk', 4, 0, 0, 0, 0, 1),
    (40005, 400, 'JT1078云台', 'F', 'video:jt:ptz', 5, 0, 0, 0, 0, 1),
    (40006, 400, 'JT1078录像', 'F', 'video:jt:record', 6, 0, 0, 0, 0, 1),
    (40007, 400, 'JT1078控制', 'F', 'video:jt:control', 7, 0, 0, 0, 0, 1),
    (41001, 410, '查询国标级联', 'F', 'video:platform:list', 1, 0, 0, 0, 0, 1),
    (41002, 410, '管理国标级联', 'F', 'video:platform:manage', 2, 0, 0, 0, 0, 1),
    (41003, 410, '共享通道', 'F', 'video:platform:share-channel', 3, 0, 0, 0, 0, 1),
    (42001, 420, '查询媒体节点', 'F', 'video:media-server:list', 1, 0, 0, 0, 0, 1),
    (42002, 420, '管理媒体节点', 'F', 'video:media-server:manage', 2, 0, 0, 0, 0, 1),
    (43001, 430, '管理RTP工具', 'F', 'video:rtp:manage', 1, 0, 0, 0, 0, 1),
    (43002, 430, '管理PS工具', 'F', 'video:ps:manage', 2, 0, 0, 0, 0, 1),
    (44001, 440, '创建流分享', 'F', 'video:stream:share', 1, 0, 0, 0, 0, 1),
    (44002, 440, '撤销流分享', 'F', 'video:stream:revoke-share', 2, 0, 0, 0, 0, 1);

-- 普通旧角色先显式继承 2.7.4 可访问的业务菜单和本人 API Key 权限。
-- 管理员首次保存角色后 update_by 非空，角色菜单由新配置整体替换，重复执行升级脚本也不会重新放大权限。
insert ignore into wvp_role_menu (role_id, menu_id)
select r.id, m.id
  from wvp_role r
 cross join wvp_menu m
 where r.role_key regexp '^legacy_role_[0-9]+$'
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

drop procedure if exists wvp_validate_rbac_expand;
delimiter //
create procedure wvp_validate_rbac_expand()
begin
    if exists (
        select 1
          from wvp_user u
          left join wvp_role r on r.id = u.role_id
         where u.role_id is not null and r.id is null
    ) then
        signal sqlstate '45000' set message_text = 'RBAC expand failed: orphan legacy user role';
    end if;
    if exists (
        select 1
          from wvp_user u
          left join wvp_dept d on d.id = u.dept_id
         where u.dept_id is null or d.id is null
    ) then
        signal sqlstate '45000' set message_text = 'RBAC expand failed: invalid user department';
    end if;
end //
delimiter ;
call wvp_validate_rbac_expand();
drop procedure wvp_validate_rbac_expand;

-- expand 脚本永不自动删除兼容表；停机收缩请单独执行 contract-mysql-2.8.0.sql。
