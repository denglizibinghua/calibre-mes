package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.OperLog;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.context.BaseContext;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.AiGraphManagementService;
import com.bikeassembly.vo.GraphEdgeVO;
import com.bikeassembly.vo.GraphNodeVO;
import com.bikeassembly.vo.GraphOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 知识图谱管理接口。
 *
 * <p>读取图谱节点/关系与统计信息，并支持删除节点及其关系、删除指定关系。
 * 所有查询均按当前用户可见范围隔离：PUBLIC，或本人私有数据。</p>
 */
@RestController
@RequestMapping("/admin/ai/graph")
@RequiredArgsConstructor
public class AiGraphController {
    private static final String NOT_CONFIGURED =
            "知识图谱未配置（NOT_CONFIGURED），请启用 ai.neo4j.enabled 并配置连接信息";

    private final AiGraphManagementService graphService;

    /**
     * 图谱概览：节点/关系总数、类型分布、公共/私有数量。
     */
    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/overview")
    public Result<GraphOverviewVO> overview() {
        if (!graphService.isEnabled()) {
            return Result.error(NOT_CONFIGURED);
        }
        return Result.success(graphService.overview(BaseContext.getCurrentId()));
    }

    /**
     * 分页查询图谱节点，支持按实体类型和名称关键字筛选。
     */
    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/nodes")
    public Result<PageResult<GraphNodeVO>> nodes(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "50") Long size) {
        if (!graphService.isEnabled()) {
            return Result.error(NOT_CONFIGURED);
        }
        return Result.success(graphService.listNodes(
                BaseContext.getCurrentId(), type, keyword, page, size));
    }

    /**
     * 分页查询图谱关系，支持按节点 ID 和关系类型筛选。
     */
    @RequirePermission(PermissionConstant.ADMIN_AI_VIEW)
    @GetMapping("/edges")
    public Result<PageResult<GraphEdgeVO>> edges(
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "50") Long size) {
        if (!graphService.isEnabled()) {
            return Result.error(NOT_CONFIGURED);
        }
        return Result.success(graphService.listEdges(
                BaseContext.getCurrentId(), nodeId, type, page, size));
    }

    /**
     * 删除指定节点及其全部关系。
     */
    @RequirePermission(PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE)
    @DeleteMapping("/nodes/{nodeId}")
    @OperLog(module = "AI助手", operType = "删除图谱节点")
    public Result<Void> deleteNode(@PathVariable String nodeId) {
        if (!graphService.isEnabled()) {
            return Result.error(NOT_CONFIGURED);
        }
        graphService.deleteNode(BaseContext.getCurrentId(), nodeId);
        return Result.success();
    }

    /**
     * 删除指定起止节点与类型的关系。
     */
    @RequirePermission(PermissionConstant.ADMIN_AI_KNOWLEDGE_MANAGE)
    @DeleteMapping("/edges")
    @OperLog(module = "AI助手", operType = "删除图谱关系")
    public Result<Void> deleteEdge(
            @RequestParam String fromId,
            @RequestParam String toId,
            @RequestParam String type) {
        if (!graphService.isEnabled()) {
            return Result.error(NOT_CONFIGURED);
        }
        graphService.deleteEdge(BaseContext.getCurrentId(), fromId, toId, type);
        return Result.success();
    }
}
