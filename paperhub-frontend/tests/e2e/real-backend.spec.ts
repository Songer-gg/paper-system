import { test, expect, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
const userA = process.env.PAPERHUB_E2E_USER_A!
const userB = process.env.PAPERHUB_E2E_USER_B!
const errors = new Map<Page, string[]>()

test('ordinary registration returns to login and the new account has no admin access', async ({
  page,
}) => {
  const username = 'browser_reg_' + randomUUID().replaceAll('-', '').slice(0, 16)
  const password = '注册测试Pass123'
  await page.goto('/login')
  await page.getByRole('link', { name: '立即注册', exact: true }).click()
  await expect(page).toHaveURL(/\/register$/)
  await page.getByPlaceholder('以字母开头，3–50位字母、数字或下划线').fill(username)
  await page.getByPlaceholder('请输入昵称', { exact: true }).fill('新注册研究员')
  await page.getByPlaceholder('请输入邮箱', { exact: true }).fill(username + '@example.com')
  await page.getByPlaceholder('8–72 UTF-8字节', { exact: true }).fill(password)
  await page.getByPlaceholder('请再次输入密码', { exact: true }).fill('different')
  await page.getByRole('button', { name: '注册账号', exact: true }).click()
  await expect(page.getByText('两次密码输入不一致', { exact: true })).toBeVisible()
  await page.getByPlaceholder('请再次输入密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '注册账号', exact: true }).click()
  await page.waitForURL(/\/login\?username=/)
  await expect(page.getByPlaceholder('用户名或邮箱')).toHaveValue(username)
  expect(
    await page.evaluate(
      () => localStorage.getItem('paperhub_auth') ?? sessionStorage.getItem('paperhub_auth'),
    ),
  ).toBeNull()
  await login(page, username, password)
  await page.goto('/user/library/mine')
  await expect(page.locator('.paper-row')).toHaveCount(0)
  const forbidden = await api(page, 'GET', '/admin/users')
  expect(forbidden.code).toBe(1002)
  await page.goto('/admin/dashboard')
  await page.waitForURL('**/user/dashboard')
})
test.beforeEach(async ({ page }) => {
  const list: string[] = []
  errors.set(page, list)
  page.on('pageerror', (e) => list.push(e.message))
})
test.afterEach(async ({ page }) => {
  expect(errors.get(page) ?? []).toEqual([])
})
async function login(
  page: Page,
  username: string,
  password = '123456',
  role = 'user',
  remember = true,
) {
  await page.goto('/login')
  if (role === 'admin') await page.getByRole('button', { name: '管理员登录', exact: true }).click()
  await page.getByPlaceholder(role === 'admin' ? '管理员账号' : '用户名或邮箱').fill(username)
  await page.getByPlaceholder('密码', { exact: true }).fill(password)
  if (!remember && role === 'user') await page.getByText('记住登录状态', { exact: true }).click()
  await page.getByRole('button', { name: '登 录', exact: true }).click()
  await page.waitForURL('**/' + role + '/dashboard')
  await page.waitForLoadState('networkidle')
}
async function api(page: Page, method: string, path: string, body?: unknown) {
  return page.evaluate(
    async ({ method, path, body }) => {
      const raw = localStorage.getItem('paperhub_auth') ?? sessionStorage.getItem('paperhub_auth')
      const token = raw ? JSON.parse(raw).token : ''
      const response = await fetch('/api/v1' + path, {
        method,
        headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      return { status: response.status, ...(await response.json()) }
    },
    { method, path, body },
  )
}
async function upload(page: Page, shared = false) {
  const title = (shared ? 'Shared ' : 'Private ') + randomUUID()
  const bytes = Buffer.concat([
    await readFile(process.env.PAPERHUB_E2E_PDF!),
    Buffer.from('\n% browser ' + randomUUID() + '\n'),
  ])
  await page.goto('/user/papers/upload')
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'browser.pdf', mimeType: 'application/pdf', buffer: bytes })
  await expect(page.getByText('文件已就绪', { exact: true })).toBeVisible()
  await page.getByPlaceholder('请输入论文标题', { exact: true }).fill(title)
  await page.getByPlaceholder('多个作者用逗号分隔，如：张三，李四').fill('Browser Author')
  await page
    .getByPlaceholder('请输入论文摘要', { exact: true })
    .fill('Browser integration verification')
  await page.getByText('选择年份', { exact: true }).click()
  await page.getByRole('option', { name: '2026 年', exact: true }).click()
  await page.getByText('选择分类（支持二级分类）', { exact: true }).click()
  await page.getByText('人工智能', { exact: true }).last().click()
  if (shared) {
    await page.getByText('共享论文', { exact: true }).click()
    await page.locator('label.copyright-check').click()
  }
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/papers' &&
      response.request().method() === 'POST',
  )
  await page
    .getByRole('button', { name: shared ? '提交审核' : '上传到私有库', exact: true })
    .click()
  const response = await pending
  expect(response.status()).toBe(200)
  const result = await response.json()
  expect(result.code).toBe(0)
  await page.waitForURL('**/user/library/mine?tab=*')
  await expect(page.getByText(title, { exact: true }).first()).toBeVisible()
  return { id: result.data.id as number, title, bytes }
}
test('real session upload preview download metadata edit and per-user isolation', async ({
  page,
  browser,
}) => {
  await login(page, userA, '123456', 'user', false)
  expect(
    await page.evaluate(
      () =>
        localStorage.getItem('paperhub_auth') === null && !!sessionStorage.getItem('paperhub_auth'),
    ),
  ).toBe(true)
  await page.reload()
  await expect(page.getByText(userA, { exact: true }).first()).toBeVisible()
  const paper = await upload(page)
  await page.goto('/user/papers/' + paper.id)
  await expect(page.getByRole('heading', { name: paper.title })).toBeVisible()
  await page.getByRole('button', { name: '在线预览', exact: true }).first().click()
  const frame = page.locator('iframe.pdf-frame')
  await expect(frame).toHaveAttribute('src', /^blob:/)
  expect(
    await frame.evaluate(async (element) =>
      (await (await fetch((element as HTMLIFrameElement).src)).text()).startsWith('%PDF-'),
    ),
  ).toBe(true)
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 PDF', exact: true }).click()
  const saved = await download
  expect(saved.suggestedFilename().endsWith('.pdf')).toBe(true)
  expect((await readFile((await saved.path())!)).equals(paper.bytes)).toBe(true)
  const other = await browser.newContext()
  const otherPage = await other.newPage()
  try {
    await login(otherPage, userB)
    await otherPage.goto('/user/papers/' + paper.id)
    await expect(otherPage.getByText('论文不存在、已删除或尚未通过审核')).toBeVisible()
  } finally {
    await other.close()
  }
  await page.goto('/user/library/mine?tab=private')
  const row = page.locator('.paper-row').filter({ hasText: paper.title })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await page
    .getByRole('dialog')
    .getByPlaceholder('请输入论文标题', { exact: true })
    .fill(paper.title + ' edited')
  await page.getByRole('dialog').getByRole('button', { name: /保存/ }).click()
  await expect(page.getByText(paper.title + ' edited', { exact: true }).first()).toBeVisible()
})
test('shared review favorite reading retained safe notes and withdrawal protection', async ({
  page,
  browser,
}) => {
  await login(page, userA)
  const paper = await upload(page, true)
  const manager = await browser.newContext()
  const managerPage = await manager.newPage()
  const reader = await browser.newContext()
  const readerPage = await reader.newPage()
  try {
    await login(managerPage, 'admin', 'admin123', 'admin')
    await managerPage.goto('/admin/review')
    const row = managerPage.locator('.el-table__row').filter({ hasText: paper.title })
    await row.getByRole('button').click()
    await managerPage.locator('.copyright-check').click()
    await managerPage.getByRole('button', { name: '审核通过', exact: true }).click()
    await expect(managerPage.getByText('审核通过，论文已进入共享论文库')).toBeVisible()
    await login(readerPage, userB)
    await readerPage.goto('/user/papers/' + paper.id)
    await readerPage.getByRole('button', { name: '收藏', exact: true }).click()
    await expect(readerPage.getByRole('button', { name: '已收藏', exact: true })).toBeVisible()
    await readerPage.locator('.reading-row .el-select').click()
    await readerPage.getByRole('option', { name: '已阅读', exact: true }).click()
    await readerPage.getByRole('button', { name: '添加阅读笔记', exact: true }).click()
    const title = 'Retained ' + randomUUID()
    const content =
      '  # Browser note\n\n<img src=x onerror="window.__paperhubXss=true">\n    preserve indentation'
    await readerPage.getByPlaceholder('请输入笔记标题', { exact: true }).fill(title)
    await readerPage.getByPlaceholder('记录论文的核心观点、实验方法或你的思考…').fill(content)
    await readerPage.getByRole('button', { name: '保存笔记', exact: true }).click()
    await readerPage.goto('/user/notes')
    await readerPage.getByText(title, { exact: true }).click()
    expect(await readerPage.evaluate(() => '__paperhubXss' in window)).toBe(false)
    expect(
      (await api(readerPage, 'GET', '/me/notes')).data.find(
        (note: { title: string }) => note.title === title,
      ).content === content,
    ).toBe(true)
    await page.goto('/user/library/mine?tab=shared')
    await page
      .locator('.paper-row')
      .filter({ hasText: paper.title })
      .getByRole('button', { name: '取消共享', exact: true })
      .click()
    await page.getByRole('dialog').getByRole('button', { name: '取消共享', exact: true }).click()
    await expect(page.getByText('已取消共享，论文转为已下架状态')).toBeVisible()
    await readerPage.reload()
    await readerPage.getByText(title, { exact: true }).click()
    await expect(readerPage.getByText(/已删除或不可访问/).first()).toBeVisible()
    await managerPage.goto('/admin/papers/offline')
    await expect(
      managerPage
        .locator('.el-table__row')
        .filter({ hasText: paper.title })
        .getByRole('button', { name: '恢复上架', exact: true }),
    ).toBeDisabled()
    expect(
      (await api(readerPage, 'GET', '/me/favorites')).data.some(
        (row: { paperId: number }) => row.paperId === paper.id,
      ),
    ).toBe(false)
    await page.locator('.user-chip').click()
    await page.getByRole('menuitem', { name: '退出登录' }).click()
    await page.getByRole('dialog').getByRole('button', { name: /退出/ }).click()
    await page.waitForURL(/\/login(?:\?.*)?$/)
    await login(page, userB)
    expect(
      (await api(page, 'GET', '/me/favorites')).data.some(
        (row: { paperId: number }) => row.paperId === paper.id,
      ),
    ).toBe(false)
  } finally {
    await manager.close()
    await reader.close()
  }
})
test('network errors are visible without Mock fallback and expired sessions clear identity', async ({
  page,
}) => {
  await login(page, userA)
  await page.route('**/api/v1/me/dashboard', (route) => route.abort())
  await page.goto('/user/dashboard')
  await expect(
    page.getByText('无法连接服务器或请求超时，请检查网络与后端服务').first(),
  ).toBeVisible()
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await page.unroute('**/api/v1/me/dashboard')
  await page.route('**/api/v1/me/statistics', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 1001, message: '未登录或会话已失效', data: null }),
    }),
  )
  await page.goto('/user/statistics')
  await page.waitForURL(/\/login(?:\?.*)?$/)
  expect(
    await page.evaluate(
      () =>
        localStorage.getItem('paperhub_auth') === null &&
        sessionStorage.getItem('paperhub_auth') === null,
    ),
  ).toBe(true)
})
test('admin disable restore reset and user password change revoke old sessions', async ({
  page,
  browser,
}) => {
  await login(page, 'admin', 'admin123', 'admin')
  await page.goto('/admin/users')
  await page.getByPlaceholder('搜索姓名 / 用户名').fill(userB)
  await page.getByPlaceholder('搜索姓名 / 用户名').press('Tab')
  const row = page.locator('.el-table__row').filter({ hasText: userB })
  await expect(row).toHaveCount(1)
  const context = await browser.newContext()
  const user = await context.newPage()
  try {
    await login(user, userB)
    await row.getByRole('button', { name: '禁用', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '禁用', exact: true }).click()
    await expect(row.getByText('已禁用', { exact: true })).toBeVisible()
    await user.goto('/user/profile')
    await user.waitForURL(/\/login(?:\?.*)?$/)
    await row.getByRole('button', { name: '恢复', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '恢复', exact: true }).click()
    await expect(row.getByText('正常', { exact: true })).toBeVisible()
    await row.getByRole('button', { name: '重置密码', exact: true }).click()
    const reset = page.waitForResponse(
      (response) =>
        response.url().endsWith('/password-reset') && response.request().method() === 'POST',
    )
    await page.getByRole('dialog').getByRole('button', { name: '重置', exact: true }).click()
    const newPassword = (await (await reset).json()).data as string
    expect(newPassword.length === 24).toBe(true)
    await page.getByRole('dialog').getByRole('button', { name: '知道了', exact: true }).click()
    await login(user, userB, newPassword)
    await user.goto('/user/profile')
    await user.getByRole('button', { name: '修改密码', exact: true }).click()
    const dialog = user.getByRole('dialog')
    await dialog.getByPlaceholder('请输入原密码').fill(newPassword)
    await dialog.getByPlaceholder('8–72 UTF-8字节').fill('新密码abc123')
    await dialog.getByPlaceholder('请再次输入新密码').fill('新密码abc123')
    await dialog.getByRole('button', { name: '确认修改', exact: true }).click()
    await user.waitForURL(/\/login(?:\?.*)?$/)
    expect(await user.evaluate(() => localStorage.getItem('paperhub_auth') === null)).toBe(true)
    await login(user, userB, '新密码abc123')
  } finally {
    await context.close()
  }
})
