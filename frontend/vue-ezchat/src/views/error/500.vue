<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const clientIp = ref<string>('Loading...')
const currentTime = ref<string>('')
const rayId = ref<string>('')

const fetchClientIp = async () => {
  try {
    const response = await fetch('https://api.ipify.org?format=json')
    if (response.ok) {
      const data = await response.json()
      clientIp.value = data.ip
    } else {
      clientIp.value = 'Unknown'
    }
  } catch (e) {
    clientIp.value = 'Unknown'
  }
}

const formatTime = () => {
  return new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC'
}

const generateRayId = () => {
  // Generate a random hex string to simulate a Ray ID for reference
  return Math.random().toString(16).substr(2, 16)
}

const handleReload = () => {
  window.location.reload()
}

const goHome = () => {
  router.push('/')
}

onMounted(() => {
  document.title = '500 Internal Server Error | EZ Chat'
  currentTime.value = formatTime()
  rayId.value = generateRayId()
  fetchClientIp()
})
</script>

<template>
  <div class="error-container">
    <div class="glass-card">
      <div class="content-wrapper">
        <h1 class="error-code">500</h1>
        <h2 class="error-title">Internal Server Error</h2>
        <p class="error-desc">
          Something went wrong on our end. We are working to fix it.
          Please try again later.
        </p>
        
        <div class="details-box">
          <div class="detail-item">
            <span class="label">Error Code:</span>
            <span class="value">500</span>
          </div>
          <div class="detail-item">
            <span class="label">Ref ID:</span>
            <span class="value font-mono">{{ rayId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Client IP:</span>
            <span class="value">{{ clientIp }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Time:</span>
            <span class="value">{{ currentTime }}</span>
          </div>
        </div>

        <div class="actions">
          <el-button type="primary" size="large" @click="handleReload" round>
            Reload Page
          </el-button>
          <el-button size="large" @click="goHome" round>
            Back to Home
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
  max-width: 600px;
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

.details-box {
  background: var(--bg-glass-overlay);
  border-radius: var(--radius-md);
  padding: 20px;
  margin-bottom: 32px;
  border: 1px solid var(--border-glass);
  text-align: left;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.detail-item:last-child {
  margin-bottom: 0;
}

.label {
  color: var(--text-400);
}

.value {
  color: var(--text-700);
  font-weight: 500;
  text-align: right;
}

.font-mono {
  font-family: monospace;
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
