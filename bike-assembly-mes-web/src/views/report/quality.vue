<template>
  <PageContainer title="质量检验报表" description="按条件汇总质量检验数据并支持导出。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间" value-format="YYYY-MM-DD HH:mm:ss" />
      <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" />
      <el-input v-model="form.result" placeholder="检验结果" clearable />
      <el-input v-model="form.workOrderId" placeholder="工单ID" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-copy">共 {{ total }} 条记录</div>
      <div class="table-toolbar-actions">
        <el-button :loading="exporting" @click="doExport">导出 Excel</el-button>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="inspectionId" label="ID" width="70" class-name="mono-num" />
      <el-table-column prop="inspectionNo" label="质检单号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="workOrderNo" label="工单编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="itemCode" label="物料编码" width="110" show-overflow-tooltip />
      <el-table-column prop="itemName" label="物料名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="result" label="检验结果" width="90" />
      <el-table-column prop="inspector" label="检验人" width="100" />
      <el-table-column prop="inspectionTime" label="检验时间" width="160" class-name="mono-num" />
      <el-table-column prop="status" label="状态" width="70" />
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[15, 30, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchData"
        @size-change="fetchData"
      />
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getQualityReport, exportQualityReport, type QualityReportRow } from '@/api/report'

const tableData = ref<QualityReportRow[]>([])
const loading = ref(false)
const exporting = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 20 })
const form = reactive<Record<string, unknown>>({ startTime: '', endTime: '', result: '', workOrderId: '' })

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const params = { ...form } as Record<string, unknown>
    Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
    params.page = query.page
    params.size = query.size
    const res = await getQualityReport(params)
    tableData.value = res.records ?? []
    total.value = res.total ?? 0
  } catch { /* handled */ }
  finally { loading.value = false }
}

function reset() { form.startTime = ''; form.endTime = ''; form.result = ''; form.workOrderId = ''; query.page = 1; fetchData() }

async function doExport() {
  exporting.value = true
  try {
    const params = { ...form } as Record<string, unknown>
    Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
    const res = await exportQualityReport(params)
    downloadBlob(res.data as Blob, '质检报表.xlsx')
    ElMessage.success('导出成功')
  } catch { /* handled */ }
  finally { exporting.value = false }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = fileName; a.click()
  URL.revokeObjectURL(url)
}
</script>
