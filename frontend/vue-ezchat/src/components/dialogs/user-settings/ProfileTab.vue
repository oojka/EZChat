<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import { useImageStore } from '@/stores/imageStore'
import { updateProfileApi, type UpdateProfileReq } from '@/api/User'
import { uploadAvatarApi } from '@/api/Auth'
import Avatar from '@/components/Avatar.vue'
import type { Image } from '@/type'

const emit = defineEmits<{
    (e: 'close'): void
}>()

const { t } = useI18n()
const userStore = useUserStore()
const imageStore = useImageStore()
const { loginUserInfo } = storeToRefs(userStore)

// Form data
const nickname = ref('')
const bio = ref('')
const avatarImage = ref<Image | undefined>(undefined)
const uploadedAvatarAssetId = ref<number | undefined>(undefined)
const isSubmitting = ref(false)

// Init form from store
watch(
    loginUserInfo,
    (info) => {
        if (info) {
            nickname.value = info.nickname || ''
            bio.value = info.bio || ''
            avatarImage.value = info.avatar
            uploadedAvatarAssetId.value = undefined
        }
    },
    { immediate: true }
)

// Avatar upload
const handleAvatarClick = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png,image/gif,image/webp'
    input.onchange = async (e: Event) => {
        const file = (e.target as HTMLInputElement).files?.[0]
        if (!file) return

        // File size check (2MB)
        if (file.size > 2 * 1024 * 1024) {
            ElMessage.warning(t('upgrade.avatar_size_error'))
            return
        }

        try {
            // Upload immediately
            const result = await uploadAvatarApi(file)
            if (result.status === 1 && result.data) {
                const uploadedImage = result.data
                ElMessage.success(t('upgrade.avatar_upload_success'))

                // Build preview image with blob URL
                const previewUrl = await imageStore.ensureThumbBlobUrl(uploadedImage)
                avatarImage.value = {
                    imageName: uploadedImage.imageName || '',
                    imageUrl: uploadedImage.imageUrl || '',
                    imageThumbUrl: uploadedImage.imageThumbUrl || uploadedImage.imageUrl || '',
                    blobUrl: previewUrl,
                    blobThumbUrl: previewUrl,
                }
                uploadedAvatarAssetId.value = uploadedImage.assetId
            } else {
                ElMessage.error(result.message || t('upgrade.avatar_upload_failed'))
            }
        } catch {
            ElMessage.error(t('upgrade.avatar_upload_failed'))
        }
    }
    input.click()
}

// Form validation
const canSubmit = computed(() => {
    return nickname.value.trim().length >= 2
})

// Submit
const handleSubmit = async () => {
    if (!canSubmit.value || isSubmitting.value) return

    isSubmitting.value = true
    try {
        const payload: UpdateProfileReq = {
            nickname: nickname.value.trim(),
            bio: bio.value.trim() || undefined,
        }

        // Include avatar if uploaded
        if (uploadedAvatarAssetId.value && avatarImage.value) {
            payload.avatar = {
                imageName: avatarImage.value.imageName,
                imageUrl: avatarImage.value.imageUrl,
                imageThumbUrl: avatarImage.value.imageThumbUrl,
                assetId: uploadedAvatarAssetId.value,
            }
        }

        const res = await updateProfileApi(payload)
        if (res.status === 1) {
            ElMessage.success(t('user_settings.profile_saved'))
            // Refresh user info
            await userStore.syncLoginUserInfo()
            emit('close')
        }
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="profile-tab">
        <el-form class="form-section profile-form" label-position="top" hide-required-asterisk @submit.prevent>
            <!-- Avatar -->
            <div class="avatar-block">
                <div class="avatar-wrapper" @click="handleAvatarClick">
                    <Avatar :image="avatarImage" :text="nickname" :size="100" shape="square" :editable="true"
                        :icon-size="32" />
                </div>
                <p class="form-hint">{{ t('user_settings.avatar_hint') }}</p>
            </div>

            <!-- Nickname -->
            <el-form-item :label="t('user_settings.nickname')">
                <el-input v-model="nickname" :placeholder="t('user_settings.nickname_placeholder')" maxlength="20"
                    show-word-limit size="large" />
            </el-form-item>

            <!-- Bio -->
            <el-form-item :label="t('user_settings.bio')">
                <el-input v-model="bio" type="textarea" :rows="3" :placeholder="t('user_settings.bio_placeholder')"
                    maxlength="255" show-word-limit />
            </el-form-item>
        </el-form>

        <!-- Actions -->
        <div class="form-actions">
            <el-button type="primary" :loading="isSubmitting" :disabled="!canSubmit" @click="handleSubmit"
                class="save-btn">
                {{ t('common.save') }}
            </el-button>
        </div>
    </div>
</template>

<style scoped>
.profile-tab {
    display: flex;
    flex-direction: column;
    height: 100%;
}



.form-section {
    flex: 1;
    overflow-y: auto;
    padding-top: 12px;
    padding-right: 4px;
    /* Space for scrollbar */
}

/* Avatar Block */
.avatar-block {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 24px;
}

.avatar-wrapper {
    cursor: pointer;
    border-radius: 20px;
    /* Match Avatar shape (approx) */
    transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.avatar-wrapper:hover {
    transform: scale(1.05);
}

.form-hint {
    font-size: 12px;
    color: var(--text-400);
    margin: 8px 0 0;
    text-align: center;
}

/* Form Item overrides to match RoomSettings */
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

.form-actions {
    margin-top: auto;
    padding-top: 24px;
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid var(--border-light);
}

.save-btn {
    min-width: 100px;
    font-weight: 700;
}
</style>
