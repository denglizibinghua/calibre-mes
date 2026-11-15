<template>
  <PageContainer title="BOM 管理" description="维护产品物料清单与版本。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.bomCode" placeholder="BOM 编码（本地筛选）" clearable />
      <el-input v-model="searchForm.version" placeholder="版本（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增 BOM
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="bomId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="itemId" label="物料 ID" width="100" class-name="mono-num" />
      <el-table-column prop="bomCode" label="BOM 编码" width="140" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="detailsJson" label="子件明细 JSON" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.detailsJson" placement="top" :show-after="500">
            <span class="text-ellipsis">{{ row.detailsJson }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑 BOM" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该 BOM 吗？" confirm-button-type="danger" @confirm="handleDelete(row.bomId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除 BOM">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- V2.3 分页 -->
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
      :title="isEdit ? '编辑 BOM' : '新增 BOM'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="物料 ID" prop="itemId">
          <el-input-number
            v-model="form.itemId"
            :min="1"
            placeholder="请输入成品物料 ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="BOM 编码" prop="bomCode">
          <el-input v-model="form.bomCode" placeholder="请输入 BOM 编码" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本，如 V1.0" />
        </el-form-item>
        <el-form-item label="子件明细 JSON" prop="detailsJson">
          <el-input
            v-model="form.detailsJson"
            type="textarea"
            :rows="5"
            placeholder='请输入子件明细 JSON，如 [{"itemId":1,"qty":2}]'
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
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
import { getBaseBomList, createBaseBom, updateBaseBom, deleteBaseBom } from '@/api/baseBom'
import type { BaseBom } from '@/types/api'

const tableData = ref<BaseBom[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const query = reactive({
  page: 1,
  size: 15
})

const searchForm = reactive({
  bomCode: '',
  version: ''
})

const form = reactive<BaseBom>({
  itemId: undefined,
  bomCode: '',
  version: 'V1.0',
  detailsJson: '',
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    itemId: [{ required: true, message: '请输入物料 ID', trigger: 'change' }],
    bomCode: [{ required: true, message: '请输入 BOM 编码', trigger: 'blur' }],
    version: [{ required: true, message: '请输入版本', trigger: 'blur' }],
    detailsJson: [{ required: true, message: '请输入子件明细 JSON', trigger: 'blur' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getBaseBomList({ page: query.page, size: query.size })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    const allData: BaseBom[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    // 前端本地筛选（仅作用于当前页；服务端筛选后续版本再做）
    const code = searchForm.bomCode.trim().toLowerCase()
    const ver = searchForm.version.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchCode = !code || (item.bomCode || '').toLowerCase().includes(code)
      const matchVer = !ver || (item.version || '').toLowerCase().includes(ver)
      return matchCode && matchVer
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.bomCode = ''
  searchForm.version = ''
  handleSearch()
}

function resetForm() {
  form.itemId = undefined
  form.bomCode = ''
  form.version = 'V1.0'
  form.detailsJson = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: BaseBom) {
  isEdit.value = true
  editId.value = row.bomId
  form.itemId = row.itemId
  form.bomCode = row.bomCode
  form.version = row.version
  form.detailsJson = row.detailsJson
  form.status = row.status
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBaseBom(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBaseBom({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteBaseBom(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅承载主操作：右对齐；窄屏由全局工具条规则自动换行 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
