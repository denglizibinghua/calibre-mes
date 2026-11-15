package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户。
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser getByUsername(@Param("username") String username);

    /**
     * 根据用户名查询用户（XML 映射，明确列出所有列）。
     */
    SysUser findByUsername(@Param("username") String username);

    /**
     * 根据微信 openid 查询用户。
     */
    SysUser findByOpenid(@Param("openid") String openid);

    Long selectDepartmentRootUnitId(@Param("userId") Long userId);
}
