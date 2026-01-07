<script setup lang="ts">
import { onMounted, computed, ref, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import ChatItem from './ChatItem.vue'
import { Star, Trophy, Check, Plus, Camera, Close } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules, type UploadProps } from 'element-plus'
import { upgradeUserApi } from '@/api/User'
import { uploadAvatarApi } from '@/api/Auth'
import type { RegisterInfo, Image } from '@/type'

const { t } = useI18n()
const roomStore = useRoomStore()
const userStore = useUserStore()
const { roomList, currentRoomCode } = storeToRefs(roomStore)

// 权益文案配置
const benefits = [
    '创建专属聊天频道',
    '聊天室加入无限制',
    '消息云端永久保存',
    '多端实时同步',
    '头像昵称自定义',
]

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

// 校验规则
const validatePass2 = (rule: any, value: any, callback: any) => {
    if (value === '') {
        callback(new Error('请再次输入密码'))
    } else if (value !== form.password) {
        callback(new Error('两次输入密码不一致!'))
    } else {
        callback()
    }
}

const rules = reactive<FormRules>({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
    ],
    confirmPassword: [
        { validator: validatePass2, trigger: 'blur' }
    ],
    nickname: [
        { required: true, message: '请输入昵称', trigger: 'blur' }
    ]
})

const handleUpgrade = () => {
    // 回显当前访客信息
    if (userStore.loginUserInfo) {
        form.nickname = userStore.loginUserInfo.nickname
        if (userStore.loginUserInfo.avatar) {
            form.avatar = { ...userStore.loginUserInfo.avatar }
        }
    }
    upgradeDialogVisible.value = true
}

const submitUpgrade = async (formEl: FormInstance | undefined) => {
    if (!formEl) return
    await formEl.validate(async (valid, fields) => {
        if (valid) {
            loading.value = true
            try {
                const res = await upgradeUserApi(form)
                if (res.code === 0) { // SUCCESS
                    ElMessage.success('升级成功，欢迎成为正式用户！')
                    // 更新本地登录态
                    userStore.setLoginUser(res.data)
                    upgradeDialogVisible.value = false
                } else {
                    ElMessage.error(res.message || '升级失败')
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
const handleAvatarSuccess: UploadProps['onSuccess'] = (response, uploadFile) => {
    if (response.code === 0) {
        form.avatar = response.data
        ElMessage.success('头像上传成功')
    } else {
        ElMessage.error('头像上传失败')
    }
}

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
    if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
        ElMessage.error('Avatar picture must be JPG or PNG format!')
        return false
    } else if (rawFile.size / 1024 / 1024 > 2) {
        ElMessage.error('Avatar picture size can not exceed 2MB!')
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
                    <div class="upgrade-title">解锁完整功能体验</div>
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
                    成为正式用户
                </el-button>
            </div>
        </div>

        <!-- 升级弹窗 (Modern Style) -->
        <el-dialog v-model="upgradeDialogVisible" width="420px" class="ez-modern-dialog upgrade-dialog-modern"
            align-center destroy-on-close :show-close="false" :close-on-click-modal="false">

            <template #header>
                <div class="dialog-header-actions">
                    <button class="close-btn" type="button" @click="upgradeDialogVisible = false">
                        <el-icon>
                            <Close />
                        </el-icon>
                    </button>
                </div>
                <div class="dialog-title-area">
                    <h3>升级为正式账号</h3>
                </div>
            </template>

            <div class="upgrade-dialog-content">
                <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="top"
                    class="upgrade-form" hide-required-asterisk>
                    <!-- 头像上传 -->
                    <div class="avatar-upload-box">
                        <el-upload class="avatar-uploader-large" action="#" :http-request="customUploadRequest"
                            :show-file-list="false" :on-success="handleAvatarSuccess"
                            :before-upload="beforeAvatarUpload">
                            <div v-if="form.avatar && form.avatar.imageUrl" class="avatar-preview-lg">
                                <img :src="form.avatar.imageUrl" class="avatar-img" />
                                <div class="edit-mask-lg">
                                    <el-icon>
                                        <Camera />
                                    </el-icon>
                                    <span>更改</span>
                                </div>
                            </div>
                            <div v-else class="placeholder-square-lg">
                                <el-icon size="40">
                                    <Plus />
                                </el-icon>
                                <span>上传头像</span>
                            </div>
                        </el-upload>
                        <el-form-item prop="avatar" class="hidden-item" />
                    </div>

                    <el-form-item label="账号" prop="username">
                        <el-input v-model="form.username" placeholder="设置登录账号" size="large" />
                    </el-form-item>
                    <el-form-item label="密码" prop="password">
                        <el-input v-model="form.password" type="password" placeholder="设置登录密码" show-password
                            size="large" />
                    </el-form-item>
                    <el-form-item label="确认密码" prop="confirmPassword">
                        <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password
                            size="large" />
                    </el-form-item>
                    <el-form-item label="昵称" prop="nickname">
                        <el-input v-model="form.nickname" placeholder="设置昵称" size="large" />
                    </el-form-item>
                    <el-form-item label="个人简介" prop="bio">
                        <el-input v-model="form.bio" type="textarea" :rows="2" placeholder="一句话介绍自己..." resize="none"
                            size="large" />
                    </el-form-item>
                </el-form>

                <div class="dialog-actions">
                    <el-button @click="upgradeDialogVisible = false" class="action-btn-half" size="large">取消</el-button>
                    <el-button type="primary" :loading="loading" @click="submitUpgrade(formRef)" class="action-btn-half"
                        size="large">
                        立即升级
                    </el-button>
                </div>
            </div>
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
}

.upgrade-card:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
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
    border-radius: var(--radius-xl) !important;
    box-shadow: var(--shadow-glass) !important;
    overflow: hidden;
    transition: all 0.3s var(--ease-out-expo);
}

html.dark :deep(.ez-modern-dialog) {
    background: var(--bg-card) !important;
    backdrop-filter: blur(24px) saturate(200%) !important;
    -webkit-backdrop-filter: blur(24px) saturate(200%) !important;
}

.upgrade-dialog-modern :deep(.el-dialog__header) {
    padding: 0 !important;
    margin: 0 !important;
}

.upgrade-dialog-modern :deep(.el-dialog__body) {
    padding: 0 !important;
}

/* Custom Header */
.dialog-header-actions {
    position: relative;
    height: 0;
}

.close-btn {
    position: absolute;
    right: 16px;
    top: 24px;
    z-index: 10;
    background: var(--bg-page);
    border: none;
    color: var(--text-500);
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s;
}

.close-btn:hover {
    background: var(--el-border-color-light);
    color: var(--text-900);
    transform: rotate(90deg);
}

.dialog-title-area {
    text-align: center;
    margin-top: 24px;
    padding: 0 40px;
    margin-bottom: 8px;
    /* Reduce bottom margin to bring Avatar closer */
}

.dialog-title-area h3 {
    font-size: 20px;
    font-weight: 800;
    color: var(--text-900);
    margin: 0;
}

/* Content Area */
.upgrade-dialog-content {
    padding: 10px 32px 32px;
    display: flex;
    flex-direction: column;
}

.upgrade-form {
    display: flex;
    flex-direction: column;
    margin-bottom: 24px;
}

:deep(.el-form-item) {
    margin-bottom: 16px;
}

:deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-700);
    padding-bottom: 6px !important;
    line-height: 1 !important;
}

/* --- Avatar Upload (Match CreateChatDialog) --- */
.avatar-upload-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 16px;
}

.avatar-uploader-large {
    display: flex;
    justify-content: center;
}

.avatar-preview-lg,
.placeholder-square-lg {
    width: 100px;
    height: 100px;
    border-radius: calc(100px * var(--avatar-border-radius-ratio));
    /* 30px */
    overflow: hidden;
    position: relative;
    cursor: pointer;
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
    /* Smaller shadow than create dialog */
    transition: all 0.3s;
    background: var(--bg-page);
}

.placeholder-square-lg {
    border: 2px dashed var(--el-border-color-light);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--text-400);
    gap: 4px;
}

.placeholder-square-lg span {
    font-size: 11px;
    font-weight: 700;
}

.placeholder-square-lg:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.edit-mask-lg {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #fff;
    opacity: 0;
    transition: 0.3s;
    font-size: 12px;
    gap: 2px;
}

.avatar-preview-lg:hover .edit-mask-lg {
    opacity: 1;
}

.hidden-item {
    margin: 0 !important;
    height: 0;
    overflow: hidden;
}

/* --- Actions --- */
.dialog-actions {
    display: flex;
    gap: 12px;
}

.action-btn-half {
    flex: 1;
    height: 48px;
    font-size: 15px;
    font-weight: 800;
    border-radius: 14px;
}

/* Input Overrides for Dialog Context */
:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
    background-color: var(--bg-page) !important;
    box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
    border-radius: var(--radius-base);
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-textarea__inner:focus) {
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}
</style>
