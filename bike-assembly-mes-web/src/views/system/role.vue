<template>
  <PageContainer title="角色管理" description="定义角色权限范围与菜单授权。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.roleName" placeholder="角色名称（本地筛选）" clearable />
      <el-input v-model="searchForm.roleKey" placeholder="角色编码（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd" v-permission="'admin:role:manage'">
          <el-icon><Plus /></el-icon> 新增角色
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="roleId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="roleKey" label="角色编码" width="160" show-overflow-tooltip />
      <el-table-column prop="dataScope" label="数据范围" width="100">
        <template #default="{ row }">
          <el-tag :type="row.dataScope === 'ALL' ? 'warning' : 'info'" size="small">
            {{ row.dataScope === 'ALL' ? '全部数据' : '仅本人' }}
          </el-tag>
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
            <el-button type="primary" link size="small" title="编辑角色" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该角色吗？" confirm-button-type="danger" @confirm="handleDelete(row.roleId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除角色">删除</el-button>
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
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="640px"
      :close-on-click-modal="false"
      @open="onDialogOpen"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="请输入角色编码，如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-radio-group v-model="form.dataScope">
            <el-radio value="SELF">仅本人数据</el-radio>
            <el-radio value="ALL">全部数据</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限分配">
          <div v-if="permTreeLoading" class="perm-tree-loading">加载权限中...</div>
          <el-tree
            v-else
            ref="permTreeRef"
            :data="permTreeData"
            show-checkbox
            node-key="menuId"
            :props="{ label: 'menuName', children: 'children' }"
            :default-checked-keys="checkedMenuIds"
            class="perm-tree"
          />
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
import { reactive, ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getSysRoleList, createSysRole, updateSysRole, deleteSysRole, getRoleMenuIds } from '@/api/sysRole'
import { getSysMenuTree } from '@/api/sysMenu'
import type { SysRole, SysMenuVO } from '@/types/api'

const tableData = ref<SysRole[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const permTreeRef = ref<InstanceType<typeof ElTree>>()
const editId = ref<number>()

const permTreeData = ref<SysMenuVO[]>([])
const checkedMenuIds = ref<number[]>([])
const permTreeLoading = ref(false)

const searchForm = reactive({
  roleName: '',
  roleKey: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SysRole>({
  roleName: '',
  roleKey: '',
  dataScope: 'SELF',
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
    roleKey: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
    dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
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
    const res = await getSysRoleList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: SysRole[] = Array.isArray(res) ? res : (res?.records || [])
    const name = searchForm.roleName.trim().toLowerCase()
    const key = searchForm.roleKey.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchName = !name || (item.roleName || '').toLowerCase().includes(name)
      const matchKey = !key || (item.roleKey || '').toLowerCase().includes(key)
      return matchName && matchKey
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
  searchForm.roleName = ''
  searchForm.roleKey = ''
  fetchList()
}

function resetForm() {
  form.roleName = ''
  form.roleKey = ''
  form.dataScope = 'SELF'
  form.status = '0'
  checkedMenuIds.value = []
  nextTick(() => {
    permTreeRef.value?.setCheckedKeys([])
  })
}

/** 对话框打开时加载权限树，编辑时加载已分配菜单 */
async function onDialogOpen() {
  permTreeLoading.value = true
  try {
    const tree = await getSysMenuTree()
    permTreeData.value = tree || []

    if (isEdit.value && editId.value) {
      try {
        const menuIds = await getRoleMenuIds(editId.value)
        checkedMenuIds.value = Array.isArray(menuIds) ? menuIds : []
      } catch {
        checkedMenuIds.value = []
      }
    } else {
      checkedMenuIds.value = []
    }

    await nextTick()
    if (checkedMenuIds.value.length > 0) {
      permTreeRef.value?.setCheckedKeys(checkedMenuIds.value)
    }
  } catch {
    permTreeData.value = []
    checkedMenuIds.value = []
  } finally {
    permTreeLoading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: SysRole) {
  isEdit.value = true
  editId.value = row.roleId
  form.roleName = row.roleName
  form.roleKey = row.roleKey
  form.dataScope = row.dataScope || 'SELF'
  form.status = row.status
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 获取当前树中半选和全选的节点 key
    // 过滤掉虚拟分组节点（menuId 为负数的 "Web 管理端"/"小程序端"），避免写入 sys_role_menu
    const halfCheckedKeys = (permTreeRef.value?.getHalfCheckedKeys() as number[] || []).filter((id) => id > 0)
    const checkedKeys = (permTreeRef.value?.getCheckedKeys() as number[] || []).filter((id) => id > 0)
    const allMenuIds = [...checkedKeys, ...halfCheckedKeys]

    const payload = {
      ...form,
      menuIds: allMenuIds
    }

    if (isEdit.value && editId.value) {
      await updateSysRole(editId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createSysRole(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSysRole(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
.perm-tree-loading {
  padding: 8px 0;
  color: var(--mes-text-muted);
}

.perm-tree {
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--mes-border);
  border-radius: var(--mes-radius-sm);
}

/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
