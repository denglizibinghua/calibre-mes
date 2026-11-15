package com.bikeassembly.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 角色菜单/权限变更事件。
 * 当角色的菜单分配发生变更时发布，用于触发权限缓存清理。
 */
@Getter
public class RoleMenusChangedEvent extends ApplicationEvent {

    private final Long roleId;

    public RoleMenusChangedEvent(Object source, Long roleId) {
        super(source);
        this.roleId = roleId;
    }
}
