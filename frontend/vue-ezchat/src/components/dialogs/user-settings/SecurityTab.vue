<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import { updatePasswordApi } from '@/api/User'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'
import PasswordInput from '@/components/PasswordInput.vue'
import { isValidPassword } from '@/utils/validators'

const emit = defineEmits<{
    (e: 'close'): void
}>()

const { t } = useI18n()
const userStore = useUserStore()

// Form data
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const isSubmitting = ref(false)

// Validation
const passwordError = computed(() => {
    if (!newPassword.value) return ''
    if (!isValidPassword(newPassword.value)) {
        return t('validation.password_length')
    }
    return ''
})

const confirmError = computed(() => {
    if (!confirmPassword.value) return ''
    if (confirmPassword.value !== newPassword.value) {
        return t('validation.password_mismatch')
    }
    return ''
})

const canSubmit = computed(() => {
    return (
        oldPassword.value.length > 0 &&
        isValidPassword(newPassword.value) &&
        confirmPassword.value === newPassword.value &&
        !isSubmitting.value
    )
})

// Submit
const handleSubmit = async () => {
    if (!canSubmit.value) return

    isSubmitting.value = true
    try {
        const res = await updatePasswordApi({
            oldPassword: oldPassword.value,
            newPassword: newPassword.value,
        })

        if (res.code === 0) {
            // Show alert and force logout
            emit('close')
            await showAlertDialog({
                title: t('common.info'),
                message: t('user_settings.password_changed_relogin'),
                confirmText: t('common.confirm'),
            })
            // Force logout
            await userStore.logout()
        }
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="security-tab">
        <el-form class="feature-form security-form" label-position="top" hide-required-asterisk @submit.prevent>
            <!-- Old Password -->
            <el-form-item :label="t('user_settings.old_password')">
                <PasswordInput v-model="oldPassword" :placeholder="t('user_settings.old_password_placeholder')"
                    size="large" />
            </el-form-item>

            <!-- New Password -->
            <el-form-item :label="t('user_settings.new_password')" :error="passwordError">
                <PasswordInput v-model="newPassword" :placeholder="t('user_settings.new_password_placeholder')"
                    size="large" />
            </el-form-item>

            <!-- Confirm Password -->
            <el-form-item :label="t('user_settings.confirm_password')" :error="confirmError">
                <PasswordInput v-model="confirmPassword" :placeholder="t('user_settings.confirm_password_placeholder')"
                    size="large" />
            </el-form-item>
        </el-form>

        <!-- Actions -->
        <div class="form-actions">
            <el-button type="primary" :loading="isSubmitting" :disabled="!canSubmit" @click="handleSubmit"
                class="save-btn">
                {{ t('user_settings.change_password') }}
            </el-button>
        </div>
    </div>
</template>

<style scoped>
.security-tab {
    display: flex;
    flex-direction: column;
    height: 100%;
}



.feature-form {
    flex: 1;
    overflow-y: auto;
    padding-top: 12px;
    padding-right: 4px;
}

/* Form Item overrides */
:deep(.el-form-item__label) {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-700);
    padding-bottom: 8px !important;
    line-height: 1.2 !important;
    letter-spacing: 0.3px;
}

:deep(.el-form-item) {
    margin-bottom: 24px;
}

/* Shake animation is handled by Element Plus form error transition */

.form-actions {
    margin-top: auto;
    padding-top: 24px;
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid var(--border-light);
}

.save-btn {
    min-width: 120px;
    font-weight: 700;
}
</style>
