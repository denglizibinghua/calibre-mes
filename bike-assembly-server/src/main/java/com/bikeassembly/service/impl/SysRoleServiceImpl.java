package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SysRoleDTO;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.service.SysRoleMenuService;
import com.bikeassembly.service.SysRoleService;
import com.bikeassembly.vo.SysRoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;


@Service
@Slf4j
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    @Override
    public SysRoleVO saveRole(SysRoleDTO dto) {
        checkRoleKeyUnique(dto.getRoleKey(), null);

        SysRole entity = toEntity(dto);
        sysRoleMapper.insert(entity);
        log.info("新增角色成功：roleId={}, roleKey={}", entity.getRoleId(), entity.getRoleKey());

        // 保存菜单/权限关联
        if (dto.getMenuIds() != null) {
            sysRoleMenuService.assignMenus(entity.getRoleId(), dto.getMenuIds());
        }

        return toVO(entity);
    }

    @Override
    public SysRoleVO updateRole(Long id, SysRoleDTO dto) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkRoleKeyUnique(dto.getRoleKey(), id);

        SysRole entity = toEntity(dto);
        entity.setRoleId(id);
        entity.setDataScope(dto.getDataScope());
        sysRoleMapper.updateById(entity);
        log.info("修改角色成功：roleId={}", id);

        // 更新菜单/权限关联
        if (dto.getMenuIds() != null) {
            sysRoleMenuService.assignMenus(id, dto.getMenuIds());
        }

        SysRole updated = sysRoleMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SysRoleVO getRoleById(Long id) {
        SysRole entity = sysRoleMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SysRoleVO> listRoles(Long page, Long size) {
        Page<SysRole> pageParam = new Page<>(page, size);
        IPage<SysRole> pageResult = sysRoleMapper.selectPage(pageParam, null);
        List<SysRoleVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteRole(Long id) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        sysRoleMapper.deleteById(id);
        log.info("删除角色成功：roleId={}", id);
    }

    private void checkRoleKeyUnique(String roleKey, Long excludeId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleKey, roleKey);
        if (excludeId != null) {
            wrapper.ne(SysRole::getRoleId, excludeId);
        }
        if (sysRoleMapper.selectCount(wrapper) > 0) {
            throw new BaseException("角色编码已存在：" + roleKey);
        }
    }

    private SysRole toEntity(SysRoleDTO dto) {
        SysRole entity = new SysRole();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SysRoleVO toVO(SysRole entity) {
        SysRoleVO vo = new SysRoleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
