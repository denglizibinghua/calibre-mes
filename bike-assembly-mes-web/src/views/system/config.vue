<template>
  <PageContainer title="参数配置" description="维护系统运行参数与业务开关。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.configKey" placeholder="配置键" clearable />
      <el-input v-model="searchForm.configName" placeholder="配置名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增配置
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="configId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="configKey" label="配置键" width="160" show-overflow-tooltip />
      <el-table-column prop="configValue" label="配置值" width="160" show-overflow-tooltip />
      <el-table-column prop="configName" label="配置名称" width="130" show-overflow-tooltip />
      <el-table-column prop="configType" label="配置类型" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="150" class-name="mono-num" />
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑配置" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该配置吗？" confirm-button-type="danger" @confirm="handleDelete(row.configId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除配置">删除</el-button>
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
      :title="isEdit ? '编辑配置' : '新增配置'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="请输入配置键" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="配置类型" prop="configType">
          <el-input v-model="form.configType" placeholder="请输入配置类型" />
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
import { getSysConfigList, createSysConfig, updateSysConfig, deleteSysConfig } from '@/api/sysConfig'
import type { SysConfig } from '@/types/api'

const tableData = ref<SysConfig[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  configKey: '',
  configName: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SysConfig>({
  configKey: '',
  configValue: '',
  configName: '',
  configType: '',
  status: '0'
})

const rules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configType: [{ required: true, message: '请输入配置类型', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getSysConfigList(searchForm)
    tableData.value = Array.isArray(res) ? res : (res?.records || [])
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}


function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
    query.page = 1
  searchForm.configKey = ''
  searchForm.configName = ''
  fetchList()
}

function resetForm() {
  form.configKey = ''
  form.configValue = ''
  form.configName = ''
  form.configType = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: SysConfig) {
  isEdit.value = true
  editId.value = row.configId
  form.configKey = row.configKey
  form.configValue = row.configValue
  form.configName = row.configName
  form.configType = row.configType
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSysConfig(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createSysConfig({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSysConfig(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
