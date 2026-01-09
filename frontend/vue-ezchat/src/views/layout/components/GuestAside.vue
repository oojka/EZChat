<script setup lang="ts">
import { onMounted, computed, ref, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import ChatItem from './ChatItem.vue'
import Avatar from '@/components/Avatar.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { Trophy, Check, Close } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules, type UploadProps } from 'element-plus'
import { upgradeUserApi } from '@/api/User'
import { uploadAvatarApi } from '@/api/Auth'
import type { RegisterInfo } from '@/type'
import { isValidUsername, isValidNickname, isValidPassword } from '@/utils/validators'

const { t } = useI18n()
const roomStore = useRoomStore()
const userStore = useUserStore()
const { roomList, currentRoomCode } = storeToRefs(roomStore)

// 权益文案配置
const benefits = computed(() => [
    t('upgrade.benefits.create_chat'),
    t('upgrade.benefits.unlimited_join'),
    t('upgrade.benefits.cloud_storage'),
    t('upgrade.benefits.multi_device'),
    t('upgrade.benefits.custom_profile'),
])

const currentRoom = computed(() => {
    // 优先取当前选中的房间
    if (currentRoomCode.value) {
        const room = roomStore.getRoomByCode(currentRoomCode.value)
        if (room) return room
    }
    // 也就是访客通常只有一个房间，默认取第一个
    return roomList.value[0]
})

onMounted(() => {
    // 如果当前没有选中房间，默认选中第一个
    const firstRoom = roomList.value[0]
    if (!currentRoomCode.value && firstRoom) {
        currentRoomCode.value = firstRoom.chatCode
    }
})

// --- Upgrade Dialog Logic ---
const upgradeDialogVisible = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<RegisterInfo>({
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    bio: '',
    avatar: {
        imageUrl: '',
        imageThumbUrl: ''
    }
})

const emptyAvatar = {
    imageUrl: '',
    imageThumbUrl: ''
}

// 校验规则
const validatePass2 = (rule: any, value: any, callback: any) => {
    if (value === '') {
        callback(new Error(t('validation.confirm_password_required')))
    } else if (value !== form.password) {
        callback(new Error(t('validation.password_mismatch')))
    } else {
        callback()
    }
}

const rules = reactive<FormRules>({
    username: [
        { required: true, message: t('validation.username_required'), trigger: 'blur' },
        {
            validator: (rule: any, value: any, callback: any) => {
                if (!isValidUsername(value)) {
                    callback(new Error(t('validation.username_format')))
                } else {
                    callback()
                }
            }, trigger: 'blur'
        }
    ],
    password: [
        { required: true, message: t('validation.password_required'), trigger: 'blur' },
        {
            validator: (rule: any, value: any, callback: any) => {
                if (!isValidPassword(value)) {
                    callback(new Error(t('validation.password_format')))
                } else {
                    callback()
                }
            }, trigger: 'blur'
        }
    ],
    confirmPassword: [
        { validator: validatePass2, trigger: 'blur' }
    ],
    nickname: [
        { required: true, message: t('validation.nickname_required'), trigger: 'blur' },
        {
            validator: (rule: any, value: any, callback: any) => {
                if (!isValidNickname(value)) {
                    callback(new Error(t('validation.nickname_format')))
                } else {
                    callback()
                }
            }, trigger: 'blur'
        }
    ]
})

const handleUpgrade = () => {
    // 回显当前访客信息
    form.username = ''
    form.password = ''
    form.confirmPassword = ''
    form.nickname = userStore.loginUserInfo?.nickname || ''
    form.bio = userStore.loginUserInfo?.bio || ''
    form.avatar = userStore.loginUserInfo?.avatar ? { ...userStore.loginUserInfo.avatar } : { ...emptyAvatar }
    upgradeDialogVisible.value = true
}

const submitUpgrade = async (formEl: FormInstance | undefined) => {
    if (!formEl) return
    await formEl.validate(async (valid, fields) => {
        if (valid) {
            loading.value = true
            try {
                const res = await upgradeUserApi(form)
                if (res.status === 1 && res.data) {
                    ElMessage.success(t('upgrade.success'))
                    // 更新本地登录态
                    userStore.setLoginUser(res.data)
                    upgradeDialogVisible.value = false
                } else {
                    ElMessage.error(res.message || t('upgrade.failed'))
                }
            } catch (error: any) {
                console.error(error)
                // 错误已经在 request.ts 拦截处理，这里防止未捕获异常
            } finally {
                loading.value = false
            }
        } else {
            console.log('error submit!', fields)
        }
    })
}

// 头像上传
const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    if (response?.data) {
        form.avatar = response.data
        ElMessage.success(t('upgrade.avatar_upload_success'))
        return
    }
    ElMessage.error(t('upgrade.avatar_upload_failed'))
}

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
    if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
        ElMessage.error(t('upgrade.avatar_format_error'))
        return false
    } else if (rawFile.size / 1024 / 1024 > 2) {
        ElMessage.error(t('upgrade.avatar_size_error'))
        return false
    }
    return true
}

// 自定义上传请求 Request
const customUploadRequest = async (options: any) => {
    const { file, onSuccess, onError } = options
    try {
        const res = await uploadAvatarApi(file)
        onSuccess(res)
    } catch (err) {
        onError(err)
    }
}
</script>

<template>
    <div class="guest-aside-container">
        <div class="header">
            <span class="title">{{ t('aside.current_chat') }}</span>
        </div>

        <div class="chat-list-container">
            <ChatItem v-if="currentRoom" :chat="currentRoom" :isActive="true" />

            <div v-else class="empty-tip">
                {{ t('aside.no_chat') }}
            </div>
        </div>

        <div class="spacer"></div>

        <!-- 升级引导区域 -->
        <div class="upgrade-section">
            <div class="upgrade-card">
                <div class="icon-wrapper">
                    <el-icon :size="24" color="#E6A23C">
                        <Trophy />
                    </el-icon>
                </div>
                <div class="text-content">
                    <div class="upgrade-title">{{ t('upgrade.title') }}</div>
                    <ul class="benefit-list">
                        <li class="benefit-item" v-for="(item, index) in benefits" :key="index">
                            <el-icon class="check-icon">
                                <Check />
                            </el-icon>
                            <span>{{ item }}</span>
                        </li>
                    </ul>
                </div>
                <el-button type="primary" class="upgrade-btn" @click="handleUpgrade">
                    {{ t('upgrade.button') }}
                </el-button>
            </div>
        </div>

        <el-dialog v-model="upgradeDialogVisible" width="850px" class="ez-modern-dialog upgrade-dialog-modern"
            align-center destroy-on-close :show-close="false" :close-on-click-modal="false">

            <template #header>
                <div class="dialog-header-actions">
                    <button class="ez-close-btn" type="button" @click="upgradeDialogVisible = false">
                        <el-icon>
                            <Close />
                        </el-icon>
                    </button>
                </div>
            </template>

            <el-form ref="formRef" :model="form" :rules="rules" label-width="0" class="dialog-layout-grid"
                hide-required-asterisk status-icon>
                <!-- LEFT COLUMN: Avatar & Branding & Profile -->
                <div class="dialog-left-col">
                    <div class="avatar-upload-section">
                        <el-upload class="avatar-uploader-large" action="#" :http-request="customUploadRequest"
                            :show-file-list="false" :on-success="handleAvatarSuccess"
                            :before-upload="beforeAvatarUpload">
                            <Avatar :thumb-url="form.avatar?.imageThumbUrl" :url="form.avatar?.imageUrl"
                                :text="form.nickname" :size="150" shape="square" editable :icon-size="48" />
                        </el-upload>
                        <p class="avatar-tip">{{ t('upgrade.avatar_tip') }}</p>
                    </div>

                    <!-- Moved Profile Inputs -->
                    <div class="left-col-inputs">
                        <el-form-item prop="nickname">
                            <span class="input-label">昵称</span>
                            <el-input v-model="form.nickname" :placeholder="t('upgrade.nickname_placeholder')" size="large" />
                        </el-form-item>
                        <el-form-item prop="bio">
                            <span class="input-label">个人简介</span>
                            <el-input v-model="form.bio" type="textarea" :rows="3" :placeholder="t('upgrade.bio_placeholder')"
                                resize="none" size="large" />
                        </el-form-item>
                    </div>
                </div>

                <!-- RIGHT COLUMN: Auth Fields -->
                <div class="dialog-right-col">
                    <div class="dialog-header-right">
                        <h3>{{ t('upgrade.dialog_title') }}</h3>
                        <p class="subtitle">{{ t('upgrade.dialog_subtitle') }}</p>
                    </div>

                    <div class="upgrade-form-fields">
                        <el-form-item prop="avatar" class="hidden-item" />

                        <el-form-item prop="username">
                            <span class="input-label">账号</span>
                            <el-input v-model="form.username" :placeholder="t('upgrade.username_placeholder')" size="large" />
                        </el-form-item>

                        <el-form-item prop="password">
                            <span class="input-label">密码</span>
                            <PasswordInput v-model="form.password" :placeholder="t('upgrade.password_placeholder')" size="large" />
                        </el-form-item>
                        <el-form-item prop="confirmPassword">
                            <span class="input-label">确认密码</span>
                            <PasswordInput v-model="form.confirmPassword" :placeholder="t('upgrade.confirm_password_placeholder')" size="large" />
                        </el-form-item>
                    </div>

                    <div class="dialog-actions">
                        <el-button @click="upgradeDialogVisible = false" class="action-btn cancel-btn"
                            size="large">{{ t('upgrade.cancel_button') }}</el-button>
                        <el-button type="primary" :loading="loading" @click="submitUpgrade(formRef)"
                            class="action-btn submit-btn" size="large">
                            {{ t('upgrade.submit_button') }}
                        </el-button>
                    </div>
                </div>
            </el-form>
        </el-dialog>
    </div>
</template>

<style scoped>
/* --- Layout --- */
.guest-aside-container {
    display: flex;
    flex-direction: column;
    height: 100%;
    background-color: var(--bg-aside);
    overflow: hidden;
}

.header {
    padding: 16px 20px;
    border-bottom: 1px solid var(--el-border-color-light);
    flex-shrink: 0;
}

.title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-500);
}

.chat-list-container {
    flex-shrink: 0;
}

.empty-tip {
    padding: 20px;
    text-align: center;
    color: var(--text-400);
    font-size: 13px;
}

.spacer {
    flex: 1;
}

/* --- Upgrade Section (Sidebar Bottom) --- */
.upgrade-section {
    padding: 16px;
    flex-shrink: 0;
}

.upgrade-card {
    background: var(--bg-card);
    border: 1px solid var(--el-border-color-light);
    border-radius: 12px;
    padding: 20px 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    box-shadow: var(--shadow-sm);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    cursor: pointer;
}

.upgrade-card:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
    border-color: var(--primary-light-5);
}

.icon-wrapper {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background-color: var(--warning-light-9);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
}

.upgrade-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-900);
    margin-bottom: 12px;
    text-align: center;
    line-height: 1.2;
}

.benefit-list {
    list-style: none;
    padding: 0;
    margin: 0 0 20px 0;
    width: 100%;
}

.benefit-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--text-600);
    margin-bottom: 8px;
    line-height: 1.4;
}

.check-icon {
    color: var(--success);
    font-size: 14px;
    flex-shrink: 0;
}

.upgrade-btn {
    width: 100%;
    border-radius: 8px;
    font-weight: 600;
    height: 36px;
}

/* --- Dialog: Modern Glass Style --- */

/* Reset Element Plus Dialog Styles */
:deep(.ez-modern-dialog) {
    background: var(--bg-glass) !important;
    backdrop-filter: var(--blur-glass) !important;
    -webkit-backdrop-filter: var(--blur-glass) !important;
    border: 1px solid var(--border-glass) !important;
    border-radius: 20px !important;
    box-shadow: var(--shadow-glass) !important;
    overflow: hidden;
    transition: all 0.3s var(--ease-out-expo);
}

html.dark :deep(.ez-modern-dialog) {
    background: var(--bg-card) !important;
    backdrop-filter: none !important;
    -webkit-backdrop-filter: none !important;
}

.upgrade-dialog-modern :deep(.el-dialog__header) {
    padding: 0 !important;
    margin: 0 !important;
    height: 0;
}

.upgrade-dialog-modern :deep(.el-dialog__body) {
    padding: 0 !important;
}

/* --- Dialog Layout: 2-Column Grid --- */
.dialog-layout-grid {
    display: flex;
    min-height: 500px;
}

/* Left Column */
.dialog-left-col {
    flex: 0 0 320px;
    background: var(--bg-fill-1);
    /* Subtle dark/light background */
    border-right: 1px solid var(--border-glass);
    padding: 40px 24px;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
}

.dialog-header-right {
    margin-bottom: 24px;
}

.dialog-header-right h3 {
    font-size: 20px;
    font-weight: 800;
    color: var(--text-900);
    margin: 0 0 8px 0;
    letter-spacing: -0.5px;
}

.subtitle {
    font-size: 13px;
    color: var(--text-500);
    margin: 0;
}

.avatar-upload-section {
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
}

.avatar-preview-lg,
.placeholder-square-lg {
    width: 128px;
    /* Larger avatar */
    height: 128px;
    border-radius: 24px;
    /* Squircle */
    overflow: hidden;
    position: relative;
    cursor: pointer;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    /* Enhanced shadow */
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    background: var(--bg-page);
}

.placeholder-square-lg {
    border: 2px dashed var(--el-border-color);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--text-400);
    gap: 8px;
}

.placeholder-square-lg:hover {
    border-color: var(--primary);
    color: var(--primary);
    background: var(--primary-light-9);
}

.upload-text {
    font-size: 13px;
    font-weight: 600;
}

.avatar-tip {
    font-size: 12px;
    color: var(--text-400);
    margin: 0;
}

.edit-mask-lg {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #fff;
    opacity: 0;
    transition: 0.2s;
    font-size: 13px;
    gap: 4px;
    font-weight: 600;
}

.avatar-preview-lg:hover .edit-mask-lg {
    opacity: 1;
}

.avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

/* Right Column */
.dialog-right-col {
    flex: 1;
    padding: 32px 40px;
    display: flex;
    flex-direction: column;
}

/* Close Btn */
.dialog-header-actions {
    position: absolute;
    right: 16px;
    top: 16px;
    z-index: 10;
}


/* Form Styles */
.upgrade-form {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.form-row {
    display: flex;
    gap: 16px;
}

.form-col {
    flex: 1;
}

/* Custom Input Labels */
.input-label {
    display: block;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-600);
    margin-bottom: 6px;
}

:deep(.el-form-item) {
    margin-bottom: 12px;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
    /* background-color: var(--bg-fill-0) !important; -> Global */
    /* box-shadow: 0 0 0 1px var(--el-border-color) inset !important; -> Global */
    border-radius: 8px;
    transition: all 0.2s;
    padding-left: 12px;
}

:deep(.el-input__wrapper:hover),
:deep(.el-textarea__inner:hover) {
    box-shadow: 0 0 0 1px var(--text-400) inset !important;
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-textarea__inner:focus) {
    /* background-color: var(--bg-page) !important; -> Global handled */
    box-shadow: 0 0 0 2px var(--primary) inset !important;
}

.hidden-item {
    display: none;
}

/* Actions */
.dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid var(--border-glass);
}

.action-btn {
    height: 40px;
    padding: 0 24px;
    font-weight: 600;
    border-radius: 8px;
}

.cancel-btn {
    border: none;
    background: transparent;
    color: var(--text-600);
}

.cancel-btn:hover {
    background: var(--el-border-color-light);
    /* 统一交互：类似 Close Button 的 hover */
    color: var(--text-900);
}

.submit-btn {
    box-shadow: 0 4px 12px rgba(var(--primary-rgb), 0.3);
}

.left-col-inputs {
    width: 100%;
    margin-top: 32px;
    display: flex;
    flex-direction: column;
    text-align: left;
}

.upgrade-form-fields {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 20px;
}
</style>
