package com.genersoft.iot.vmp.auth.menu;

import com.genersoft.iot.vmp.auth.menu.dto.MenuRequest;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
public class SystemMenuController {

    private final SystemMenuService menuService;

    public SystemMenuController(SystemMenuService menuService) {
        this.menuService = menuService;
    }

    @PreAuthorize("@authz.require('system:menu:list')")
    @GetMapping
    public List<Menu> list() {
        return menuService.list();
    }

    @PreAuthorize("@authz.require('system:menu:list')")
    @GetMapping("/{id}")
    public Menu detail(@PathVariable long id) {
        return menuService.detail(id);
    }

    @PreAuthorize("@authz.require('system:menu:add')")
    @PostMapping
    public long create(@Valid @RequestBody MenuRequest request) {
        return menuService.create(request);
    }

    @PreAuthorize("@authz.require('system:menu:edit')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id,
            @Valid @RequestBody MenuRequest request) {
        menuService.update(id, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:menu:remove')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        menuService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
