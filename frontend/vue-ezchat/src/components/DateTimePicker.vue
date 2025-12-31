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
  <div class="config-card-flat expiry-card">
    <div class="card-title-row">
      <el-icon><Calendar /></el-icon><span>{{ t('create_chat.expiry_title') }}</span>
    </div>
    <div class="expiry-vertical-layout">
      <el-radio-group
        v-model="selectedDateRadio"
        class="modern-radios-compact full-width"
      >
        <el-radio-button :value="1">{{ t('create_chat.day_n', { n: 1 }) }}</el-radio-button>
        <el-radio-button :value="7">{{ t('create_chat.day_n', { n: 7 }) }}</el-radio-button>
        <el-radio-button :value="30">{{ t('create_chat.day_n', { n: 30 }) }}</el-radio-button>
      </el-radio-group>

      <div class="custom-date-wrapper full-width">
        <el-date-picker
          v-model="selectedDate"
          type="datetime"
          :placeholder="t('create_chat.custom_date')"
          format="YYYY/MM/DD HH:mm"
          :disabled-date="props.disabledDate"
          :prefix-icon="EditPen"
          class="custom-picker-inline"
          style="width: 100%"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-card-flat {
  border-radius: var(--radius-md);
  padding: 14px 18px;
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
}

.expiry-card {
  margin-bottom: 24px;
  padding-bottom: 24px;
}

.card-title-row {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-700);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.expiry-vertical-layout {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.custom-date-wrapper {
  margin-bottom: 12px;
}

.full-width {
  width: 100%;
}

:deep(.modern-radios-compact) {
  display: flex;
}

:deep(.modern-radios-compact .el-radio-button) {
  flex: 1;
  margin-right: 8px;
}

:deep(.modern-radios-compact .el-radio-button:last-child) {
  margin-right: 0;
}

:deep(.modern-radios-compact .el-radio-button__inner) {
  width: 100%;
  height: 36px;
  background: var(--bg-page);
  border: 1px solid var(--el-border-color-light) !important;
  border-radius: var(--radius-sm) !important;
  color: var(--text-500);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  font-size: 12px;
}

:deep(.modern-radios-compact .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: var(--primary) !important;
  border-color: var(--primary) !important;
  color: #fff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2) !important;
}
</style>
