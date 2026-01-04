<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-content">
      <div class="error-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M12 8V12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M12 16H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      
      <h2 class="error-title">{{ t('error.boundary_title') }}</h2>
      <p class="error-message">{{ errorMessage }}</p>
      
      <div class="error-details" v-if="showDetails">
        <pre class="error-stack">{{ errorStack }}</pre>
        <div class="error-context" v-if="errorContext">
          <h4>{{ t('error.context_title') }}</h4>
          <pre>{{ JSON.stringify(errorContext, null, 2) }}</pre>
        </div>
      </div>
      
      <div class="error-actions">
        <el-button type="primary" @click="handleRetry" :loading="isRetrying">
          {{ t('error.retry_button') }}
        </el-button>
        <el-button @click="toggleDetails">
          {{ showDetails ? t('error.hide_details') : t('error.show_details') }}
        </el-button>
        <el-button @click="handleGoHome">
          {{ t('error.go_home') }}
        </el-button>
        <el-button @click="handleReport" v-if="canReport">
          {{ t('error.report_button') }}
        </el-button>
      </div>
    </div>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, computed, onErrorCaptured, provide } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton } from 'element-plus';
import i18n from '@/i18n';
import { errorHandler, type FrontendError, ErrorType, ErrorSeverity } from '@/error/ErrorHandler';

const { t } = i18n.global;
const router = useRouter();

// 错误状态
const hasError = ref(false);
const error = ref<Error | null>(null);
const errorInfo = ref<any>(null);
const isRetrying = ref(false);
const showDetails = ref(false);

// 计算属性
const errorMessage = computed(() => {
  if (!error.value) return t('error.unknown_error');
  
  // 如果是前端错误对象，使用其消息
  const frontendError = error.value as any;
  if (frontendError.type && frontendError.message) {
    return frontendError.message;
  }
  
  return error.value.message || t('error.unknown_error');
});

const errorStack = computed(() => {
  if (!error.value) return '';
  return error.value.stack || t('error.no_stack_trace');
});

const errorContext = computed(() => {
  if (!errorInfo.value) return null;
  
  return {
    component: errorInfo.value.component?.$options?.name || 'Unknown',
    lifecycle: errorInfo.value.lifecycleHook || 'Unknown',
    timestamp: new Date().toISOString(),
    route: router.currentRoute.value,
  };
});

const canReport = computed(() => {
  return errorHandler.getConfig().enableErrorReporting;
});

// 错误捕获
onErrorCaptured((err, instance, info) => {
  console.error('ErrorBoundary captured error:', err, instance, info);
  
  // 记录错误
  error.value = err;
  errorInfo.value = { instance, info };
  hasError.value = true;
  
  // 通过错误处理器处理
  errorHandler.handleVueError(err, instance, info);
  
  // 阻止错误继续向上传播
  return false;
});

// 方法
const handleRetry = async () => {
  isRetrying.value = true;
  try {
    // 等待一小段时间，让状态有机会重置
    await new Promise(resolve => setTimeout(resolve, 300));
    
    // 重置错误状态
    hasError.value = false;
    error.value = null;
    errorInfo.value = null;
    showDetails.value = false;
    
    // 触发重试事件
    emit('retry');
  } finally {
    isRetrying.value = false;
  }
};

const toggleDetails = () => {
  showDetails.value = !showDetails.value;
};

const handleGoHome = () => {
  router.push('/').catch(() => {
    // 如果路由跳转失败，刷新页面
    window.location.href = '/';
  });
};

const handleReport = () => {
  if (!error.value) return;
  
  // 创建前端错误对象并上报
  const frontendError: FrontendError = {
    type: ErrorType.COMPONENT_RENDER_ERROR,
    severity: ErrorSeverity.ERROR,
    message: error.value.message,
    stack: error.value.stack,
    timestamp: new Date(),
    component: errorInfo.value?.instance?.$options?.name,
    userAction: errorInfo.value?.info,
    originalError: error.value,
  };
  
  errorHandler.handleError(frontendError);
};

// 提供错误重置方法给子组件
const resetError = () => {
  hasError.value = false;
  error.value = null;
  errorInfo.value = null;
};

provide('errorBoundaryReset', resetError);

// 事件
const emit = defineEmits<{
  retry: [];
}>();

// 暴露方法给父组件
defineExpose({
  resetError,
  hasError,
});
</script>

<style scoped>
.error-boundary {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 12px;
  margin: 1rem;
}

.error-content {
  max-width: 600px;
  width: 100%;
  text-align: center;
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.error-icon {
  color: #f56c6c;
  margin-bottom: 1.5rem;
}

.error-icon svg {
  width: 64px;
  height: 64px;
}

.error-title {
  color: #303133;
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.error-message {
  color: #606266;
  font-size: 1rem;
  margin-bottom: 1.5rem;
  line-height: 1.5;
}

.error-details {
  margin: 1.5rem 0;
  text-align: left;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 1rem;
  max-height: 300px;
  overflow-y: auto;
}

.error-stack {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.875rem;
  color: #909399;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.error-context {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #dcdfe6;
}

.error-context h4 {
  color: #303133;
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.error-context pre {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.75rem;
  color: #909399;
  background: #fafafa;
  padding: 0.5rem;
  border-radius: 4px;
  overflow-x: auto;
}

.error-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 1.5rem;
}

@media (max-width: 640px) {
  .error-boundary {
    padding: 1rem;
    margin: 0.5rem;
  }
  
  .error-content {
    padding: 1.5rem;
  }
  
  .error-actions {
    flex-direction: column;
  }
  
  .error-actions .el-button {
    width: 100%;
  }
}
</style>
