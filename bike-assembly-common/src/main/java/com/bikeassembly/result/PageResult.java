package com.bikeassembly.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回结构（V2.2 增强版）。
 * <p>
 * 字段名兼容前端 PageResult&lt;T&gt;：
 * <ul>
 *   <li>{@code current} = 当前页码（前端字段）</li>
 *   <li>{@code page}    = 当前页码（别名，便于后端可读性）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始）—— 前端用此字段 */
    private Long current;

    /** 当前页码（别名） */
    private Long page;

    /** 每页条数 */
    private Long size;

    /** 总页数 */
    private Long pages;

    // ========== 工厂方法 ==========

    /**
     * 从分页原始值构造（通用工厂方法）。
     *
     * @param records 当前页数据（VO 列表）
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页条数
     * @param pages   总页数
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size, long pages) {
        PageResult<T> r = new PageResult<>();
        r.records = records != null ? records : Collections.emptyList();
        r.total = total;
        r.page = page;
        r.current = page;
        r.size = size;
        r.pages = pages;
        return r;
    }

    /**
     * 手动构造（自动计算总页数）。
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        long pages = size > 0 ? (long) Math.ceil((double) total / size) : 0;
        return of(records, total, page, size, pages);
    }
}
