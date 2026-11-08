<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { registerUser } from '@/services/authService'

/** Ordinary account registration. No role selector or automatic login; no props or emits. */
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', name: '', email: '', password: '', confirmPassword: '' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, done: (error?: Error) => void) => {
        done(
          /^[A-Za-z][A-Za-z0-9_]{2,49}$/.test(value.trim())
            ? undefined
            : new Error('以字母开头，3–50位字母、数字或下划线'),
        )
      },
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, done: (error?: Error) => void) => {
        const size = Array.from(value.trim()).length
        done(size > 0 && size <= 50 ? undefined : new Error('昵称须为1–50个字符'))
      },
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' },
    { max: 120, message: '邮箱不能超过120个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, done: (error?: Error) => void) => {
        const bytes = new TextEncoder().encode(value).length
        done(
          bytes >= 8 && bytes <= 72 && value.trim() && !value.includes('\0')
            ? undefined
            : new Error('密码须为8–72 UTF-8字节，不能全为空白'),
        )
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, done: (error?: Error) => void) => {
        done(value === form.password ? undefined : new Error('两次密码输入不一致'))
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  if (loading.value) return
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    loading.value = true
    const user = await registerUser({ ...form })
    form.password = ''
    form.confirmPassword = ''
    ElMessage.success('注册成功，请使用新账号登录')
    await router.replace({ name: 'login', query: { username: user.username } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <section class="register-card">
      <RouterLink class="brand" :to="{ name: 'login' }">PaperHub</RouterLink>
      <h1>创建用户账号</h1>
      <p class="intro">管理个人论文，收藏与阅读共享文献</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        :disabled="loading"
        @submit.prevent="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="以字母开头，3–50位字母、数字或下划线"
            autocomplete="username"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="name">
          <el-input v-model="form.name" placeholder="请输入昵称" autocomplete="nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model.trim="form.email"
            placeholder="请输入邮箱"
            autocomplete="email"
            maxlength="120"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="8–72 UTF-8字节"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            placeholder="请再次输入密码"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button class="register-button" type="primary" native-type="submit" :loading="loading">{{
          loading ? '正在注册…' : '注册账号'
        }}</el-button>
      </el-form>
      <p class="login-entry">已有账号？<RouterLink :to="{ name: 'login' }">返回登录</RouterLink></p>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  box-sizing: border-box;
  background: #f1f5f9;
}
.register-card {
  width: 100%;
  max-width: 460px;
  padding: 32px;
  box-sizing: border-box;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: white;
  box-shadow: 0 12px 40px rgb(15 23 42 / 6%);
}
.brand {
  color: #2563eb;
  font-weight: 700;
  font-size: 22px;
  text-decoration: none;
}
h1 {
  margin: 20px 0 8px;
  font-size: 24px;
  color: #0f172a;
}
.intro {
  color: #64748b;
  margin: 0 0 24px;
  font-size: 14px;
}
.register-button {
  width: 100%;
  margin-top: 6px;
}
.login-entry {
  text-align: center;
  color: #64748b;
  font-size: 14px;
  margin: 24px 0 0;
}
.login-entry a {
  margin-left: 6px;
  color: #2563eb;
  text-decoration: none;
}
</style>
