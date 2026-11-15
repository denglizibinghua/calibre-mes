package com.bikeassembly.warehouse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bikeassembly.dto.WmsStockDocDTO;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.entity.WmsInventory;
import com.bikeassembly.entity.WmsLocation;
import com.bikeassembly.entity.WmsStockFlow;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.WmsInventoryMapper;
import com.bikeassembly.mapper.WmsLocationMapper;
import com.bikeassembly.mapper.WmsStockFlowMapper;
import com.bikeassembly.service.WmsStockDocService;
import com.bikeassembly.vo.WmsStockDocVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存单据执行集成测试：入库/出库/负库存拒绝/流水。
 */
@SpringBootTest
@ActiveProfiles("test")
class WmsStockDocExecuteIntegrationTest extends com.bikeassembly.AbstractMySqlContainerIntegrationTest {

    @Autowired
    private WmsStockDocService wmsStockDocService;
    @Autowired
    private WmsStockFlowMapper wmsStockFlowMapper;
    @Autowired
    private WmsInventoryMapper wmsInventoryMapper;
    @Autowired
    private BaseItemMapper baseItemMapper;
    @Autowired
    private WmsLocationMapper wmsLocationMapper;

    @Test
    void inbound_execute_increasesInventoryAndWritesFlow() {
        long seed = System.currentTimeMillis();
        BaseItem item = createItem(seed);
        WmsLocation loc = createLocation(seed);

        WmsStockDocVO vo = createDoc("DOC-IN-" + seed, "I",
                "[{\"itemId\":" + item.getItemId() + ",\"locationId\":" + loc.getLocationId() + ",\"qty\":10}]");

        WmsStockDocVO executed = wmsStockDocService.executeStockDoc(vo.getDocId());
        assertEquals("2", executed.getStatus(), "执行后单据状态应为已执行(2)");

        WmsInventory inv = findInventory(item.getItemId(), loc.getLocationId());
        assertEquals(0, new BigDecimal("10").compareTo(inv.getQty()), "入库后库存应为10");
        assertEquals(1L, flowCount(vo.getDocId()), "应生成1条流水");
    }

    @Test
    void outbound_insufficientStock_rejectedAndRolledBack() {
        long seed = System.currentTimeMillis();
        BaseItem item = createItem(seed);
        WmsLocation loc = createLocation(seed);
        WmsStockDocVO in = createDoc("DOC-IN-" + seed, "I",
                "[{\"itemId\":" + item.getItemId() + ",\"locationId\":" + loc.getLocationId() + ",\"qty\":5}]");
        wmsStockDocService.executeStockDoc(in.getDocId());

        WmsStockDocVO out = createDoc("DOC-OUT-" + seed, "O",
                "[{\"itemId\":" + item.getItemId() + ",\"locationId\":" + loc.getLocationId() + ",\"qty\":20}]");

        assertThrows(BaseException.class, () -> wmsStockDocService.executeStockDoc(out.getDocId()),
                "库存不足应抛业务异常");

        WmsInventory inv = findInventory(item.getItemId(), loc.getLocationId());
        assertEquals(0, new BigDecimal("5").compareTo(inv.getQty()), "失败后库存应保持不变");
        assertEquals(0L, flowCount(out.getDocId()), "失败单据不应产生流水");
        assertEquals("0", wmsStockDocService.getWmsStockDocById(out.getDocId()).getStatus(),
                "失败单据状态应仍为草稿(0)");
    }

    @Test
    void outbound_execute_deductsInventory() {
        long seed = System.currentTimeMillis();
        BaseItem item = createItem(seed);
        WmsLocation loc = createLocation(seed);
        WmsStockDocVO in = createDoc("DOC-IN-" + seed, "I",
                "[{\"itemId\":" + item.getItemId() + ",\"locationId\":" + loc.getLocationId() + ",\"qty\":10}]");
        wmsStockDocService.executeStockDoc(in.getDocId());

        WmsStockDocVO out = createDoc("DOC-OUT-" + seed, "O",
                "[{\"itemId\":" + item.getItemId() + ",\"locationId\":" + loc.getLocationId() + ",\"qty\":4}]");
        wmsStockDocService.executeStockDoc(out.getDocId());

        WmsInventory inv = findInventory(item.getItemId(), loc.getLocationId());
        assertEquals(0, new BigDecimal("6").compareTo(inv.getQty()), "出库4后库存应为6");
        assertEquals(2L, flowCount(out.getDocId()) + flowCount(in.getDocId()), "入+出共2条流水");
    }

    private WmsStockDocVO createDoc(String docNo, String docType, String detailsJson) {
        WmsStockDocDTO dto = new WmsStockDocDTO();
        dto.setDocNo(docNo);
        dto.setDocType(docType);
        dto.setStatus("0");
        dto.setDetailsJson(detailsJson);
        return wmsStockDocService.saveWmsStockDoc(dto);
    }

    private BaseItem createItem(long seed) {
        BaseItem item = new BaseItem();
        item.setItemCode("IT-" + seed);
        item.setItemName("测试物料" + seed);
        item.setItemType("1");
        item.setUnit("件");
        item.setStatus("0");
        baseItemMapper.insert(item);
        return item;
    }

    private WmsLocation createLocation(long seed) {
        WmsLocation loc = new WmsLocation();
        loc.setParentId(0L);
        loc.setLocationCode("LOC-" + seed);
        loc.setLocationName("测试库位" + seed);
        loc.setLocationType("L");
        loc.setStatus("0");
        wmsLocationMapper.insert(loc);
        return loc;
    }

    private WmsInventory findInventory(Long itemId, Long locationId) {
        WmsInventory inv = wmsInventoryMapper.selectOne(new LambdaQueryWrapper<WmsInventory>()
                .eq(WmsInventory::getItemId, itemId)
                .eq(WmsInventory::getLocationId, locationId)
                .last("LIMIT 1"));
        assertTrue(inv != null, "库存记录应存在");
        return inv;
    }

    private long flowCount(Long docId) {
        Long count = wmsStockFlowMapper.selectCount(new LambdaQueryWrapper<WmsStockFlow>()
                .eq(WmsStockFlow::getDocId, docId));
        return count == null ? 0 : count;
    }
}
