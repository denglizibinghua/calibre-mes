package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.WmsStockDocDTO;
import com.bikeassembly.dto.message.InventoryMessageDTO;
import com.bikeassembly.entity.WmsStockDoc;
import com.bikeassembly.entity.WmsInventory;
import com.bikeassembly.entity.WmsStockFlow;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.WmsStockDocMapper;
import com.bikeassembly.mapper.WmsInventoryMapper;
import com.bikeassembly.mapper.WmsStockFlowMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.MessageProducerService;
import com.bikeassembly.service.WmsStockDocService;
import com.bikeassembly.service.erp.ErpSyncPublisher;
import com.bikeassembly.vo.WmsStockDocVO;
import com.bikeassembly.ws.WebSocketNotifier;
import com.bikeassembly.ws.WsMessageDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WmsStockDocServiceImpl extends ServiceImpl<WmsStockDocMapper, WmsStockDoc> implements WmsStockDocService {

    @Autowired
    private WmsStockDocMapper wmsStockDocMapper;
    @Autowired
    private ObjectProvider<MessageProducerService> messageProducerProvider;
    @Autowired
    private WmsInventoryMapper wmsInventoryMapper;
    @Autowired
    private WmsStockFlowMapper wmsStockFlowMapper;
    @Autowired
    private WebSocketNotifier webSocketNotifier;
    @Autowired
    private ErpSyncPublisher erpSyncPublisher;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public WmsStockDocVO saveWmsStockDoc(WmsStockDocDTO dto) {
        validateEditableStatus(dto.getStatus());
        WmsStockDoc entity = toEntity(dto);
        entity.setDocId(null);
        entity.setStatus(dto.getStatus() == null ? "0" : dto.getStatus());
        wmsStockDocMapper.insert(entity);
        log.info("新增库存单据成功：docId={}, docNo={}", entity.getDocId(), entity.getDocNo());

        // 发送 RabbitMQ 库存消息（RabbitMQ 未启用时优雅降级）
        notifyInventory(entity, WsMessageDTO.EVENT_CREATE);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockDocVO updateWmsStockDoc(Long id, WmsStockDocDTO dto) {
        WmsStockDoc existing = wmsStockDocMapper.selectByIdForUpdate(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        requireEditable(existing);
        validateEditableStatus(dto.getStatus());
        WmsStockDoc entity = toEntity(dto);
        entity.setDocId(id);
        entity.setCreateTime(existing.getCreateTime());
        wmsStockDocMapper.updateById(entity);
        log.info("修改库存单据成功：docId={}", id);

        WmsStockDoc updated = wmsStockDocMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public WmsStockDocVO getWmsStockDocById(Long id) {
        WmsStockDoc entity = wmsStockDocMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<WmsStockDocVO> listWmsStockDocs(Long page, Long size,
                                                       String docNo, String docType,
                                                       String status, String keyword) {
        Page<WmsStockDoc> pageParam = new Page<>(page, size);
        IPage<WmsStockDoc> pageResult = wmsStockDocMapper.selectPageByCondition(
                pageParam, docNo, docType, status, keyword);
        List<WmsStockDocVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWmsStockDoc(Long id) {
        WmsStockDoc existing = wmsStockDocMapper.selectByIdForUpdate(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        requireEditable(existing);
        wmsStockDocMapper.deleteById(id);
        log.info("删除库存单据成功：docId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockDocVO executeStockDoc(Long id) {
        WmsStockDoc doc = wmsStockDocMapper.selectByIdForUpdate(id);
        if (doc == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        if (doc.getStatus() == null || (!"0".equals(doc.getStatus()) && !"1".equals(doc.getStatus()))) {
            throw new BaseException("当前单据状态不可执行，仅草稿/已提交可执行");
        }
        String docType = doc.getDocType();
        if (docType == null || (!"I".equals(docType) && !"O".equals(docType) && !"T".equals(docType))) {
            throw new BaseException("单据类型不合法");
        }

        List<StockDocDetail> details = parseDetails(doc.getDetailsJson());
        if (details.isEmpty()) {
            throw new BaseException("单据明细不能为空");
        }

        Long operatorId = BaseContext.getCurrentId();
        for (StockDocDetail d : details) {
            if (d.itemId == null) {
                throw new BaseException("明细缺少物料");
            }
            if (d.locationId == null) {
                throw new BaseException("明细缺少库位");
            }
            if (d.qty == null || d.qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BaseException("明细数量必须大于0");
            }
            switch (docType) {
                case "I" -> inbound(doc, d, operatorId);
                case "O" -> outbound(doc, d, operatorId);
                case "T" -> transfer(doc, d, operatorId);
                default -> throw new BaseException("单据类型不合法");
            }
        }

        // 全部明细成功后，单据置为已执行
        WmsStockDoc update = new WmsStockDoc();
        update.setDocId(id);
        update.setStatus("2");
        if (wmsStockDocMapper.updateById(update) != 1) {
            throw new BaseException(MessageConstant.DATA_CONCURRENT_MODIFIED);
        }
        doc.setStatus("2");
        log.info("执行库存单据成功：docId={}, docNo={}, details={}", id, doc.getDocNo(), details.size());
        notifyInventory(doc, WsMessageDTO.EVENT_EXECUTE);
        erpSyncPublisher.publish("INVENTORY", id, "O");
        return toVO(wmsStockDocMapper.selectById(id));
    }

    private void requireEditable(WmsStockDoc doc) {
        if (!"0".equals(doc.getStatus()) && !"1".equals(doc.getStatus())) {
            throw new BaseException("已执行或已关闭的库存单据不可修改或删除");
        }
    }

    private void validateEditableStatus(String status) {
        if (status != null && !"0".equals(status) && !"1".equals(status)) {
            throw new BaseException("普通编辑仅允许草稿或已提交状态，请使用执行操作完成单据");
        }
    }

    private void inbound(WmsStockDoc doc, StockDocDetail d, Long operatorId) {
        WmsInventory inv = findInventory(d.itemId, d.locationId, d.batchNo);
        BigDecimal before;
        Long inventoryId;
        if (inv == null) {
            WmsInventory created = new WmsInventory();
            created.setItemId(d.itemId);
            created.setLocationId(d.locationId);
            created.setBatchNo(blankToNull(d.batchNo));
            created.setQty(d.qty);
            created.setLockedQty(BigDecimal.ZERO);
            wmsInventoryMapper.insert(created);
            inventoryId = created.getInventoryId();
            before = BigDecimal.ZERO;
        } else {
            inventoryId = inv.getInventoryId();
            before = inv.getQty() == null ? BigDecimal.ZERO : inv.getQty();
            wmsInventoryMapper.addStock(inventoryId, d.qty);
        }
        saveFlow(doc, inventoryId, d.itemId, d.locationId, blankToNull(d.batchNo),
                d.qty, before, before.add(d.qty), "I", operatorId);
    }

    private void outbound(WmsStockDoc doc, StockDocDetail d, Long operatorId) {
        WmsInventory inv = findInventory(d.itemId, d.locationId, d.batchNo);
        if (inv == null) {
            throw new BaseException("库存不存在：物料/库位/批次未找到对应库存记录");
        }
        BigDecimal before = inv.getQty() == null ? BigDecimal.ZERO : inv.getQty();
        int rows = wmsInventoryMapper.deductStock(inv.getInventoryId(), d.qty);
        if (rows == 0) {
            throw new BaseException("库存不足：当前库存 " + before.toPlainString()
                    + "，扣减 " + d.qty.toPlainString());
        }
        saveFlow(doc, inv.getInventoryId(), d.itemId, d.locationId, blankToNull(d.batchNo),
                d.qty.negate(), before, before.subtract(d.qty), "O", operatorId);
    }

    private void transfer(WmsStockDoc doc, StockDocDetail d, Long operatorId) {
        if (d.targetLocationId == null) {
            throw new BaseException("移库明细需指定目标库位 targetLocationId");
        }
        if (d.targetLocationId.equals(d.locationId)) {
            throw new BaseException("移库目标库位不能与来源库位相同");
        }
        // 来源出库
        WmsInventory src = findInventory(d.itemId, d.locationId, d.batchNo);
        if (src == null) {
            throw new BaseException("移库来源库存不存在：物料/库位/批次未找到对应库存记录");
        }
        BigDecimal srcBefore = src.getQty() == null ? BigDecimal.ZERO : src.getQty();
        if (wmsInventoryMapper.deductStock(src.getInventoryId(), d.qty) == 0) {
            throw new BaseException("移库来源库存不足：当前库存 " + srcBefore.toPlainString());
        }
        saveFlow(doc, src.getInventoryId(), d.itemId, d.locationId, blankToNull(d.batchNo),
                d.qty.negate(), srcBefore, srcBefore.subtract(d.qty), "T", operatorId);
        // 目标入库
        WmsInventory dst = findInventory(d.itemId, d.targetLocationId, d.batchNo);
        BigDecimal dstBefore;
        Long dstId;
        if (dst == null) {
            WmsInventory created = new WmsInventory();
            created.setItemId(d.itemId);
            created.setLocationId(d.targetLocationId);
            created.setBatchNo(blankToNull(d.batchNo));
            created.setQty(d.qty);
            created.setLockedQty(BigDecimal.ZERO);
            wmsInventoryMapper.insert(created);
            dstId = created.getInventoryId();
            dstBefore = BigDecimal.ZERO;
        } else {
            dstId = dst.getInventoryId();
            dstBefore = dst.getQty() == null ? BigDecimal.ZERO : dst.getQty();
            wmsInventoryMapper.addStock(dstId, d.qty);
        }
        saveFlow(doc, dstId, d.itemId, d.targetLocationId, blankToNull(d.batchNo),
                d.qty, dstBefore, dstBefore.add(d.qty), "T", operatorId);
    }

    private WmsInventory findInventory(Long itemId, Long locationId, String batchNo) {
        LambdaQueryWrapper<WmsInventory> wrapper = new LambdaQueryWrapper<WmsInventory>()
                .eq(WmsInventory::getItemId, itemId)
                .eq(WmsInventory::getLocationId, locationId);
        if (batchNo == null || batchNo.isBlank()) {
            wrapper.isNull(WmsInventory::getBatchNo);
        } else {
            wrapper.eq(WmsInventory::getBatchNo, batchNo);
        }
        wrapper.last("LIMIT 1");
        return wmsInventoryMapper.selectOne(wrapper);
    }

    private void saveFlow(WmsStockDoc doc, Long inventoryId, Long itemId, Long locationId,
                          String batchNo, BigDecimal changeQty, BigDecimal before,
                          BigDecimal after, String flowType, Long operatorId) {
        WmsStockFlow flow = new WmsStockFlow();
        flow.setDocId(doc.getDocId());
        flow.setDocNo(doc.getDocNo());
        flow.setInventoryId(inventoryId);
        flow.setItemId(itemId);
        flow.setLocationId(locationId);
        flow.setBatchNo(batchNo);
        flow.setChangeQty(changeQty);
        flow.setBeforeQty(before);
        flow.setAfterQty(after);
        flow.setFlowType(flowType);
        flow.setRelatedOrderNo(doc.getRelatedOrderNo());
        flow.setCreateBy(operatorId);
        flow.setCreateTime(LocalDateTime.now());
        wmsStockFlowMapper.insert(flow);
    }

    private List<StockDocDetail> parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = OBJECT_MAPPER.readTree(detailsJson);
            if (!arr.isArray()) {
                throw new BaseException("单据明细格式错误，应为数组");
            }
            List<StockDocDetail> list = new ArrayList<>();
            for (JsonNode node : arr) {
                StockDocDetail d = new StockDocDetail();
                d.itemId = node.has("itemId") && !node.get("itemId").isNull()
                        ? node.get("itemId").asLong() : null;
                d.locationId = node.has("locationId") && !node.get("locationId").isNull()
                        ? node.get("locationId").asLong() : null;
                d.qty = node.has("qty") && !node.get("qty").isNull()
                        ? node.get("qty").decimalValue() : null;
                d.batchNo = node.has("batchNo") && !node.get("batchNo").isNull()
                        ? node.get("batchNo").asText() : null;
                d.targetLocationId = node.has("targetLocationId") && !node.get("targetLocationId").isNull()
                        ? node.get("targetLocationId").asLong() : null;
                list.add(d);
            }
            return list;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("单据明细解析失败");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 单据明细行 */
    private static class StockDocDetail {
        Long itemId;
        Long locationId;
        Long targetLocationId;
        BigDecimal qty;
        String batchNo;
    }

    private WmsStockDoc toEntity(WmsStockDocDTO dto) {
        WmsStockDoc entity = new WmsStockDoc();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private WmsStockDocVO toVO(WmsStockDoc entity) {
        WmsStockDocVO vo = new WmsStockDocVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 库存单据变更通知：RabbitMQ 启用时发送 MQ（由消费者异步推送 WebSocket），
     * 未启用时降级为同步推送（管理端库存/单据页）。发送失败不影响主业务。
     */
    private void notifyInventory(WmsStockDoc entity, String eventType) {
        com.bikeassembly.utils.AfterCommitNotification.execute(() -> sendInventoryNotification(entity, eventType));
    }

    private void sendInventoryNotification(WmsStockDoc entity, String eventType) {
        MessageProducerService producer = messageProducerProvider == null
                ? null : messageProducerProvider.getIfAvailable();
        if (producer != null) {
            try {
                InventoryMessageDTO message = InventoryMessageDTO.builder()
                        .inventoryId(entity.getDocId())
                        .itemId(null)
                        .locationId(null)
                        .quantity(null)
                        .eventType(eventType)
                        .createTime(entity.getCreateTime() != null
                                ? entity.getCreateTime() : LocalDateTime.now())
                        .build();
                producer.sendInventoryMessage(message);
                return;
            } catch (Exception e) {
                log.warn("库存消息发送失败（不影响主业务）：docId={}, error={}",
                        entity.getDocId(), e.getMessage(), e);
            }
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("docId", entity.getDocId());
        data.put("docNo", entity.getDocNo());
        data.put("docType", entity.getDocType());
        data.put("status", entity.getStatus());
        webSocketNotifier.pushToAdmin(WsMessageDTO.BIZ_INVENTORY, eventType, data);
    }
}
