package com.genersoft.iot.vmp.jt1078.dao;

import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JTTerminalStableIdMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JTTerminalMapper mapper;

    @BeforeEach
    void createSchema() {
        jdbcTemplate.execute("drop table if exists wvp_jt_terminal");
        jdbcTemplate.execute("""
                create table wvp_jt_terminal (
                    id integer primary key,
                    phone_number varchar(50) not null unique,
                    province_id varchar(20),
                    terminal_id varchar(50),
                    province_text varchar(100),
                    city_id varchar(20),
                    city_text varchar(100),
                    maker_id varchar(50),
                    model varchar(100),
                    plate_color integer,
                    plate_no varchar(100),
                    longitude double,
                    latitude double,
                    register_time varchar(30),
                    status boolean,
                    update_time varchar(30)
                )
                """);
        jdbcTemplate.update("""
                insert into wvp_jt_terminal
                    (id, phone_number, plate_no, plate_color, status, update_time)
                values
                    (31, 'stable-owner', 'OWNER-OLD', 1, false, 'old'),
                    (32, 'replacement-phone', 'OTHER-OLD', 1, false, 'old')
                """);
    }

    @Test
    void updateUsesAuthorizedInternalIdInsteadOfReplaceablePhoneNumber() {
        JTDevice update = new JTDevice();
        update.setId(31);
        update.setPhoneNumber("replacement-phone");
        update.setPlateNo("OWNER-UPDATED");
        update.setPlateColor(2);
        update.setStatus(true);
        update.setUpdateTime("new");

        mapper.updateDevice(update);

        assertEquals("OWNER-UPDATED", plateNo(31));
        assertEquals("OTHER-OLD", plateNo(32));
    }

    private String plateNo(int id) {
        return jdbcTemplate.queryForObject(
                "select plate_no from wvp_jt_terminal where id = ?",
                String.class,
                id);
    }
}
