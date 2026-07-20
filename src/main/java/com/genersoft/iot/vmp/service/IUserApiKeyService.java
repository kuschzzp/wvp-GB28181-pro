package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyCreatedResponse;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyQuery;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeySummary;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeOptions;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyCommand;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.github.pagehelper.PageInfo;

public interface IUserApiKeyService {

    ApiKeyCreatedResponse create(
            long ownerId,
            CreateApiKeyCommand command,
            DepartmentScope scope);

    ApiKeyCreatedResponse rotate(long ownerId, long keyId, DepartmentScope scope);

    PageInfo<ApiKeySummary> pageOwnKeys(
            long ownerId,
            ApiKeyQuery query,
            DepartmentScope scope);

    ApiKeyScopeOptions scopeOptions(long ownerId, DepartmentScope scope);

    void changeStatus(
            long ownerId,
            long keyId,
            ApiKeyStatus status,
            DepartmentScope scope);

    void updateRemark(long ownerId, long keyId, String remark, DepartmentScope scope);

    void updateScope(
            long ownerId,
            long keyId,
            ApiKeyScopeCommand command,
            DepartmentScope scope);

    void delete(long ownerId, long keyId, DepartmentScope scope);
}
