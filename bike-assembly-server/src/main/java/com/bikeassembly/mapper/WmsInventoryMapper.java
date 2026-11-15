package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.WmsInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface WmsInventoryMapper extends BaseMapper<WmsInventory> {

    /** @deprecated V2.2 已替换为 {@link #selectPageByCondition(Page, String, String, String)} */
    @Deprecated
    List<WmsInventory> selectListByCondition(@Param("itemCode") String itemCode,
                                              @Param("itemName") String itemName,
                                              @Param("warehouseName") String warehouseName);

    /**
     * 动态条件分页查询库存（V2.2 新增）。
     * <p>
     * MyBatis-Plus 分页插件会自动拦截第一个 Page 参数并添加 COUNT + LIMIT。
     */
    IPage<WmsInventory> selectPageByCondition(Page<WmsInventory> page,
                                               @Param("itemCode") String itemCode,
                                               @Param("itemName") String itemName,
                                               @Param("warehouseName") String warehouseName);

    /**
     * 跨表检查库存被外键引用的次数。
     * 当前暂无表直接以外键引用 wms_inventory.inventory_id，
     * 保留此方法便于未来扩展。
     */
    int countReferences(@Param("inventoryId") Long inventoryId);

    /** 原子加库存（入库/移库入），返回影响行数 */
    int addStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);

    /** 原子扣减库存（出库/移库出），仅当库存充足时成功，返回影响行数 */
    int deductStock(@Param("inventoryId") Long inventoryId, @Param("qty") BigDecimal qty);
}
