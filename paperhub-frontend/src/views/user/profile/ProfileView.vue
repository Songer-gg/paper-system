<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import type { MyProfile } from '@/services/profileService'
import * as profileService from '@/services/profileService'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const profile = ref<MyProfile | null>(null)

async function fetchProfile() {
  loading.value = true
  try {
    profile.value = await profileService.getMyProfile()
  } finally {
    loading.value = false
  }
}
onMounted(fetchProfile)

const initial = computed(() => profile.value?.name?.charAt(0) ?? '研')

function syncSessionName(name: string) {
  auth.updateUser({ name })
}

/* ---------------- 修改资料 ---------------- */
const profileDialogVisible = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({
  name: '',
  email: '',
})

const emailRule = {
  validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
    if (!value) callback()
    else if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value)) callback(new Error('邮箱格式不正确'))
    else callback()
  },
  trigger: 'blur',
}

const profileRules: FormRules = {
  name: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [emailRule],
}

function openProfileDialog() {
  profileForm.name = profile.value?.name ?? ''
  profileForm.email = profile.value?.email ?? ''
  profileDialogVisible.value = true
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profile.value = await profileService.updateProfile({
    name: profileForm.name.trim(),
    email: profileForm.email.trim(),
  })
  syncSessionName(profile.value.name)
  profileDialogVisible.value = false
  ElMessage.success('个人信息已更新')
}

/* ---------------- 修改头像 ---------------- */
async function handleAvatarChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.error('头像图片不能超过 2 MB')
    return
  }
  try {
    profile.value = await profileService.updateAvatar(raw)
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像更新失败')
  }
}

/* ---------------- 修改密码 ---------------- */
const pwdDialogVisible = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        const size = new TextEncoder().encode(value).length
        callback(size < 8 || size > 72 ? new Error('新密码须为8–72 UTF-8字节') : undefined)
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string, cb: (err?: Error) => void) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

function openPwdDialog() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdDialogVisible.value = true
}

async function savePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const res = await profileService.changePassword(
    pwdForm.oldPassword,
    pwdForm.newPassword,
    pwdForm.confirmPassword,
  )
  if (res.ok) {
    pwdDialogVisible.value = false
    ElMessage.success(res.message)
    auth.logout()
    await router.replace('/login')
  } else {
    ElMessage.error(res.message)
  }
}

/* ---------------- 退出登录 ---------------- */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    auth.logout()
    ElMessage.success('已安全退出')
    router.push('/login')
  } catch (error) {
    reportActionError(error)
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">个人中心</h2>
        <p class="ph-page-desc">维护个人信息、头像与账号安全</p>
      </div>
    </div>

    <div v-if="loading" v-loading="true" class="profile-loading" />

    <template v-else-if="profile">
      <div class="profile-layout">
        <!-- 左侧资料卡 -->
        <div class="ph-card profile-card">
          <div class="avatar-wrap">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              @change="handleAvatarChange"
            >
              <div class="avatar">
                <img v-if="profile.avatar" :src="profile.avatar" alt="头像" />
                <span v-else>{{ initial }}</span>
                <div class="avatar-mask">
                  <el-icon :size="18"><Camera /></el-icon>
                  <span>更换头像</span>
                </div>
              </div>
            </el-upload>
            <div class="profile-name">{{ profile.name }}</div>
            <div class="profile-email">{{ profile.email }}</div>
            <el-tag
              :type="profile.role === 'admin' ? 'danger' : 'primary'"
              effect="light"
              size="small"
              class="profile-role"
            >
              {{ profile.role === 'admin' ? '管理员' : '研究员' }}
            </el-tag>
          </div>

          <el-divider />

          <div class="meta-list">
            <div class="meta-row">
              <span class="meta-key">用户名</span>
              <span class="meta-val">{{ profile.username }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-key">注册时间</span>
              <span class="meta-val">{{ profile.registeredAt.slice(0, 10) }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-key">账号状态</span>
              <span class="meta-val">
                <span class="ph-status ph-status--success">
                  {{ profile.status === 'active' ? '正常' : '已禁用' }}
                </span>
              </span>
            </div>
            <div class="meta-row">
              <span class="meta-key">已上传论文</span>
              <span class="meta-val">{{ profile.uploadedCount }} 篇</span>
            </div>
          </div>
        </div>

        <!-- 右侧操作卡 -->
        <div class="actions-col">
          <div class="ph-card action-card">
            <div class="action-head">
              <el-icon :size="20" color="#2563eb"><UserFilled /></el-icon>
              <div>
                <div class="action-title">基本信息</div>
                <div class="action-desc">昵称、邮箱用于页面展示与找回账号</div>
              </div>
            </div>
            <el-button type="primary" plain :icon="'EditPen'" @click="openProfileDialog">
              修改资料
            </el-button>
          </div>

          <div class="ph-card action-card">
            <div class="action-head">
              <el-icon :size="20" color="#d97706"><Lock /></el-icon>
              <div>
                <div class="action-title">账号安全</div>
                <div class="action-desc">定期修改密码可以保护账号安全</div>
              </div>
            </div>
            <el-button type="primary" plain :icon="'Key'" @click="openPwdDialog">
              修改密码
            </el-button>
          </div>

          <div class="ph-card action-card">
            <div class="action-head">
              <el-icon :size="20" color="#dc2626"><SwitchButton /></el-icon>
              <div>
                <div class="action-title">退出登录</div>
                <div class="action-desc">结束当前会话，返回登录页</div>
              </div>
            </div>
            <el-button type="danger" plain :icon="'SwitchButton'" @click="handleLogout">
              退出账号
            </el-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 修改资料弹窗 -->
    <el-dialog v-model="profileDialogVisible" title="修改资料" width="440px" destroy-on-close>
      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-width="70px"
        label-position="left"
      >
        <el-form-item label="昵称" prop="name">
          <el-input v-model="profileForm.name" placeholder="请输入昵称" maxlength="16" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="440px" destroy-on-close>
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="90px"
        label-position="left"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="8–72 UTF-8字节"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-loading {
  height: 400px;
}

.profile-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.profile-card {
  width: 300px;
  flex-shrink: 0;
  padding: 28px 24px;
}
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.avatar {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 999px;
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-size: 32px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-mask {
  position: absolute;
  inset: 0;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 11px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar:hover .avatar-mask {
  opacity: 1;
}
.profile-name {
  margin-top: 14px;
  font-size: 17px;
  font-weight: 700;
  color: var(--ph-text);
}
.profile-email {
  font-size: 12.5px;
  color: var(--ph-text-muted);
  margin-top: 4px;
}
.profile-role {
  margin-top: 10px;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}
.meta-key {
  color: var(--ph-text-muted);
}
.meta-val {
  color: var(--ph-text);
  font-weight: 500;
}

.actions-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.action-card {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.action-head {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.action-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ph-text);
}
.action-desc {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 4px;
}

@media (max-width: 1080px) {
  .profile-layout {
    flex-direction: column;
  }
  .profile-card {
    width: 100%;
  }
}
</style>
