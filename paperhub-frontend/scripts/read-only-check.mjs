import { chromium } from 'playwright'
import { writeFile } from 'node:fs/promises'
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const errors = []
const checked = []
try {
  for (const role of ['user', 'admin']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    page.on('pageerror', (e) => errors.push(e.message))
    page.on('response', (r) => {
      if (r.status() >= 500 && r.url().includes('/api/'))
        errors.push('HTTP ' + r.status() + ' ' + new URL(r.url()).pathname)
    })
    await page.goto('http://127.0.0.1:5173/login')
    if (role === 'admin')
      await page.getByRole('button', { name: '管理员登录', exact: true }).click()
    await page
      .getByPlaceholder(role === 'admin' ? '管理员账号' : '用户名或邮箱')
      .fill(role === 'admin' ? 'admin' : 'researcher')
    await page
      .getByPlaceholder('密码', { exact: true })
      .fill(role === 'admin' ? 'admin123' : '123456')
    await page.getByRole('button', { name: '登 录', exact: true }).click()
    await page.waitForURL('**/' + role + '/dashboard')
    const paths =
      role === 'user'
        ? [
            'dashboard',
            'library/shared',
            'library/mine',
            'papers/upload',
            'papers/2001',
            'categories',
            'tags',
            'reading',
            'notes',
            'statistics',
            'profile',
            'papers/2001/reader',
          ]
        : [
            'dashboard',
            'users',
            'users/1',
            'papers',
            'papers/offline',
            'review',
            'review/records',
            'duplicates',
            'duplicates/records',
            'categories',
            'tags',
            'reports',
            'reports/records',
            'logs',
            'settings',
          ]
    for (const path of paths) {
      await page.goto('http://127.0.0.1:5173/' + role + '/' + path)
      await page.waitForLoadState('networkidle')
      const busy = await page.locator('.el-loading-mask:visible').count()
      if (busy) errors.push('Loading remains ' + role + '/' + path)
      checked.push({ path: '/' + role + '/' + path, title: await page.title() })
    }
    await context.close()
  }
} finally {
  await browser.close()
}
await writeFile(
  '../paperhub-back/target/batch9-local-browser-check.json',
  JSON.stringify({ readOnly: true, checked, errors }, null, 2),
)
console.log(JSON.stringify({ checked: checked.length, errors }))
if (errors.length) process.exitCode = 1
