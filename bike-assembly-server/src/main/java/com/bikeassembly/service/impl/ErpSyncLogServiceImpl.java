package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.ErpSyncLogDTO;
import com.bikeassembly.entity.ErpSyncLog;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.ErpSyncLogMapper;
import com.bikeassembly.service.ErpSyncLogService;
import com.bikeassembly.service.erp.ErpSyncClient;
import com.bikeassembly.service.erp.ErpSyncPayloadService;
import com.bikeassembly.vo.ErpSyncLogVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ErpSyncLogServiceImpl extends ServiceImpl<ErpSyncLogMapper, ErpSyncLog> implements ErpSyncLogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ErpSyncLogMapper erpSyncLogMapper;
    @Autowired
    private ErpSyncClient erpSyncClient;
    @Autowired
    private ErpSyncPayloadService erpSyncPayloadService;

    @Override
    public ErpSyncLogVO saveSyncLog(ErpSyncLogDTO dto) {
        ErpSyncLog entity = toEntity(dto);
        erpSyncLogMapper.insert(entity);
        log.info("新增ERP同步日志成功：syncId={}", entity.getSyncId());

        return toVO(entity);
    }

    @Override
    public ErpSyncLogVO updateSyncLog(Long id, ErpSyncLogDTO dto) {
        ErpSyncLog existing = erpSyncLogMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        ErpSyncLog entity = toEntity(dto);
        entity.setSyncId(id);
        erpSyncLogMapper.updateById(entity);
        log.info("修改ERP同步日志成功：syncId={}", id);

        ErpSyncLog updated = erpSyncLogMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public ErpSyncLogVO getSyncLogById(Long id) {
        ErpSyncLog entity = erpSyncLogMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<ErpSyncLogVO> listSyncLogs(Long page, Long size) {
        Page<ErpSyncLog> pageParam = new Page<>(page, size);
        IPage<ErpSyncLog> pageResult = erpSyncLogMapper.selectPage(pageParam, null);
        List<ErpSyncLogVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteSyncLog(Long id) {
        ErpSyncLog existing = erpSyncLogMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        erpSyncLogMapper.deleteById(id);
        log.info("删除ERP同步日志成功：syncId={}", id);
    }

    @Override
    public void recordSync(String bizType, String bizId, String syncDirection,
                           String syncStatus, String requestData, String responseData) {
        erpSyncLogMapper.upsertByBiz(bizType, bizId, syncDirection, syncStatus, requestData, responseData);
        log.info("ERP同步日志已记录：bizType={}, bizId={}, status={}", bizType, bizId, syncStatus);
    }

    @Override
    public ErpSyncLogVO retrySync(Long id) {
        ErpSyncLog existing = erpSyncLogMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        String bizType = existing.getBizType();
        String bizId = existing.getBizId();
        Map<String, Object> payload = erpSyncPayloadService.buildPayload(bizType, bizId);
        ErpSyncClient.ErpSyncResult result = erpSyncClient.sync(
                new ErpSyncClient.ErpSyncRequest(bizType, bizId, payload));

        existing.setSyncStatus(result.success() ? "2" : "3");
        existing.setRequestData(toJson(payload));
        existing.setResponseData(result.responseData());
        existing.setSyncTime(LocalDateTime.now());
        erpSyncLogMapper.updateById(existing);
        log.info("ERP同步重试完成：syncId={}, bizType={}, bizId={}, status={}",
                id, bizType, bizId, existing.getSyncStatus());
        return toVO(existing);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    private ErpSyncLog toEntity(ErpSyncLogDTO dto) {
        ErpSyncLog entity = new ErpSyncLog();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private ErpSyncLogVO toVO(ErpSyncLog entity) {
        ErpSyncLogVO vo = new ErpSyncLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
