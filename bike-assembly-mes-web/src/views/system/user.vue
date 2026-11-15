<template>
  <PageContainer title="用户管理" description="维护系统账号、角色归属与启用状态。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.username" placeholder="用户名" clearable />
      <el-input v-model="searchForm.nickname" placeholder="昵称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd" v-permission="'admin:user:manage'">
          <el-icon><Plus /></el-icon> 新增用户
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="userId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          {{ getRoleName(row.roleId) }}
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
      <el-table-column label="操作" min-width="220" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑用户" @click="handleEdit(row)" v-permission="'admin:user:manage'">编辑</el-button>
            <el-button
              v-if="row.loginFailCount >= 5"
              type="warning" link size="small"
              title="解锁账号"
              @click="handleUnlock(row)"
              v-permission="'admin:user:unlock'"
            >
              解锁
            </el-button>
            <el-button type="warning" link size="small" title="重置密码" @click="showResetPwdDialog(row)" v-permission="'admin:user:manage'">重置密码</el-button>
            <el-popconfirm title="确定删除该用户吗？" confirm-button-type="danger" @confirm="handleDelete(row.userId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除用户" v-permission="'admin:user:manage'">删除</el-button>
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
    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="resetPwdVisible"
      title="重置密码"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          确认重置用户 <strong>{{ resetTargetUser?.username }}</strong> 的密码？
        </template>
        <ul style="margin: 8px 0 0 16px; padding: 0">
          <li>重置后用户下次登录必须修改密码</li>
          <li>该用户当前所有登录会话将失效</li>
        </ul>
      </el-alert>
      <el-form :model="resetPwdForm" label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="resetPwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少8位）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" @click="handleResetPassword" :loading="resettingPwd">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width:100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.roleId"
              :label="r.roleName"
              :value="r.roleId"
            />
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getSysUserList, createSysUser, updateSysUser, deleteSysUser } from '@/api/sysUser'
import { getSysRoleList } from '@/api/sysRole'
import request from '@/api/request'
import type { SysUser, SysRole } from '@/types/api'

const tableData = ref<SysUser[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const roleOptions = ref<SysRole[]>([])

const searchForm = reactive({
  username: '',
  nickname: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SysUser>({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  roleId: 1,
  status: '0'
})

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: isEdit.value
    ? []
    : [{ required: true, message: '请输入密码', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}))

onMounted(() => {
  fetchList()
  fetchRoleOptions()
})

async function fetchRoleOptions() {
  try {
    const res = await getSysRoleList({ page: 1, size: 999 })
    roleOptions.value = Array.isArray(res?.records) ? res.records : (Array.isArray(res) ? res : [])
  } catch {
    roleOptions.value = []
  }
}

function getRoleName(roleId: number | undefined): string {
  if (roleId == null) return '-'
  const role = roleOptions.value.find(r => r.roleId === roleId)
  return role?.roleName || String(roleId)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getSysUserList({ page: query.page, size: query.size, ...searchForm })
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}


function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  query.page = 1
  searchForm.username = ''
  searchForm.nickname = ''
  fetchList()
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.phone = ''
  form.roleId = roleOptions.value.length > 0 ? (roleOptions.value[0].roleId ?? 1) : 1
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: SysUser) {
  isEdit.value = true
  editId.value = row.userId
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname
  form.phone = row.phone
  form.roleId = row.roleId
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      const submitData = { ...form }
      if (!submitData.password) delete submitData.password
      await updateSysUser(editId.value, submitData)
      ElMessage.success('编辑成功')
    } else {
      await createSysUser({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSysUser(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// ==================== 重置密码 ====================

const resetPwdVisible = ref(false)
const resettingPwd = ref(false)
const resetTargetUser = ref<SysUser | null>(null)
const resetPwdForm = reactive({ newPassword: '' })

function showResetPwdDialog(row: SysUser) {
  resetTargetUser.value = row
  resetPwdForm.newPassword = ''
  resetPwdVisible.value = true
}

async function handleResetPassword() {
  if (!resetPwdForm.newPassword || resetPwdForm.newPassword.length < 8) {
    ElMessage.warning('请输入至少8位的新密码')
    return
  }
  if (!resetTargetUser.value?.userId) return

  resettingPwd.value = true
  try {
    await request.put(`/admin/sys-user/${resetTargetUser.value.userId}/reset-password`, {
      newPassword: resetPwdForm.newPassword,
    })
    ElMessage.success('密码已重置，用户下次登录需修改密码')
    resetPwdVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { resettingPwd.value = false }
}

// ==================== 解锁账号 ====================

async function handleUnlock(row: SysUser) {
  if (!row.userId) return
  try {
    await ElMessageBox.confirm(
      `确认解锁用户 "${row.username}" 的账号吗？解锁后该用户可以重新登录。`,
      '解锁账号',
      { type: 'warning' }
    )
    await request.put(`/admin/sys-user/${row.userId}/unlock`)
    ElMessage.success('账号已解锁')
    fetchList()
  } catch (e: any) {
    if (e !== 'cancel') { /* 已在 request 拦截器中处理 */ }
  }
}
</script>

<style scoped>
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
