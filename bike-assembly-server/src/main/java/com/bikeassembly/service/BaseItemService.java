package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseItemDTO;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseItemVO;

/**
 * 物料 Service 接口。
 * 基础 CRUD 继承自 IService&lt;BaseItem&gt;，此处定义自定义业务方法。
 */
public interface BaseItemService extends IService<BaseItem> {

    /**
     * 新增物料
     */
    BaseItemVO saveItem(BaseItemDTO dto);

    /**
     * 修改物料
     */
    BaseItemVO updateItem(Long id, BaseItemDTO dto);

    /**
     * 按 ID 查询物料
     */
    BaseItemVO getItemById(Long id);

    /**
     * 分页查询物料
     */
    PageResult<BaseItemVO> listItems(Long page, Long size, String itemCode, String itemName);

    /**
     * 删除物料（含外键引用检查）
     */
    void deleteItem(Long id);
}
