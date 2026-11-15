package com.bikeassembly.service;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.mapper.BaseWorkUnitMapper;
import com.bikeassembly.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一解析数据范围。AI、报表和异步任务不得自行解释 ALL/DEPT/SELF。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;
    private final BaseWorkUnitMapper baseWorkUnitMapper;

    public Scope current() {
        return resolve(BaseContext.getCurrentId());
    }

    public Scope resolve(Long userId) {
        if (userId == null) {
            return Scope.denied();
        }
        String scope = permissionService.getUserDataScope(userId);
        if ("ALL".equals(scope)) {
            return new Scope(userId, "ALL", null, List.of(), true);
        }
        if ("DEPT".equals(scope)) {
            Long root = sysUserMapper.selectDepartmentRootUnitId(userId);
            if (root == null) {
                // A department scope without an assigned production unit must
                // never fall through to an unfiltered query.
                return Scope.denied();
            }
            List<Long> units = baseWorkUnitMapper.selectDescendantUnitIds(root);
            if (units == null || units.isEmpty()) {
                return Scope.denied();
            }
            return new Scope(userId, "DEPT", root, units, false);
        }
        if ("SELF".equals(scope)) {
            return new Scope(userId, "SELF", null, List.of(), false);
        }
        // Unknown or missing scope values are deny-by-default.
        return Scope.denied();
    }

    public record Scope(Long userId, String name, Long rootUnitId,
                        List<Long> unitIds, boolean all) {
        public static Scope denied() {
            return new Scope(null, "DENIED", null, List.of(), false);
        }

        public boolean department() {
            return "DEPT".equals(name) && !unitIds.isEmpty();
        }

        public boolean restricted() {
            return "SELF".equals(name) || ("DEPT".equals(name) && unitIds.isEmpty());
        }
    }
}
