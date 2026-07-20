package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.conf.security.dto.LoginUser;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 用户登录认证逻辑
 */
@Slf4j
@Component
public class DefaultUserDetailsServiceImpl implements UserDetailsService {

    private static final Pattern LEGACY_MD5 = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final IUserService userService;

    public DefaultUserDetailsServiceImpl(IUserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (StringUtils.isBlank(username)) {
            log.info("登录用户：{} 不存在", username);
            throw new UsernameNotFoundException("登录用户：" + username + " 不存在");
        }

        // 查出密码
        User user = userService.getUserByUsername(username);
        if (user == null) {
            log.info("登录用户：{} 不存在", username);
            throw new UsernameNotFoundException("登录用户：" + username + " 不存在");
        }
        user.setPassword(adaptStoredPassword(user));
        return new LoginUser(user);
    }

    private String adaptStoredPassword(User user) {
        String password = user.getPassword();
        if (password != null && user.getPasswordVersion() == 0 && LEGACY_MD5.matcher(password).matches()) {
            return "{legacy-md5}" + password.toLowerCase(Locale.ROOT);
        }
        return password;
    }
}
