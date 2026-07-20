package com.genersoft.iot.vmp.auth.menu;

import com.genersoft.iot.vmp.auth.menu.dto.MenuRequest;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;

import java.util.List;

public interface SystemMenuService {

    List<Menu> list();

    Menu detail(long id);

    long create(MenuRequest request);

    void update(long id, MenuRequest request);

    void delete(long id);
}
