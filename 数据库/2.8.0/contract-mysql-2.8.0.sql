-- WVP 2.8.0 RBAC contract（MySQL 8.0+）
-- 仅允许在停机、readiness 全绿、快照恢复演练通过后执行。
-- 本脚本删除 2.7.x 角色主表，把用户角色关系表收缩为最终表名 wvp_user_role。
-- wvp_user_role_rel 会保留为新应用使用的可更新兼容视图；旧应用仍不可回退运行。

drop procedure if exists wvp_contract_rbac_2_8_0;
delimiter //
create procedure wvp_contract_rbac_2_8_0()
main: begin
    declare v_final_relation_columns bigint default 0;
    declare v_legacy_master_columns bigint default 0;
    declare v_expand_relation_table bigint default 0;
    declare v_relation_alias_view bigint default 0;
    declare v_contract_temp_table bigint default 0;
    declare v_next_id bigint default 1;

    select count(*)
      into v_final_relation_columns
      from information_schema.columns
     where table_schema = database()
       and table_name = 'wvp_user_role'
       and column_name in ('user_id', 'role_id');

    select count(*)
      into v_relation_alias_view
      from information_schema.tables
     where table_schema = database()
       and table_name = 'wvp_user_role_rel'
       and table_type = 'VIEW';

    select count(*)
      into v_contract_temp_table
      from information_schema.tables
     where table_schema = database()
       and table_name = 'wvp_user_role_legacy_contract';

    if v_final_relation_columns = 2 then
        if v_contract_temp_table <> 0 then
            signal sqlstate '45000'
                set message_text = 'RBAC contract incomplete: legacy contract temp table still exists';
        end if;
        if v_relation_alias_view = 0 then
            set @wvp_contract_sql =
                'create view wvp_user_role_rel as select user_id, role_id from wvp_user_role';
            prepare wvp_contract_statement from @wvp_contract_sql;
            execute wvp_contract_statement;
            deallocate prepare wvp_contract_statement;
        end if;
    else
        select count(*)
          into v_legacy_master_columns
          from information_schema.columns
         where table_schema = database()
           and table_name = 'wvp_user_role'
           and column_name in ('id', 'name', 'authority');

        select count(*)
          into v_expand_relation_table
          from information_schema.tables
         where table_schema = database()
           and table_name = 'wvp_user_role_rel'
           and table_type = 'BASE TABLE';

        if v_legacy_master_columns <> 3 or v_expand_relation_table <> 1 then
            signal sqlstate '45000'
                set message_text = 'RBAC contract refused: expand schema is incomplete or unknown';
        end if;
        if v_contract_temp_table <> 0 then
            signal sqlstate '45000'
                set message_text = 'RBAC contract refused: stale contract temp table exists';
        end if;
        if exists (
            select 1 from wvp_user_api_key where status = 'LEGACY'
        ) then
            signal sqlstate '45000'
                set message_text = 'RBAC contract refused: LEGACY API Key remains';
        end if;
        if exists (
            select 1
              from wvp_cloud_record
             where source_type is null
                or source_type = 'LEGACY_UNBOUND'
                or source_id is null
                or channel_id is null
        ) then
            signal sqlstate '45000'
                set message_text = 'RBAC contract refused: unbound cloud record remains';
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
            signal sqlstate '45000'
                set message_text = 'RBAC contract refused: legacy role mapping is not fully copied';
        end if;

        set @wvp_contract_sql =
            'rename table wvp_user_role to wvp_user_role_legacy_contract, wvp_user_role_rel to wvp_user_role';
        prepare wvp_contract_statement from @wvp_contract_sql;
        execute wvp_contract_statement;
        deallocate prepare wvp_contract_statement;

        set @wvp_contract_sql =
            'create view wvp_user_role_rel as select user_id, role_id from wvp_user_role';
        prepare wvp_contract_statement from @wvp_contract_sql;
        execute wvp_contract_statement;
        deallocate prepare wvp_contract_statement;

        set @wvp_contract_sql = 'drop table wvp_user_role_legacy_contract';
        prepare wvp_contract_statement from @wvp_contract_sql;
        execute wvp_contract_statement;
        deallocate prepare wvp_contract_statement;
    end if;

    select greatest(coalesce(max(id), 0) + 1, 1) into v_next_id from wvp_role;
    set @wvp_contract_sql = concat('alter table wvp_role auto_increment = ', v_next_id);
    prepare wvp_contract_statement from @wvp_contract_sql;
    execute wvp_contract_statement;
    deallocate prepare wvp_contract_statement;

    select greatest(coalesce(max(id), 0) + 1, 1) into v_next_id from wvp_user;
    set @wvp_contract_sql = concat('alter table wvp_user auto_increment = ', v_next_id);
    prepare wvp_contract_statement from @wvp_contract_sql;
    execute wvp_contract_statement;
    deallocate prepare wvp_contract_statement;

    select greatest(coalesce(max(id), 0) + 1, 1) into v_next_id from wvp_user_api_key;
    set @wvp_contract_sql = concat('alter table wvp_user_api_key auto_increment = ', v_next_id);
    prepare wvp_contract_statement from @wvp_contract_sql;
    execute wvp_contract_statement;
    deallocate prepare wvp_contract_statement;
end //
delimiter ;

call wvp_contract_rbac_2_8_0();
drop procedure wvp_contract_rbac_2_8_0;
