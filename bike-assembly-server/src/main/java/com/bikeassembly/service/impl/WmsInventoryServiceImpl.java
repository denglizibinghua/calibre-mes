package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.WmsInventoryDTO;
import com.bikeassembly.entity.WmsInventory;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.mapper.WmsInventoryMapper;
import com.bikeassembly.mapper.WmsLocationMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.WmsInventoryService;
import com.bikeassembly.vo.WmsInventoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class WmsInventoryServiceImpl extends ServiceImpl<WmsInventoryMapper, WmsInventory> implements WmsInventoryService {

    @Autowired
    private WmsInventoryMapper wmsInventoryMapper;
    @Autowired
    private BaseItemMapper baseItemMapper;
    @Autowired
    private WmsLocationMapper wmsLocationMapper;

    @Override
    public WmsInventoryVO saveWmsInventory(WmsInventoryDTO dto) {
        // 1. 校验库存唯一性（同一物料 + 同一库位 + 同一批次 = 重复）
        checkInventoryUnique(dto.getItemId(), dto.getLocationId(), dto.getBatchNo(), null);

        // 2. FK 预校验：物料
        if (dto.getItemId() != null
                && baseItemMapper.selectById(dto.getItemId()) == null) {
            throw new BaseException("物料不存在");
        }
        // 3. FK 预校验：库位
        if (dto.getLocationId() != null
                && wmsLocationMapper.selectById(dto.getLocationId()) == null) {
            throw new BaseException("库位不存在");
        }

        WmsInventory entity = toEntity(dto);
        wmsInventoryMapper.insert(entity);
        log.info("新增库存成功：inventoryId={}, itemId={}, locationId={}", entity.getInventoryId(), entity.getItemId(), entity.getLocationId());

        return toVO(entity);
    }

    @Override
    public WmsInventoryVO updateWmsInventory(Long id, WmsInventoryDTO dto) {
        WmsInventory existing = wmsInventoryMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // 校验库存唯一性（排除自身）
        checkInventoryUnique(dto.getItemId(), dto.getLocationId(), dto.getBatchNo(), id);

        // FK 预校验：物料
        if (dto.getItemId() != null
                && baseItemMapper.selectById(dto.getItemId()) == null) {
            throw new BaseException("物料不存在");
        }
        // FK 预校验：库位
        if (dto.getLocationId() != null
                && wmsLocationMapper.selectById(dto.getLocationId()) == null) {
            throw new BaseException("库位不存在");
        }

        WmsInventory entity = toEntity(dto);
        entity.setInventoryId(id);
        wmsInventoryMapper.updateById(entity);
        log.info("修改库存成功：inventoryId={}", id);

        WmsInventory updated = wmsInventoryMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public WmsInventoryVO getWmsInventoryById(Long id) {
        WmsInventory entity = wmsInventoryMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<WmsInventoryVO> listWmsInventories(String itemCode, String itemName, String warehouseName,
                                                          Long page, Long size) {
        // V3：始终使用 JOIN 查询，确保 VO 包含物料编码/名称、库位编码/名称等关联展示字段
        IPage<WmsInventory> result = wmsInventoryMapper.selectPageByCondition(
                new Page<>(page, size), itemCode, itemName, warehouseName);

        List<WmsInventoryVO> records = result.getRecords()
                .stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public void deleteWmsInventory(Long id) {
        // 1. 检查记录是否存在
        WmsInventory existing = wmsInventoryMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // 2. 检查外键引用
        int refCount = wmsInventoryMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该库存记录被 " + refCount + " 条业务数据引用，无法删除");
        }

        // 3. 删除
        wmsInventoryMapper.deleteById(id);
        log.info("删除库存成功：inventoryId={}", id);
    }

    // ========== 私有方法 ==========

    /**
     * 校验库存唯一性：同一物料 + 同一库位 + 同一批次 = 重复记录。
     *
     * @param itemId     物料ID
     * @param locationId 库位ID
     * @param batchNo    批次号（可为 null）
     * @param excludeId  排除的库存ID（新增时传 null）
     */
    private void checkInventoryUnique(Long itemId, Long locationId, String batchNo, Long excludeId) {
        LambdaQueryWrapper<WmsInventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WmsInventory::getItemId, itemId)
               .eq(WmsInventory::getLocationId, locationId)
               .eq(WmsInventory::getBatchNo, batchNo);
        if (excludeId != null) {
            wrapper.ne(WmsInventory::getInventoryId, excludeId);
        }
        if (wmsInventoryMapper.selectCount(wrapper) > 0) {
            throw new BaseException("相同物料、库位、批次的库存记录已存在");
        }
    }

    private WmsInventory toEntity(WmsInventoryDTO dto) {
        WmsInventory entity = new WmsInventory();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private WmsInventoryVO toVO(WmsInventory entity) {
        WmsInventoryVO vo = new WmsInventoryVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
