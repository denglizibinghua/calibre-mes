package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.SysMenuDTO;
import com.bikeassembly.entity.SysMenu;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SysMenuMapper;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.SysMenuService;
import com.bikeassembly.vo.SysMenuVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;


@Service
@Slf4j
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Override
    public SysMenuVO saveMenu(SysMenuDTO dto) {
        SysMenu entity = toEntity(dto);
        sysMenuMapper.insert(entity);
        log.info("新增菜单成功：menuId={}, menuName={}", entity.getMenuId(), entity.getMenuName());

        return toVO(entity);
    }

    @Override
    public SysMenuVO updateMenu(Long id, SysMenuDTO dto) {
        SysMenu existing = sysMenuMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        SysMenu entity = toEntity(dto);
        entity.setMenuId(id);
        sysMenuMapper.updateById(entity);
        log.info("修改菜单成功：menuId={}", id);

        SysMenu updated = sysMenuMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SysMenuVO getMenuById(Long id) {
        SysMenu entity = sysMenuMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SysMenuVO> listMenus(Long page, Long size) {
        Page<SysMenu> pageParam = new Page<>(page, size);
        IPage<SysMenu> pageResult = sysMenuMapper.selectPage(pageParam, null);
        List<SysMenuVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteMenu(Long id) {
        SysMenu existing = sysMenuMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        sysMenuMapper.deleteById(id);
        log.info("删除菜单成功：menuId={}", id);
    }

    @Override
    public List<SysMenuVO> listAllPerms() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getMenuType, "F")
               .eq(SysMenu::getStatus, "0");
        List<SysMenu> menus = sysMenuMapper.selectList(wrapper);
        return menus.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<SysMenuVO> listMenuTree() {
        List<SysMenu> allMenus = sysMenuMapper.selectList(null);
        List<SysMenuVO> allVos = allMenus.stream().map(this::toVO).collect(Collectors.toList());

        // 按 parentId 分组
        Map<Long, List<SysMenuVO>> childrenMap = allVos.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenuVO::getParentId));

        // 构建树：把子节点挂到父节点下
        List<SysMenuVO> webRoots = new ArrayList<>();
        List<SysMenuVO> appRoots = new ArrayList<>();
        for (SysMenuVO vo : allVos) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0) {
                if ("M".equals(vo.getMenuType())) {
                    appRoots.add(vo);
                } else {
                    webRoots.add(vo);
                }
            }
            // 挂载子节点
            List<SysMenuVO> children = childrenMap.get(vo.getMenuId());
            if (children != null) {
                vo.setChildren(children);
            }
        }

        // 按端分组返回，避免小程序端菜单与 Web 端菜单在同一层级出现重名/错层
        List<SysMenuVO> result = new ArrayList<>();
        if (!webRoots.isEmpty()) {
            result.add(buildPlatformRoot(PLATFORM_WEB_ROOT_ID, "Web 管理端", webRoots));
        }
        if (!appRoots.isEmpty()) {
            result.add(buildPlatformRoot(PLATFORM_APP_ROOT_ID, "小程序端", appRoots));
        }
        return result;
    }

    /** 授权树中“Web 管理端”虚拟分组节点 ID（负数，不落库） */
    private static final Long PLATFORM_WEB_ROOT_ID = -1L;

    /** 授权树中“小程序端”虚拟分组节点 ID（负数，不落库） */
    private static final Long PLATFORM_APP_ROOT_ID = -2L;

    /**
     * 构建端分组虚拟根节点，仅用于角色授权树展示，不参与落库。
     */
    private SysMenuVO buildPlatformRoot(Long menuId, String menuName, List<SysMenuVO> children) {
        SysMenuVO root = new SysMenuVO();
        root.setMenuId(menuId);
        root.setParentId(0L);
        root.setMenuName(menuName);
        root.setMenuType("G");
        root.setStatus("0");
        root.setOrderNum(0);
        root.setChildren(children);
        return root;
    }

    // ===== 动态菜单/权限查询（管理端专用） =====

    @Override
    public List<SysMenuVO> getCurrentUserMenuTree(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        // 判断是否为管理员
        SysUser user = sysUserMapper.selectById(userId);
        List<SysMenu> menus;

        if (user != null && user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null && "admin".equals(role.getRoleKey())) {
                // 管理员返回所有已启用的目录和菜单页
                LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
                wrapper.in(SysMenu::getMenuType, "D", "C")
                       .eq(SysMenu::getStatus, "0")
                       .orderByAsc(SysMenu::getOrderNum)
                       .orderByAsc(SysMenu::getMenuId);
                menus = sysMenuMapper.selectList(wrapper);
            } else {
                // 普通用户：按角色过滤
                menus = sysMenuMapper.selectVisibleMenusByUserId(userId);
                menus = fillMissingParentCatalogs(menus);
            }
        } else {
            menus = sysMenuMapper.selectVisibleMenusByUserId(userId);
            menus = fillMissingParentCatalogs(menus);
        }

        if (menus.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysMenuVO> allVos = menus.stream().map(this::toVO).collect(Collectors.toList());
        return buildTree(allVos);
    }

    /**
     * 补齐角色菜单中缺失的父目录（D）节点。
     * 角色授权种子/历史数据可能只分配了菜单页（C），漏配父目录（D），
     * 导致 buildTree 时页面因父节点不在结果集中而被静默丢弃。
     */
    private List<SysMenu> fillMissingParentCatalogs(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return menus;
        }
        List<SysMenu> result = new ArrayList<>(menus);
        Set<Long> presentIds = result.stream()
                .map(SysMenu::getMenuId)
                .collect(Collectors.toSet());

        boolean changed = true;
        while (changed) {
            changed = false;
            Set<Long> missingIds = new HashSet<>();
            for (SysMenu menu : result) {
                Long parentId = menu.getParentId();
                if (parentId != null && parentId != 0 && !presentIds.contains(parentId)) {
                    missingIds.add(parentId);
                }
            }
            if (missingIds.isEmpty()) {
                break;
            }
            List<SysMenu> parents = sysMenuMapper.selectBatchIds(missingIds);
            for (SysMenu parent : parents) {
                if (parent == null || presentIds.contains(parent.getMenuId())) {
                    continue;
                }
                if (!"0".equals(parent.getStatus())) {
                    continue;
                }
                if (!"D".equals(parent.getMenuType()) && !"C".equals(parent.getMenuType())) {
                    continue;
                }
                result.add(parent);
                presentIds.add(parent.getMenuId());
                changed = true;
            }
        }
        return result;
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        // 管理员返回所有权限码
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null && "admin".equals(role.getRoleKey())) {
                // 页面查看权限同样是权限码，不能只返回按钮(F)，否则前端与后端授权不一致。
                LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SysMenu::getStatus, "0")
                       .isNotNull(SysMenu::getPerms)
                       .ne(SysMenu::getPerms, "");
                List<SysMenu> allPerms = sysMenuMapper.selectList(wrapper);
                return allPerms.stream()
                        .map(SysMenu::getPerms)
                        .filter(p -> p != null && !p.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
            }
        }

        // 普通用户从角色关联查询
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        return menus.stream()
                .filter(m -> "0".equals(m.getStatus()))
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 将平铺菜单列表构建为树形结构，按 orderNum 升序排列。
     */
    private List<SysMenuVO> buildTree(List<SysMenuVO> allVos) {
        Map<Long, List<SysMenuVO>> childrenMap = allVos.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenuVO::getParentId));

        List<SysMenuVO> roots = new ArrayList<>();
        for (SysMenuVO vo : allVos) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0) {
                roots.add(vo);
            }
            List<SysMenuVO> children = childrenMap.get(vo.getMenuId());
            if (children != null) {
                vo.setChildren(children);
            }
        }

        // 对 roots 和每个节点的 children 按 orderNum 排序
        roots.sort((a, b) -> {
            int sortA = a.getOrderNum() != null ? a.getOrderNum() : 0;
            int sortB = b.getOrderNum() != null ? b.getOrderNum() : 0;
            return Integer.compare(sortA, sortB);
        });

        return roots;
    }

    private SysMenu toEntity(SysMenuDTO dto) {
        SysMenu entity = new SysMenu();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SysMenuVO toVO(SysMenu entity) {
        SysMenuVO vo = new SysMenuVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
