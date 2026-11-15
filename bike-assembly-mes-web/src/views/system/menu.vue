<template>
  <PageContainer title="菜单管理" description="配置后台菜单、按钮权限与路由组件。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.menuName" placeholder="菜单名称（本地筛选）" clearable />
      <el-input v-model="searchForm.menuType" placeholder="M/C/F（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增菜单
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="menuId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="parentId" label="父菜单 ID" width="110" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.parentId }">
            {{ row.parentId ?? '顶级' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="menuName" label="菜单名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="path" label="路由路径" width="180" show-overflow-tooltip />
      <el-table-column prop="perms" label="权限标识" width="160" show-overflow-tooltip />
      <el-table-column prop="menuType" label="菜单类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.menuType === 'M' ? 'primary' : row.menuType === 'C' ? 'success' : 'warning'"
            size="small"
          >
            {{ row.menuType === 'M' ? '目录' : row.menuType === 'C' ? '菜单' : row.menuType === 'F' ? '按钮' : (row.menuType || '-') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderNum" label="排序" width="70" class-name="mono-num" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑菜单" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该菜单吗？" confirm-button-type="danger" @confirm="handleDelete(row.menuId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除菜单">删除</el-button>
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
      :title="isEdit ? '编辑菜单' : '新增菜单'"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父菜单 ID" prop="parentId">
          <el-input-number
            v-model="form.parentId"
            :min="0"
            placeholder="0 表示顶级菜单（选填）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="请输入路由路径，如 /system/role" />
        </el-form-item>
        <el-form-item label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="请输入权限标识，如 system:role:list" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-select v-model="form.menuType" placeholder="请选择菜单类型" style="width: 100%">
            <el-option label="目录 (M)" value="M" />
            <el-option label="菜单 (C)" value="C" />
            <el-option label="按钮 (F)" value="F" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number
            v-model="form.orderNum"
            :min="0"
            placeholder="请输入排序号"
            style="width: 100%"
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
import { getSysMenuList, createSysMenu, updateSysMenu, deleteSysMenu } from '@/api/sysMenu'
import type { SysMenu } from '@/types/api'

const tableData = ref<SysMenu[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  menuName: '',
  menuType: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SysMenu>({
  parentId: undefined,
  menuName: '',
  path: '',
  perms: '',
  menuType: '',
  orderNum: 0,
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    // parentId: 新增和编辑都选填（DB BIGINT NULL，0 或空表示顶级菜单）
    menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
    path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }],
    perms: [{ required: true, message: '请输入权限标识', trigger: 'blur' }],
    menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
    orderNum: [{ required: true, message: '请输入排序号', trigger: 'change' }],
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
    const res = await getSysMenuList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: SysMenu[] = Array.isArray(res) ? res : (res?.records || [])
    const name = searchForm.menuName.trim().toLowerCase()
    const type = searchForm.menuType.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchName = !name || (item.menuName || '').toLowerCase().includes(name)
      const matchType = !type || (item.menuType || '').toLowerCase().includes(type)
      return matchName && matchType
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}


function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
    query.page = 1
  searchForm.menuName = ''
  searchForm.menuType = ''
  fetchList()
}

function resetForm() {
  form.parentId = undefined
  form.menuName = ''
  form.path = ''
  form.perms = ''
  form.menuType = ''
  form.orderNum = 0
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: SysMenu) {
  isEdit.value = true
  editId.value = row.menuId
  form.parentId = row.parentId
  form.menuName = row.menuName
  form.path = row.path
  form.perms = row.perms
  form.menuType = row.menuType
  form.orderNum = row.orderNum
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
      await updateSysMenu(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createSysMenu({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSysMenu(id)
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
