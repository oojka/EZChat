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
  <div class="expiry-config-container">
    <div class="card-title-row">
      <el-icon><Calendar /></el-icon><span>{{ t('create_chat.expiry_title') }}</span>
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
</template>

<style scoped>
.expiry-config-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-title-row {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-700);
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 4px;
}

.expiry-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ez-radio-group-modern {
  display: flex;
  width: 100%;
}

:deep(.ez-radio-group-modern .el-radio-button) {
  flex: 1;
}

:deep(.ez-radio-group-modern .el-radio-button__inner) {
  width: 100%;
  height: 40px;
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
  margin: 0 4px;
}

:deep(.ez-radio-group-modern .el-radio-button:first-child .el-radio-button__inner) {
  margin-left: 0;
}

:deep(.ez-radio-group-modern .el-radio-button:last-child .el-radio-button__inner) {
  margin-right: 0;
}

:deep(.ez-radio-group-modern .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: var(--bg-card) !important;
  border-color: var(--primary) !important;
  color: var(--primary);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1) !important;
}

/* Date picker consistency is handled by parent's deep selector for .el-input__wrapper */
</style>
