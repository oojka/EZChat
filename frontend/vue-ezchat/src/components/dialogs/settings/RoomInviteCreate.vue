<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DateTimePicker from '@/components/DateTimePicker.vue'
import { computed } from 'vue'

const { t } = useI18n()

interface Props {
    isCreating: boolean
    disabledDate: (time: Date) => boolean
    modelValue?: Date | null
    radioValue?: number | null
    oneTimeLink?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (e: 'update:modelValue', val: Date | null): void
    (e: 'update:radioValue', val: number | null): void
    (e: 'update:oneTimeLink', val: boolean): void
    (e: 'confirm'): void
    (e: 'cancel'): void
}>()

const selectedDate = computed({
    get: () => props.modelValue ?? null,
    set: (val) => emit('update:modelValue', val)
})

const selectedDateRadio = computed({
    get: () => props.radioValue ?? null,
    set: (val) => emit('update:radioValue', val)
})

const oneTimeLinkModel = computed({
    get: () => props.oneTimeLink ?? false,
    set: (val) => emit('update:oneTimeLink', val)
})
</script>

<template>
    <div class="step-content form-content">
        <div class="form-section">
            <p class="form-desc">{{ t('room_settings.create_invite_desc') }}</p>

            <DateTimePicker v-model="selectedDate" v-model:radio-value="selectedDateRadio"
                v-model:oneTimeLink="oneTimeLinkModel" :disabled-date="props.disabledDate" />
        </div>

        <div class="form-footer-actions">
            <el-button @click="$emit('cancel')">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" :loading="props.isCreating" @click="$emit('confirm')">
                {{ t('common.confirm') }}
            </el-button>
        </div>
    </div>
</template>

<style scoped>
/* --- Form Step --- */
.step-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.form-content {
    justify-content: space-between;
}

.form-desc {
    font-size: 13px;
    color: var(--text-500);
    margin: 0 0 20px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
    line-height: 1.5;
}

.form-footer-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding-top: 20px;
    border-top: 1px solid var(--el-border-color-light);
}
</style>
