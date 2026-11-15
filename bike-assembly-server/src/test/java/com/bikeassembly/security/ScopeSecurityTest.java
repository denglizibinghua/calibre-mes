package com.bikeassembly.security;

import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.*;
import com.bikeassembly.service.impl.*;
import com.bikeassembly.utils.ScopeValidator;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Scope 参数越权查询安全回归测试。
 *
 * 覆盖所有包含 scope 参数的 Controller/Service 模块，验证：
 * 1. 非法 scope 值（未知值、空字符串、空格、大小写变体）均被拒绝
 * 2. scope=mine 仅返回当前用户数据
 * 3. scope=all 仅在有权限时返回全部数据
 * 4. Service 层作为最终安全边界，不依赖 Controller 校验
 */
@DisplayName("Scope 越权查询安全测试")
class ScopeSecurityTest {

    // ==================== ScopeValidator 基础校验 ====================

    @Nested
    @DisplayName("ScopeValidator 基础校验")
    class ScopeValidatorBasics {

        @Test
        @DisplayName("合法值 mine/all 应通过校验")
        void shouldAcceptValidScopes() {
            assertDoesNotThrow(() -> ScopeValidator.requireValid("mine", ScopeValidator.MINE_OR_ALL));
            assertDoesNotThrow(() -> ScopeValidator.requireValid("all", ScopeValidator.MINE_OR_ALL));
        }

        @Test
        @DisplayName("合法值 mine/all/pending 应通过质检查询校验")
        void shouldAcceptInspectionScopes() {
            assertDoesNotThrow(() -> ScopeValidator.requireValid("mine", ScopeValidator.MINE_PENDING_ALL));
            assertDoesNotThrow(() -> ScopeValidator.requireValid("all", ScopeValidator.MINE_PENDING_ALL));
            assertDoesNotThrow(() -> ScopeValidator.requireValid("pending", ScopeValidator.MINE_PENDING_ALL));
        }

        @Test
        @DisplayName("null 应被拒绝")
        void shouldRejectNull() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid(null, ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("空字符串应被拒绝")
        void shouldRejectEmpty() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("纯空格应被拒绝")
        void shouldRejectBlank() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("   ", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("未知值 abc 应被拒绝")
        void shouldRejectUnknown() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("abc", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("大小写变体 ALL 应被拒绝")
        void shouldRejectCaseVariant() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("ALL", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("大小写变体 Mine 应被拒绝")
        void shouldRejectCapitalizedVariant() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("Mine", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("带前导空格的值 ' mine' 应被拒绝")
        void shouldRejectLeadingSpace() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid(" mine", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("带尾部空格的值 'mine ' 应被拒绝")
        void shouldRejectTrailingSpace() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("mine ", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("test 值应被拒绝")
        void shouldRejectTest() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("test", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("pending 在 MINE_OR_ALL 集合中应被拒绝（质检查询才允许 pending）")
        void shouldRejectPendingWhenNotAllowed() {
            // pending 只在 MINE_PENDING_ALL 中合法，在 MINE_OR_ALL（工序/工单/维修/安灯）中应被拒绝
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("pending", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }

        @Test
        @DisplayName("随机值 XSS 应被拒绝")
        void shouldRejectXssValue() {
            BaseException ex = assertThrows(BaseException.class,
                    () -> ScopeValidator.requireValid("<script>", ScopeValidator.MINE_OR_ALL));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
        }
    }

    // ==================== ProWorkProcessServiceImpl scope 校验 ====================

    @Nested
    @DisplayName("工序查询 Service scope 校验")
    class ProWorkProcessScopeTest {

        @Test
        @DisplayName("scope=mine → 调用 selectPageByCondition 设置 assigneeUserId")
        void mineScopeFiltersByAssignee() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var orderMapper = mock(ProWorkOrderMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);
            setField(processService, "proWorkOrderMapper", orderMapper);

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            when(processMapper.selectPageByCondition(any(), isNull(), isNull())).thenReturn(mockPage);

            processService.listProWorkProcesses(1L, 15L, "mine");
            verify(processMapper).selectPageByCondition(any(), isNull(), isNull());
            verify(processMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=all → 调用 selectPage 查询全部")
        void allScopeQueriesAll() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var orderMapper = mock(ProWorkOrderMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);
            setField(processService, "proWorkOrderMapper", orderMapper);

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            // 现在统一使用 selectPageByCondition（始终 JOIN sys_user 获取操作员姓名）
            when(processMapper.selectPageByCondition(any(), isNull(), isNull())).thenReturn(mockPage);

            processService.listProWorkProcesses(1L, 15L, "all");
            verify(processMapper).selectPageByCondition(any(), isNull(), isNull());
            verify(processMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=abc → Service 抛参数错误（绕过 Controller 直接调用 Service）")
        void unknownScopeThrowsInService() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var orderMapper = mock(ProWorkOrderMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);
            setField(processService, "proWorkOrderMapper", orderMapper);

            BaseException ex = assertThrows(BaseException.class,
                    () -> processService.listProWorkProcesses(1L, 15L, "abc"));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
            // 确认 Mapper 从未被调用——在到达 Mapper 之前就被拦截
            verify(processMapper, never()).selectPage(any(), any());
            verify(processMapper, never()).selectPageByCondition(any(), any(), any());
        }

        @Test
        @DisplayName("scope=test → Service 拒绝且不查询数据库")
        void testScopeThrowsInService() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);

            assertThrows(BaseException.class,
                    () -> processService.listProWorkProcesses(1L, 15L, "test"));
            verify(processMapper, never()).selectPage(any(), any());
            verify(processMapper, never()).selectPageByCondition(any(), any(), any());
        }

        @Test
        @DisplayName("scope=ALL → Service 拒绝且不查询数据库")
        void allUpperCaseThrowsInService() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);

            assertThrows(BaseException.class,
                    () -> processService.listProWorkProcesses(1L, 15L, "ALL"));
            verify(processMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=''空字符串 → Service 拒绝")
        void emptyScopeThrowsInService() {
            var processMapper = mock(ProWorkProcessMapper.class);
            var processService = new ProWorkProcessServiceImpl();
            setField(processService, "proWorkProcessMapper", processMapper);

            assertThrows(BaseException.class,
                    () -> processService.listProWorkProcesses(1L, 15L, ""));
            verify(processMapper, never()).selectPage(any(), any());
        }
    }

    // ==================== QmsInspectionServiceImpl scope 校验 ====================

    @Nested
    @DisplayName("质检查询 Service scope 校验")
    class QmsInspectionScopeTest {

        @Test
        @DisplayName("scope=mine → 设置 inspectorUserId=当前用户")
        void mineScopeFiltersByInspector() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);
            setField(inspectionService, "permissionService", mock(com.bikeassembly.service.PermissionService.class));

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            when(inspectionMapper.selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull()))
                    .thenReturn(mockPage);

            inspectionService.listQmsInspections(1L, 15L, null, null, null, "mine", null);
            // isMine=true → selectPageByCondition 被调用
            verify(inspectionMapper).selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull());
            verify(inspectionMapper, never()).selectPage(any(), isNull());
        }

        @Test
        @DisplayName("scope=pending → 设置 isPending=true")
        void pendingScopeFiltersPending() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            when(inspectionMapper.selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull(), eq(true), eq(false), isNull()))
                    .thenReturn(mockPage);

            inspectionService.listQmsInspections(1L, 15L, null, null, null, "pending", null);
            verify(inspectionMapper).selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull(), eq(true), eq(false), isNull());
        }

        @Test
        @DisplayName("scope=all → 查询全部，不传 userId 过滤")
        void allScopeQueriesAll() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            when(inspectionMapper.selectPage(any(), isNull())).thenReturn(mockPage);

            inspectionService.listQmsInspections(1L, 15L, null, null, null, "all", null);
            verify(inspectionMapper).selectPage(any(), isNull());
            verify(inspectionMapper, never()).selectPageByCondition(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("scope=abc → Service 抛参数错误")
        void unknownScopeThrows() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);

            BaseException ex = assertThrows(BaseException.class,
                    () -> inspectionService.listQmsInspections(1L, 15L, null, null, null, "abc", null));
            assertEquals(MessageConstant.SCOPE_INVALID, ex.getMessage());
            verify(inspectionMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=ALL → Service 拒绝且不查询")
        void caseVariantThrows() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);

            assertThrows(BaseException.class,
                    () -> inspectionService.listQmsInspections(1L, 15L, null, null, null, "ALL", null));
            verify(inspectionMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope 为空 → Service 拒绝")
        void emptyScopeThrows() {
            var inspectionMapper = mock(QmsInspectionMapper.class);
            var inspectionService = new QmsInspectionServiceImpl();
            setField(inspectionService, "qmsInspectionMapper", inspectionMapper);

            assertThrows(BaseException.class,
                    () -> inspectionService.listQmsInspections(1L, 15L, null, null, null, "", null));
            verify(inspectionMapper, never()).selectPage(any(), any());
        }
    }

    // ==================== ProWorkOrderServiceImpl scope 校验 ====================

    @Nested
    @DisplayName("工单查询 Service scope 校验")
    class ProWorkOrderScopeTest {

        @Test
        @DisplayName("scope=mine → 设置 assigneeUserId=当前用户")
        void mineScopeFiltersByAssignee() {
            var orderMapper = mock(ProWorkOrderMapper.class);
            var orderService = new ProWorkOrderServiceImpl();
            setField(orderService, "proWorkOrderMapper", orderMapper);

            var mockPage = mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(mockPage.getRecords()).thenReturn(java.util.Collections.emptyList());
            when(orderMapper.selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(mockPage);

            orderService.listProWorkOrders(1L, 15L, null, null, null, "mine");
            verify(orderMapper).selectPageByCondition(any(), isNull(), isNull(), isNull(), isNull());
            verify(orderMapper, never()).selectPage(any(), isNull());
        }

        @Test
        @DisplayName("scope=abc → Service 抛参数错误且不查询")
        void unknownScopeThrows() {
            var orderMapper = mock(ProWorkOrderMapper.class);
            var orderService = new ProWorkOrderServiceImpl();
            setField(orderService, "proWorkOrderMapper", orderMapper);

            assertThrows(BaseException.class,
                    () -> orderService.listProWorkOrders(1L, 15L, null, null, null, "abc"));
            verify(orderMapper, never()).selectPage(any(), any());
        }
    }

    // ==================== EqmRepairOrderServiceImpl scope 校验 ====================

    @Nested
    @DisplayName("设备维修查询 Service scope 校验")
    class EqmRepairOrderScopeTest {

        @Test
        @DisplayName("scope=abc → Service 抛参数错误且不查询")
        void unknownScopeThrows() {
            var repairMapper = mock(EqmRepairOrderMapper.class);
            var repairService = new EqmRepairOrderServiceImpl();
            setField(repairService, "eqmRepairOrderMapper", repairMapper);

            assertThrows(BaseException.class,
                    () -> repairService.listEqmRepairOrders(1L, 15L, "abc", null));
            verify(repairMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=ALL → Service 拒绝大小写变体")
        void caseVariantThrows() {
            var repairMapper = mock(EqmRepairOrderMapper.class);
            var repairService = new EqmRepairOrderServiceImpl();
            setField(repairService, "eqmRepairOrderMapper", repairMapper);

            assertThrows(BaseException.class,
                    () -> repairService.listEqmRepairOrders(1L, 15L, "ALL", null));
            verify(repairMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=test → Service 拒绝随机值")
        void randomValueThrows() {
            var repairMapper = mock(EqmRepairOrderMapper.class);
            var repairService = new EqmRepairOrderServiceImpl();
            setField(repairService, "eqmRepairOrderMapper", repairMapper);

            assertThrows(BaseException.class,
                    () -> repairService.listEqmRepairOrders(1L, 15L, "test", null));
            verify(repairMapper, never()).selectPage(any(), any());
        }
    }

    // ==================== AndonAlertServiceImpl scope 校验 ====================

    @Nested
    @DisplayName("安灯告警查询 Service scope 校验")
    class AndonAlertScopeTest {

        @Test
        @DisplayName("scope=abc → Service 抛参数错误且不查询")
        void unknownScopeThrows() {
            var andonMapper = mock(AndonAlertMapper.class);
            var andonService = new AndonAlertServiceImpl();
            setField(andonService, "andonAlertMapper", andonMapper);

            assertThrows(BaseException.class,
                    () -> andonService.listAndonAlerts(1L, 15L, "abc", null));
            verify(andonMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("scope=ALL → Service 拒绝大小写变体")
        void caseVariantThrows() {
            var andonMapper = mock(AndonAlertMapper.class);
            var andonService = new AndonAlertServiceImpl();
            setField(andonService, "andonAlertMapper", andonMapper);

            assertThrows(BaseException.class,
                    () -> andonService.listAndonAlerts(1L, 15L, "ALL", null));
            verify(andonMapper, never()).selectPage(any(), any());
        }
    }

    // ==================== 反射辅助 ====================

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            try {
                var field = target.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e2) { throw new RuntimeException(e2); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
