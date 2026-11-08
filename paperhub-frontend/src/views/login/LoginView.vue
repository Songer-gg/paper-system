<script setup lang="ts">
import { reactive, ref, watch, nextTick } from 'vue'
import { useMock } from '@/config/runtime'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

type Mode = Role
const mode = ref<Mode>('user')
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '',
  remember: true,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/* 切换身份时清空已输入的密码，避免误提交 */
watch(mode, () => {
  form.password = ''
  formRef.value?.clearValidate()
})

async function fillDemoAccount(m: Mode) {
  mode.value = m
  await nextTick()
  form.username = m === 'admin' ? 'admin' : 'researcher'
  form.password = m === 'admin' ? 'admin123' : '123456'
  formRef.value?.clearValidate()
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await auth.login(form.username, form.password, mode.value, form.remember)
    ElMessage.success(`登录成功，欢迎回来，${user.name}`)
    const redirect = (route.query.redirect as string) || auth.homePath
    await router.push(
      redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : auth.homePath,
    )
  } catch (err) {
    ElMessage.error((err as Error).message)
  } finally {
    loading.value = false
  }
}

function notifyNotReady() {
  ElMessage.info('该功能尚未开放，请联系平台管理员')
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <section class="brand-panel">
      <div class="brand-deco brand-deco--1" />
      <div class="brand-deco brand-deco--2" />
      <div class="brand-deco brand-deco--3" />

      <div class="brand-inner">
        <div class="brand-logo">
          <div class="logo-mark">
            <el-icon :size="24"><Reading /></el-icon>
          </div>
          <span class="logo-name">PaperHub</span>
        </div>

        <h1 class="brand-headline">让学术文献管理<br />更简单、更高效</h1>
        <p class="brand-intro">
          PaperHub 是面向科研人员的论文管理与共享平台。
          统一的共享资源库避免重复上传，个人知识体系助力高效科研。
        </p>

        <ul class="brand-features">
          <li>
            <div class="feature-icon">
              <el-icon :size="16"><Collection /></el-icon>
            </div>
            <div>
              <div class="feature-title">共享论文库</div>
              <div class="feature-desc">平台统一审核，避免重复上传相同论文</div>
            </div>
          </li>
          <li>
            <div class="feature-icon">
              <el-icon :size="16"><Notebook /></el-icon>
            </div>
            <div>
              <div class="feature-title">个人知识管理</div>
              <div class="feature-desc">分类、标签、阅读状态与阅读笔记</div>
            </div>
          </li>
          <li>
            <div class="feature-icon">
              <el-icon :size="16"><Lock /></el-icon>
            </div>
            <div>
              <div class="feature-title">私有空间</div>
              <div class="feature-desc">私有资料按权限保护，管理访问留痕</div>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- 右侧登录区 -->
    <section class="login-panel">
      <div class="login-card">
        <div class="login-card-head">
          <h2>欢迎使用 PaperHub</h2>
          <p>选择身份并登录，开始你的文献管理之旅</p>
        </div>

        <!-- 身份切换 -->
        <div class="role-switch">
          <button
            type="button"
            class="role-btn"
            :class="{ active: mode === 'user' }"
            @click="mode = 'user'"
          >
            <el-icon :size="15"><User /></el-icon>
            用户登录
          </button>
          <button
            type="button"
            class="role-btn"
            :class="{ active: mode === 'admin' }"
            @click="mode = 'admin'"
          >
            <el-icon :size="15"><Monitor /></el-icon>
            管理员登录
          </button>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="mode === 'admin' ? '管理员账号' : '用户名或邮箱'"
              :prefix-icon="mode === 'admin' ? 'Monitor' : 'User'"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="'Lock'"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div v-if="mode === 'user'" class="form-row-between">
            <el-checkbox v-model="form.remember">记住登录状态</el-checkbox>
            <el-link underline="never" class="link-text" @click="notifyNotReady">
              忘记密码？
            </el-link>
          </div>

          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            {{ loading ? '正在登录…' : '登 录' }}
          </el-button>
        </el-form>

        <div v-if="mode === 'user'" class="register-entry">
          还没有账号？
          <RouterLink :to="{ name: 'register' }">立即注册</RouterLink>
        </div>
        <div v-else class="admin-note">
          <el-icon :size="13"><InfoFilled /></el-icon>
          管理员账号由平台统一分配，不提供注册入口
        </div>

        <!-- 演示账号提示 -->
        <div v-if="useMock" class="demo-hint">
          <div class="demo-hint-title">
            <el-icon :size="13"><MagicStick /></el-icon>
            演示账号（点击自动填入）
          </div>
          <div class="demo-hint-row">
            <button type="button" @click="fillDemoAccount('user')">
              用户：<b>researcher</b> / 123456
            </button>
            <button type="button" @click="fillDemoAccount('admin')">
              管理员：<b>admin</b> / admin123
            </button>
          </div>
        </div>
      </div>

      <p class="login-footer">PaperHub 论文管理平台</p>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 左侧品牌区 ---------- */
.brand-panel {
  position: relative;
  flex: 1.15;
  background: linear-gradient(140deg, #2563eb 0%, #1e40af 55%, #172f7a 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.brand-deco {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.14);
}
.brand-deco--1 {
  width: 520px;
  height: 520px;
  top: -160px;
  left: -120px;
}
.brand-deco--2 {
  width: 340px;
  height: 340px;
  bottom: -100px;
  right: -60px;
  background: rgba(59, 130, 246, 0.12);
  border: none;
}
.brand-deco--3 {
  width: 700px;
  height: 700px;
  bottom: -420px;
  left: -260px;
}

.brand-inner {
  position: relative;
  max-width: 460px;
  padding: 0 32px;
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}
.logo-mark {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-name {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.brand-headline {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.4;
  margin-bottom: 18px;
  letter-spacing: 1px;
}
.brand-intro {
  font-size: 15px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.82);
  margin-bottom: 44px;
}
.brand-features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.brand-features li {
  display: flex;
  align-items: center;
  gap: 14px;
}
.feature-icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.feature-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}
.feature-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

/* ---------- 右侧登录区 ---------- */
.login-panel {
  flex: 1;
  background: var(--ph-bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
}
.login-card {
  width: 420px;
  max-width: 100%;
  background: #fff;
  border: 1px solid var(--ph-border);
  border-radius: 18px;
  padding: 36px 38px 28px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.06);
}
.login-card-head {
  margin-bottom: 24px;
}
.login-card-head h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--ph-text);
  margin-bottom: 8px;
}
.login-card-head p {
  font-size: 13.5px;
  color: var(--ph-text-secondary);
}

.role-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  background: #f3f4f6;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 22px;
}
.role-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 38px;
  border: none;
  border-radius: 9px;
  background: transparent;
  font-size: 14px;
  color: var(--ph-text-secondary);
  cursor: pointer;
  transition: all 0.18s ease;
  font-family: inherit;
}
.role-btn.active {
  background: #fff;
  color: var(--ph-primary);
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.form-row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.link-text {
  font-size: 13px;
  color: var(--ph-text-secondary);
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 6px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.register-entry {
  text-align: center;
  font-size: 13.5px;
  color: var(--ph-text-secondary);
  margin-bottom: 18px;
}
.admin-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--ph-text-muted);
  margin-bottom: 18px;
}

.demo-hint {
  border: 1px dashed #c7d2e8;
  background: var(--ph-primary-light);
  border-radius: 12px;
  padding: 12px 14px;
}
.demo-hint-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ph-primary);
  margin-bottom: 8px;
}
.demo-hint-row {
  display: flex;
  gap: 8px;
}
.demo-hint-row button {
  flex: 1;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  color: var(--ph-text-secondary);
  padding: 8px 6px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
  white-space: nowrap;
}
.demo-hint-row button:hover {
  color: var(--ph-primary);
  background: #fff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.12);
}
.demo-hint-row b {
  color: var(--ph-primary);
  font-weight: 600;
}

.login-footer {
  margin-top: 28px;
  font-size: 12px;
  color: var(--ph-text-muted);
}

/* ---------- 窄屏兼容：品牌区收缩为顶部条 ---------- */
@media (max-width: 960px) {
  .login-page {
    flex-direction: column;
    overflow-y: auto;
  }
  .brand-panel {
    flex: none;
    padding: 40px 28px 32px;
  }
  .brand-headline {
    font-size: 24px;
    margin-bottom: 12px;
  }
  .brand-intro,
  .brand-features {
    display: none;
  }
  .brand-logo {
    margin-bottom: 0;
  }
}
</style>
