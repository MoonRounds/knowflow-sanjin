import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright E2E 配置：验证两个 V1 核心闭环（对话沉淀、笔记/上传）。
 *
 * 前置：docker compose -f docker-compose.e2e.yml up -d --wait。
 * webServer 依次启动：本地模型 stub → 后端 → 前端 dev server。
 * 后端以 allow-local-base-url=true 连接本地 stub（model-stub.py 提供 chat/utility/embedding）。
 *
 * 推荐运行：sh scripts/verify-e2e.sh
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'list',
  timeout: 120_000,
  use: {
    baseURL: 'http://127.0.0.1:15173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'python3 ../scripts/model-stub.py 18082',
      url: 'http://127.0.0.1:18082/health',
      timeout: 30_000,
      reuseExistingServer: false,
    },
    {
      command:
        '../knowflow-app/mvnw -s ../knowflow-app/.mvn/settings.xml -f ../pom.xml -pl knowflow-app spring-boot:run',
      stdout: process.env.KNOWFLOW_E2E_BACKEND_LOGS === 'true' ? 'pipe' : 'ignore',
      env: {
        SPRING_PROFILES_ACTIVE: 'e2e',
        SERVER_PORT: '18081',
        MYSQL_HOST: '127.0.0.1',
        MYSQL_PORT: '13306',
        MYSQL_DATABASE: 'knowflow_e2e',
        MYSQL_USER: 'knowflow_e2e',
        MYSQL_PASSWORD: 'e2e-password',
        REDIS_HOST: '127.0.0.1',
        REDIS_PORT: '16379',
        RABBITMQ_HOST: '127.0.0.1',
        RABBITMQ_PORT: '15673',
        RABBITMQ_USER: 'guest',
        RABBITMQ_PASSWORD: 'guest',
        QDRANT_URL: 'http://127.0.0.1:16333',
        QDRANT_COLLECTION: 'knowflow_e2e_dense_v1',
        KNOWFLOW_RABBIT_PREFIX: 'knowflow.e2e',
        KNOWFLOW_RABBIT_RETRY_DELAYS: '100ms,100ms,100ms',
        KNOWFLOW_MODEL_ALLOW_LOCAL_BASE_URL: 'true',
        KNOWFLOW_EMBEDDING_BASE_URL: 'http://127.0.0.1:18082/v1',
        KNOWFLOW_EMBEDDING_API_KEY: 'test-key',
        KNOWFLOW_EMBEDDING_MODEL: 'stub-embedding',
        KNOWFLOW_SECURITY_MASTER_KEY: process.env.KNOWFLOW_SECURITY_MASTER_KEY ?? '',
        KNOWFLOW_DOCUMENT_STORAGE_ROOT: process.env.KNOWFLOW_E2E_STORAGE_ROOT ?? '../.e2e/files',
      },
      url: 'http://127.0.0.1:18081/actuator/health',
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      env: {
        KNOWFLOW_API_PROXY_TARGET: 'http://127.0.0.1:18081',
      },
      url: 'http://127.0.0.1:15173/chat',
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
