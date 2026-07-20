package com.genersoft.iot.vmp.storager.dao.dto;

public class Menu {

    private long id;
    private long parentId;
    private String menuName;
    private String menuType;
    private String routeName;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private int orderNum;
    private int visible;
    private int status;
    private boolean cacheable;
    private boolean external;
    private boolean systemMenu;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getParentId() { return parentId; }
    public void setParentId(long parentId) { this.parentId = parentId; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getPerms() { return perms; }
    public void setPerms(String perms) { this.perms = perms; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }
    public int getVisible() { return visible; }
    public void setVisible(int visible) { this.visible = visible; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public boolean isCacheable() { return cacheable; }
    public void setCacheable(boolean cacheable) { this.cacheable = cacheable; }
    public boolean isExternal() { return external; }
    public void setExternal(boolean external) { this.external = external; }
    public boolean isSystemMenu() { return systemMenu; }
    public void setSystemMenu(boolean systemMenu) { this.systemMenu = systemMenu; }
}
