<template>
  <PageContainer title="物料管理" description="维护原材料、半成品与成品物料主数据。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.itemCode" placeholder="物料编码" clearable />
      <el-input v-model="searchForm.itemName" placeholder="物料名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增物料
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="itemId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="itemCode" label="物料编码" width="140" show-overflow-tooltip />
      <el-table-column prop="itemName" label="物料名称" width="140" show-overflow-tooltip />
      <el-table-column prop="specModel" label="规格型号" width="120" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="itemType" label="物料类型" width="120">
        <template #default="{ row }">
          {{ itemTypeLabel(row.itemType) }}
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
            <el-button type="primary" link size="small" title="编辑物料" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该物料吗？" confirm-button-type="danger" @confirm="handleDelete(row.itemId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除物料">删除</el-button>
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
      :title="isEdit ? '编辑物料' : '新增物料'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="物料编码" prop="itemCode">
          <el-input v-model="form.itemCode" placeholder="请输入物料编码" />
        </el-form-item>
        <el-form-item label="物料名称" prop="itemName">
          <el-input v-model="form.itemName" placeholder="请输入物料名称" />
        </el-form-item>
        <el-form-item label="规格型号" prop="specModel">
          <el-input v-model="form.specModel" placeholder="请输入规格型号" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="物料类型" prop="itemType">
          <el-select v-model="form.itemType" placeholder="请选择物料类型" style="width: 100%">
            <el-option label="成品" value="P" />
            <el-option label="半成品" value="S" />
            <el-option label="零部件" value="M" />
            <el-option label="原材料" value="R" />
          </el-select>
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
import { getBaseItemList, createBaseItem, updateBaseItem, deleteBaseItem } from '@/api/baseItem'
import type { BaseItem } from '@/types/api'

/** 物料类型 CHAR(1) → 中文显示映射 */
const ITEM_TYPE_MAP: Record<string, string> = {
  P: '成品',
  S: '半成品',
  M: '零部件',
  R: '原材料'
}

function itemTypeLabel(code: string): string {
  return ITEM_TYPE_MAP[code] ?? code
}

const tableData = ref<BaseItem[]>([])
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
  itemCode: '',
  itemName: ''
})

const form = reactive<BaseItem>({
  itemCode: '',
  itemName: '',
  specModel: '',
  unit: '',
  itemType: '',
  status: '0'
})

const rules: FormRules = {
  itemCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getBaseItemList({ page: query.page, size: query.size, ...searchForm })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function resetSearch() {
  searchForm.itemCode = ''
  searchForm.itemName = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetForm() {
  form.itemCode = ''
  form.itemName = ''
  form.specModel = ''
  form.unit = ''
  form.itemType = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: BaseItem) {
  isEdit.value = true
  editId.value = row.itemId
  form.itemCode = row.itemCode
  form.itemName = row.itemName
  form.specModel = row.specModel
  form.unit = row.unit
  form.itemType = row.itemType
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBaseItem(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBaseItem({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteBaseItem(id)
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
