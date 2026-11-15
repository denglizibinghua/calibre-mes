<template>
  <PageContainer title="设备维修报表" description="按条件汇总设备维修数据并支持导出。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间" value-format="YYYY-MM-DD HH:mm:ss" />
      <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" />
      <el-input v-model="form.equipmentId" placeholder="设备ID" clearable />
      <el-input v-model="form.status" placeholder="状态" clearable />
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
      <el-table-column prop="repairId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="equipmentCode" label="设备编码" width="110" show-overflow-tooltip />
      <el-table-column prop="equipmentName" label="设备名称" min-width="110" show-overflow-tooltip />
      <el-table-column prop="faultDesc" label="故障描述" min-width="130" show-overflow-tooltip />
      <el-table-column prop="reportUser" label="报修人" width="90" />
      <el-table-column prop="repairUser" label="维修人" width="90" />
      <el-table-column prop="status" label="状态" width="60" />
      <el-table-column prop="reportTime" label="报修时间" width="140" class-name="mono-num" />
      <el-table-column prop="finishTime" label="完成时间" width="140" class-name="mono-num" />
      <el-table-column prop="resultDesc" label="维修结果" min-width="130" show-overflow-tooltip />
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
import { getEquipmentRepairReport, exportEquipmentRepairReport, type EquipmentRepairReportRow } from '@/api/report'

const tableData = ref<EquipmentRepairReportRow[]>([])
const loading = ref(false)
const exporting = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 20 })
const form = reactive<Record<string, unknown>>({ startTime: '', endTime: '', equipmentId: '', status: '' })

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const params = { ...form } as Record<string, unknown>
    Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
    params.page = query.page
    params.size = query.size
    const res = await getEquipmentRepairReport(params)
    tableData.value = res.records ?? []
    total.value = res.total ?? 0
  } catch { /* handled */ }
  finally { loading.value = false }
}

function reset() { form.startTime = ''; form.endTime = ''; form.equipmentId = ''; form.status = ''; query.page = 1; fetchData() }

async function doExport() {
  exporting.value = true
  try {
    const params = { ...form } as Record<string, unknown>
    Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
    const res = await exportEquipmentRepairReport(params)
    downloadBlob(res.data as Blob, '设备维修报表.xlsx')
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
