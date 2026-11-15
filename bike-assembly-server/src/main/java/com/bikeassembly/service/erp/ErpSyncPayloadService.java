package com.bikeassembly.service.erp;

import com.bikeassembly.entity.ProWorkOrder;
import com.bikeassembly.entity.WmsStockDoc;
import com.bikeassembly.mapper.ProWorkOrderMapper;
import com.bikeassembly.mapper.WmsStockDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ERP 同步负载构建：按业务类型从数据库读取最新业务快照。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErpSyncPayloadService {

    private final ProWorkOrderMapper proWorkOrderMapper;
    private final WmsStockDocMapper wmsStockDocMapper;

    /**
     * 构建同步负载。
     *
     * @param bizType 业务类型（WORK_ORDER / INVENTORY）
     * @param bizId   业务ID
     * @return 业务快照（业务不存在时返回仅含 bizType/bizId 的最小负载）
     */
    public Map<String, Object> buildPayload(String bizType, String bizId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", bizType);
        payload.put("bizId", bizId);

        try {
            if ("WORK_ORDER".equals(bizType)) {
                ProWorkOrder wo = proWorkOrderMapper.selectById(Long.valueOf(bizId));
                if (wo != null) {
                    payload.put("workOrderId", wo.getWorkOrderId());
                    payload.put("workOrderNo", wo.getWorkOrderNo());
                    payload.put("orderNo", wo.getOrderNo());
                    payload.put("itemId", wo.getItemId());
                    payload.put("planQty", wo.getPlanQty());
                    payload.put("unit", wo.getUnit());
                    payload.put("status", wo.getStatus());
                }
            } else if ("INVENTORY".equals(bizType)) {
                WmsStockDoc doc = wmsStockDocMapper.selectById(Long.valueOf(bizId));
                if (doc != null) {
                    payload.put("docId", doc.getDocId());
                    payload.put("docNo", doc.getDocNo());
                    payload.put("docType", doc.getDocType());
                    payload.put("status", doc.getStatus());
                    payload.put("detailsJson", doc.getDetailsJson());
                }
            }
        } catch (Exception e) {
            log.warn("ERP 同步负载构建异常：bizType={}, bizId={}, error={}",
                    bizType, bizId, e.getMessage());
        }
        return payload;
    }
}
