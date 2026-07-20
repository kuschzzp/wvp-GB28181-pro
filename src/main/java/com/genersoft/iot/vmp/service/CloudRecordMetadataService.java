package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBinding;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceBindingResolver;
import com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSourceResolutionException;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
public class CloudRecordMetadataService {

    private final CloudRecordSourceBindingResolver bindingResolver;
    private final CloudRecordServiceMapper mapper;

    public CloudRecordMetadataService(
            CloudRecordSourceBindingResolver bindingResolver,
            CloudRecordServiceMapper mapper) {
        this.bindingResolver = Objects.requireNonNull(
                bindingResolver, "bindingResolver must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Transactional
    public boolean addResolved(CloudRecordItem item) {
        Objects.requireNonNull(item, "cloud record item must not be null");
        CloudRecordSourceBinding binding;
        try {
            binding = bindingResolver.resolveForNewRecord(
                    item.getCallId(), item.getApp(), item.getStream());
        } catch (CloudRecordSourceResolutionException e) {
            log.warn(
                    "[云录像来源固化失败] app: {}, stream: {}, callId: {}, reason: {}",
                    item.getApp(), item.getStream(), item.getCallId(), e.getMessage());
            return false;
        }
        binding.applyTo(item);
        int inserted = mapper.add(item);
        if (inserted != 1) {
            throw new IllegalStateException(
                    "云录像元数据写入数量异常: " + inserted);
        }
        return true;
    }
}
