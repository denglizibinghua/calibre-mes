package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SysRoleDTO;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.SysRoleVO;

public interface SysRoleService extends IService<SysRole> {

    SysRoleVO saveRole(SysRoleDTO dto);

    SysRoleVO updateRole(Long id, SysRoleDTO dto);

    SysRoleVO getRoleById(Long id);

    PageResult<SysRoleVO> listRoles(Long page, Long size);

    void deleteRole(Long id);
}
