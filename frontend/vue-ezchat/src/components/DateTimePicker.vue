<script setup lang="ts">
import { Calendar, EditPen } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  modelValue?: Date | null
  radioValue?: number | null
  disabledDate?: (time: Date) => boolean
}

interface Emits {
  (e: 'update:modelValue', value: Date | null): void
  (e: 'update:radioValue', value: number | null): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()

const selectedDate = computed({
  get: () => props.modelValue || null,
  set: (val) => emit('update:modelValue', val)
})

const selectedDateRadio = computed({
  get: () => props.radioValue ?? null,
  set: (val) => emit('update:radioValue', val)
})
</script>

<template>
  <div class="expiry-config-wrapper">
    <div class="config-glass-card">
      <div class="config-header">
        <div class="title-with-icon">
          <el-icon><Calendar /></el-icon>
          <span>{{ t('create_chat.expiry_title') }}</span>
        </div>
      </div>
      <div class="expiry-content">
        <el-radio-group
          v-model="selectedDateRadio"
          class="ez-radio-group-modern"
        >
          <el-radio-button :value="1">{{ t('create_chat.day_n', { n: 1 }) }}</el-radio-button>
          <el-radio-button :value="7">{{ t('create_chat.day_n', { n: 7 }) }}</el-radio-button>
          <el-radio-button :value="30">{{ t('create_chat.day_n', { n: 30 }) }}</el-radio-button>
        </el-radio-group>

        <div class="custom-date-picker-area">
          <el-date-picker
            v-model="selectedDate"
            type="datetime"
            :placeholder="t('create_chat.custom_date')"
            format="YYYY/MM/DD HH:mm"
            :disabled-date="props.disabledDate"
            :prefix-icon="EditPen"
            class="ez-date-picker-custom"
            style="width: 100%"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.expiry-config-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  width: 100%;
}

.expiry-config-wrapper .config-glass-card {
  width: 100%;
}

.config-glass-card {
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: var(--radius-md);
  padding: 24px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s var(--ease-out-expo);
}

.config-glass-card:hover {
  border-color: var(--primary-light);
  transform: translateY(-2px);
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 800;
  color: var(--text-700);
  padding-left: 4px;
}

.expiry-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 8px;
}

.ez-radio-group-modern {
  display: flex;
  width: 100%;
  gap: 8px;
}

:deep(.ez-radio-group-modern .el-radio-button) {
  flex: 1;
  margin: 0 !important;
}

:deep(.ez-radio-group-modern .el-radio-button__inner) {
  width: 100%;
  height: 48px !important;
  background: var(--bg-page);
  border: 1px solid transparent !important;
  border-radius: var(--radius-base) !important;
  color: var(--text-500);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s var(--ease-out-expo);
  font-size: 13px;
  margin: 0 !important;
  padding: 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.ez-radio-group-modern .el-radio-button:first-child .el-radio-button__inner) {
  margin-left: 0 !important;
}

:deep(.ez-radio-group-modern .el-radio-button:last-child .el-radio-button__inner) {
  margin-right: 0 !important;
}

:deep(.ez-radio-group-modern .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: var(--bg-card) !important;
  border-color: var(--primary) !important;
  color: var(--primary) !important;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1) !important;
}

:deep(.ez-radio-group-modern .el-radio-button__original-radio:checked + .el-radio-button__inner span) {
  color: var(--primary) !important;
}

.custom-date-picker-area {
  width: 100%;
}

/* Date picker 高度统一为 48px */
:deep(.custom-date-picker-area .el-input__wrapper) {
  height: 48px !important;
}

/* Date picker 样式统一由父组件的 deep selector 处理 */
</style>
