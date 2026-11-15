<template>
  <PageContainer title="操作日志" description="查询系统操作审计与异常记录。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.module" placeholder="模块（本地筛选）" clearable />
      <el-input v-model="searchForm.operUser" placeholder="操作人（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增操作日志
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="logId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="module" label="模块" width="120" />
      <!-- operType 使用英文编码: INSERT/UPDATE/DELETE/QUERY/EXPORT/OTHER -->
      <el-table-column prop="operType" label="操作类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.operType === 'INSERT' ? 'success' : row.operType === 'DELETE' ? 'danger' : row.operType === 'UPDATE' ? 'warning' : 'info'"
            size="small"
          >
            {{ operTypeLabel(row.operType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operUser" label="操作人" width="120" />
      <el-table-column prop="operIp" label="IP 地址" width="140" />
      <el-table-column prop="requestMethod" label="请求方式" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.requestMethod === 'GET' ? 'success' : row.requestMethod === 'POST' ? 'warning' : row.requestMethod === 'DELETE' ? 'danger' : 'info'"
            size="small"
          >
            {{ row.requestMethod || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '正常' : '异常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operTime" label="操作时间" width="180" class-name="mono-num" />
      <el-table-column prop="errorMsg" label="错误信息" min-width="160">
        <template #default="{ row }">
          <el-tooltip :content="row.errorMsg" placement="top" :show-after="500">
            <span class="text-ellipsis" :class="row.errorMsg ? 'error-text' : 'text-muted'">
              {{ row.errorMsg || '无' }}
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑操作日志" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该操作日志吗？" confirm-button-type="danger" @confirm="handleDelete(row.logId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除操作日志">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- V2.2 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[15, 30, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑操作日志' : '新增操作日志'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模块" prop="module">
          <el-input v-model="form.module" placeholder="请输入模块名称，如 用户管理" />
        </el-form-item>
        <!-- operType 使用英文编码，与 demo 数据 INSERT/UPDATE/DELETE 一致 -->
        <el-form-item label="操作类型" prop="operType">
          <el-select v-model="form.operType" placeholder="请选择操作类型" style="width: 100%">
            <el-option label="新增 (INSERT)" value="INSERT" />
            <el-option label="修改 (UPDATE)" value="UPDATE" />
            <el-option label="删除 (DELETE)" value="DELETE" />
            <el-option label="查询 (QUERY)"  value="QUERY" />
            <el-option label="导出 (EXPORT)" value="EXPORT" />
            <el-option label="其他 (OTHER)"  value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人" prop="operUser">
          <el-input v-model="form.operUser" placeholder="请输入操作人" />
        </el-form-item>
        <el-form-item label="IP 地址" prop="operIp">
          <el-input v-model="form.operIp" placeholder="请输入 IP 地址" />
        </el-form-item>
        <el-form-item label="请求方式" prop="requestMethod">
          <el-select v-model="form.requestMethod" placeholder="请选择请求方式" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间" prop="operTime">
          <el-date-picker
            v-model="form.operTime"
            type="datetime"
            placeholder="请选择操作时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="错误信息" prop="errorMsg">
          <el-input
            v-model="form.errorMsg"
            type="textarea"
            :rows="2"
            placeholder="请输入错误信息（选填）"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">异常</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getSysOperLogList, createSysOperLog, updateSysOperLog, deleteSysOperLog } from '@/api/sysOperLog'
import type { SysOperLog } from '@/types/api'

const tableData = ref<SysOperLog[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()
const total = ref(0)

const query = reactive({
  page: 1,
  size: 15
})

const searchForm = reactive({
  module: '',
  operUser: ''
})

const form = reactive<SysOperLog>({
  module: '',
  operType: '',
  operUser: '',
  operIp: '',
  requestMethod: '',
  status: '0',
  operTime: '',
  errorMsg: ''
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    module: [{ required: true, message: '请输入模块', trigger: 'blur' }],
    operType: [{ required: true, message: '请选择操作类型', trigger: 'change' }],
    operUser: [{ required: true, message: '请输入操作人', trigger: 'blur' }],
    operIp: [{ required: true, message: '请输入 IP 地址', trigger: 'blur' }],
    requestMethod: [{ required: true, message: '请选择请求方式', trigger: 'change' }],
    operTime: [{ required: true, message: '请选择操作时间', trigger: 'change' }],
    // errorMsg: 新增和编辑都选填（DB VARCHAR(255) NULL）
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

/** operType 编码 → 中文 */
function operTypeLabel(code: string): string {
  const map: Record<string, string> = {
    INSERT: '新增', UPDATE: '修改', DELETE: '删除',
    QUERY: '查询', EXPORT: '导出', OTHER: '其他'
  }
  return map[code] || code || '-'
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getSysOperLogList({ page: query.page, size: query.size })
    // V2.2: 后端返回 PageResult 包裹在 data 中
    const data = res?.data || res
    const allData: SysOperLog[] = Array.isArray(data?.records) ? data.records : []
    total.value = data?.total ?? 0
    // 本地筛选（仅当前页）
    const mod = searchForm.module.trim().toLowerCase()
    const user = searchForm.operUser.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchMod = !mod || (item.module || '').toLowerCase().includes(mod)
      const matchUser = !user || (item.operUser || '').toLowerCase().includes(user)
      return matchMod && matchUser
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.module = ''
  searchForm.operUser = ''
  handleSearch()
}

function resetForm() {
  form.module = ''
  form.operType = ''
  form.operUser = ''
  form.operIp = ''
  form.requestMethod = ''
  form.status = '0'
  form.operTime = ''
  form.errorMsg = ''
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: SysOperLog) {
  isEdit.value = true
  editId.value = row.logId
  form.module = row.module
  form.operType = row.operType
  form.operUser = row.operUser
  form.operIp = row.operIp
  form.requestMethod = row.requestMethod
  form.status = row.status
  form.operTime = row.operTime || ''
  form.errorMsg = row.errorMsg || ''
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSysOperLog(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createSysOperLog({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSysOperLog(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
.error-text {
  color: var(--el-color-danger);
}

/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
