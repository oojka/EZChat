<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => router.push('/')
const goBack = () => router.back()
</script>

<template>
  <div class="error-container">
    <div class="glass-card">
      <div class="content-wrapper">
        <h1 class="error-code">404</h1>
        <h2 class="error-title">Page Not Found</h2>
        <p class="error-desc">
          Sorry, we couldn't find the page you're looking for.
          It might have been moved or deleted.
        </p>

        <div class="actions">
          <el-button type="primary" size="large" @click="goHome" round>
            Return Home
          </el-button>
          <el-button size="large" @click="goBack" round>
            Go Back
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.error-container {
  min-height: 100vh;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--bg-page);
  padding: 20px;
}

.glass-card {
  width: 100%;
  max-width: 480px;
  padding: 48px;
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--border-glass);
  box-shadow: var(--shadow-glass);
  border-radius: var(--radius-lg);
  text-align: center;
  transition: all 0.3s var(--ease-out-expo);
}

.error-code {
  font-size: 80px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 16px;
  letter-spacing: -2px;
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-900);
  margin-bottom: 16px;
}

.error-desc {
  font-size: 16px;
  color: var(--text-500);
  margin-bottom: 32px;
  line-height: 1.6;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* Mobile Responsiveness */
@media (max-width: 640px) {
  .glass-card {
    padding: 32px 24px;
  }
  
  .error-code {
    font-size: 60px;
  }
  
  .actions {
    flex-direction: column;
  }
  
  .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
}
</style>
