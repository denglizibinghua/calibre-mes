package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.BaseItemDTO;
import com.bikeassembly.entity.BaseItem;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.BaseItemMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.service.BaseItemService;
import com.bikeassembly.vo.BaseItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.stream.Collectors;

@Service
@Slf4j
public class BaseItemServiceImpl extends ServiceImpl<BaseItemMapper, BaseItem> implements BaseItemService {

    @Autowired
    private BaseItemMapper baseItemMapper;

    @Override
    public BaseItemVO saveItem(BaseItemDTO dto) {
        // 1. 校验 item_code 唯一性
        checkItemCodeUnique(dto.getItemCode(), null);

        // 2. DTO → Entity
        BaseItem entity = toEntity(dto);

        // 3. 入库
        baseItemMapper.insert(entity);
        log.info("新增物料成功：itemId={}, itemCode={}", entity.getItemId(), entity.getItemCode());

        // 4. Entity → VO 返回
        return toVO(entity);
    }

    @Override
    public BaseItemVO updateItem(Long id, BaseItemDTO dto) {
        // 1. 检查记录是否存在
        BaseItem existing = baseItemMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // 2. 校验 item_code 唯一性（排除自身）
        checkItemCodeUnique(dto.getItemCode(), id);

        // 3. DTO → Entity + 设置 ID
        BaseItem entity = toEntity(dto);
        entity.setItemId(id);

        // 4. 更新
        baseItemMapper.updateById(entity);
        log.info("修改物料成功：itemId={}", id);

        // 5. 重新查询最新数据 → Entity → VO 返回
        BaseItem updated = baseItemMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public BaseItemVO getItemById(Long id) {
        BaseItem entity = baseItemMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<BaseItemVO> listItems(Long page, Long size, String itemCode, String itemName) {
        LambdaQueryWrapper<BaseItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(itemCode != null && !itemCode.isBlank(), BaseItem::getItemCode, itemCode)
               .like(itemName != null && !itemName.isBlank(), BaseItem::getItemName, itemName);
        Page<BaseItem> pageParam = new Page<>(page, size);
        IPage<BaseItem> pageResult = baseItemMapper.selectPage(pageParam, wrapper);
        List<BaseItemVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteItem(Long id) {
        // 1. 检查记录是否存在
        BaseItem existing = baseItemMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        // 2. 检查外键引用
        int refCount = baseItemMapper.countReferences(id);
        if (refCount > 0) {
            throw new BaseException("该物料被 " + refCount + " 条业务数据引用，无法删除");
        }

        // 3. 删除
        baseItemMapper.deleteById(id);
        log.info("删除物料成功：itemId={}", id);
    }

    // ========== 私有方法 ==========

    /**
     * 校验物料编码唯一性。
     *
     * @param itemCode  物料编码
     * @param excludeId 排除的物料 ID（新增时传 null）
     */
    private void checkItemCodeUnique(String itemCode, Long excludeId) {
        LambdaQueryWrapper<BaseItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseItem::getItemCode, itemCode);
        if (excludeId != null) {
            wrapper.ne(BaseItem::getItemId, excludeId);
        }
        if (baseItemMapper.selectCount(wrapper) > 0) {
            throw new BaseException("物料编码已存在：" + itemCode);
        }
    }

    private BaseItem toEntity(BaseItemDTO dto) {
        BaseItem entity = new BaseItem();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private BaseItemVO toVO(BaseItem entity) {
        BaseItemVO vo = new BaseItemVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
