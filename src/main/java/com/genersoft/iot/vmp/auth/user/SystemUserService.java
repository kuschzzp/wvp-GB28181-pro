package com.genersoft.iot.vmp.auth.user;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.AdminUpdateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeUserStatusRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeOwnPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.ResetPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.UpdateOwnProfileRequest;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.github.pagehelper.PageInfo;

import java.util.Optional;
import java.util.Set;

public interface SystemUserService {

    PageInfo<UserSummary> query(UserQuery query, DepartmentScope scope);

    Optional<UserSummary> findVisible(long userId, DepartmentScope scope);

    long create(CreateUserRequest request, DepartmentScope scope);

    void update(long userId, AdminUpdateUserRequest request, DepartmentScope scope);

    void changeStatus(long userId, ChangeUserStatusRequest request, DepartmentScope scope);

    void assignRoles(long userId, Set<Long> roleIds, DepartmentScope scope);

    void delete(long userId, DepartmentScope scope);

    void resetPassword(long userId, ResetPasswordRequest request, DepartmentScope scope);

    void changePushKey(long userId, String pushKey, DepartmentScope scope);

    void updateOwnProfile(UpdateOwnProfileRequest request);

    void changeOwnPassword(ChangeOwnPasswordRequest request);
}
