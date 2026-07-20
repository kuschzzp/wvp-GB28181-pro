-- WVP 2.8.0 RBAC contract（PostgreSQL 14+）
-- 仅允许在停机、readiness 全绿、快照恢复演练通过后执行。
-- wvp_user_role_rel 最终成为可更新兼容视图；旧应用仍不可回退运行。

begin;

do $contract$
declare
    final_relation_columns integer;
    legacy_master_columns integer;
    expand_relation_table integer;
    relation_alias_view integer;
    contract_temp_table integer;
begin
    select count(*)
      into final_relation_columns
      from information_schema.columns
     where table_schema = current_schema()
       and table_name = 'wvp_user_role'
       and column_name in ('user_id', 'role_id');

    select count(*)
      into relation_alias_view
      from information_schema.views
     where table_schema = current_schema()
       and table_name = 'wvp_user_role_rel';

    select count(*)
      into contract_temp_table
      from information_schema.tables
     where table_schema = current_schema()
       and table_name = 'wvp_user_role_legacy_contract';

    if final_relation_columns = 2 then
        if contract_temp_table <> 0 then
            raise exception 'RBAC contract incomplete: legacy contract temp table still exists';
        end if;
        if relation_alias_view = 0 then
            execute 'create view wvp_user_role_rel as select user_id, role_id from wvp_user_role';
        end if;
    else
        select count(*)
          into legacy_master_columns
          from information_schema.columns
         where table_schema = current_schema()
           and table_name = 'wvp_user_role'
           and column_name in ('id', 'name', 'authority');

        select count(*)
          into expand_relation_table
          from information_schema.tables
         where table_schema = current_schema()
           and table_name = 'wvp_user_role_rel'
           and table_type = 'BASE TABLE';

        if legacy_master_columns <> 3 or expand_relation_table <> 1 then
            raise exception 'RBAC contract refused: expand schema is incomplete or unknown';
        end if;
        if contract_temp_table <> 0 then
            raise exception 'RBAC contract refused: stale contract temp table exists';
        end if;
        if exists (select 1 from wvp_user_api_key where status = 'LEGACY') then
            raise exception 'RBAC contract refused: LEGACY API Key remains';
        end if;
        if exists (
            select 1
              from wvp_cloud_record
             where source_type is null
                or source_type = 'LEGACY_UNBOUND'
                or source_id is null
                or channel_id is null
        ) then
            raise exception 'RBAC contract refused: unbound cloud record remains';
        end if;
        if exists (
            select 1
              from wvp_user u
             where u.role_id is not null
               and not exists (
                    select 1
                      from wvp_user_role_rel urr
                     where urr.user_id = u.id
                       and urr.role_id = u.role_id
               )
        ) then
            raise exception 'RBAC contract refused: legacy role mapping is not fully copied';
        end if;

        execute 'alter table wvp_user_role rename to wvp_user_role_legacy_contract';
        execute 'alter table wvp_user_role_rel rename to wvp_user_role';
        execute 'create view wvp_user_role_rel as select user_id, role_id from wvp_user_role';
        execute 'drop table wvp_user_role_legacy_contract';
    end if;
end;
$contract$;

select setval(
    pg_get_serial_sequence('wvp_role', 'id'),
    greatest((select coalesce(max(id), 0) from wvp_role), 1),
    true);
select setval(
    pg_get_serial_sequence('wvp_user', 'id'),
    greatest((select coalesce(max(id), 0) from wvp_user), 1),
    true);
select setval(
    pg_get_serial_sequence('wvp_user_api_key', 'id'),
    greatest((select coalesce(max(id), 0) from wvp_user_api_key), 1),
    true);

comment on table wvp_user_role is '最终用户角色关系';
comment on view wvp_user_role_rel is '2.8.x 新应用过渡别名；不是旧角色主表';

commit;
