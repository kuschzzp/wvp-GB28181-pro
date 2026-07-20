package com.genersoft.iot.vmp.conf.security.dto;

import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LoginUser implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

    private final long id;
    private final String username;
    private String password;
    private final boolean accountNonLocked;
    private final boolean enabled;

    public LoginUser(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.accountNonLocked = !user.isLocked();
        this.enabled = !user.isDeleted() && user.getStatus() == 0;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期，过期无法验证
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 指定用户是否解锁，锁定的用户无法进行身份验证
     * <p>
     * 密码锁定
     * </p>
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /**
     * 指示是否已过期的用户的凭据(密码)，过期的凭据防止认证
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 用户是否被启用或禁用。禁用的用户无法进行身份验证。
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 认证完成后，擦除密码
     */
    @Override
    public void eraseCredentials() {
        password = null;
    }


    public long getId() {
        return id;
    }

}
