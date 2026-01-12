<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import ProfileTab from '@/components/dialogs/user-settings/ProfileTab.vue'
import SecurityTab from '@/components/dialogs/user-settings/SecurityTab.vue'
import { ArrowRight, User as UserIcon, Lock, InfoFilled } from '@element-plus/icons-vue'
import PasswordInput from '@/components/PasswordInput.vue'
import Avatar from '@/components/Avatar.vue'
import { ElMessage, type FormInstance, type FormRules, type UploadProps } from 'element-plus'
import { upgradeUserApi } from '@/api/User'
import { uploadAvatarApi } from '@/api/Auth'
import type { RegisterInfo, Image } from '@/type'
import { isValidUsername, isValidNickname, isValidPassword } from '@/utils/validators'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isGuest = computed(() => userStore.loginUserInfo?.userType === 'guest')

type SettingsSection = 'main' | 'profile' | 'security' | 'upgrade'
const currentSection = ref<SettingsSection>('main')

const emptyAvatar: Image = {
  imageUrl: '',
  imageThumbUrl: ''
}

onMounted(() => {
  if (route.query.upgrade === 'true') {
    initUpgradeForm()
    currentSection.value = 'upgrade'
    router.replace({ path: '/chat/settings', query: {} })
  }
})

const goToSection = (section: SettingsSection) => {
  if (section === 'upgrade') {
    initUpgradeForm()
  }
  currentSection.value = section
}

const goBack = () => {
  currentSection.value = 'main'
}

const handleLogout = async () => {
  await userStore.logout({ showDialog: true })
  router.push('/')
}

const formRef = ref<FormInstance>()
const form = reactive<RegisterInfo>({
  nickname: '',
  bio: '',
  username: '',
  password: '',
  confirmPassword: '',
  avatar: { ...emptyAvatar }
})
const isSubmitting = ref(false)

const initUpgradeForm = () => {
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.nickname = userStore.loginUserInfo?.nickname || ''
  form.bio = userStore.loginUserInfo?.bio || ''
  form.avatar = userStore.loginUserInfo?.avatar ? { ...userStore.loginUserInfo.avatar } : { ...emptyAvatar }
}

const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error(t('validation.confirm_password_required')))
  } else if (value !== form.password) {
    callback(new Error(t('validation.password_mismatch')))
  } else {
    callback()
  }
}

const formRules = reactive<FormRules>({
  nickname: [
    { required: true, message: t('validation.nickname_required'), trigger: 'blur' },
    { validator: (_rule, value, callback) => {
        if (!isValidNickname(value as string)) callback(new Error(t('validation.nickname_format')))
        else callback()
      }, trigger: 'blur' }
  ],
  username: [
    { required: true, message: t('validation.username_required'), trigger: 'blur' },
    { validator: (_rule, value, callback) => {
        if (!isValidUsername(value as string)) callback(new Error(t('validation.username_format')))
        else callback()
      }, trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('validation.password_required'), trigger: 'blur' },
    { validator: (_rule, value, callback) => {
        if (!isValidPassword(value as string)) callback(new Error(t('validation.password_format')))
        else callback()
      }, trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: validatePass2, trigger: 'blur' }
  ]
})

const customUploadRequest = async (options: { file: File; onSuccess: (res: unknown) => void; onError: (err: unknown) => void }) => {
  const { file, onSuccess, onError } = options
  try {
    const res = await uploadAvatarApi(file)
    onSuccess(res)
  } catch (err) {
    onError(err)
  }
}

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

const handleUpgradeSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  isSubmitting.value = true
  try {
    const res = await upgradeUserApi(form)
    if (res.status === 1 && res.data) {
      ElMessage.success(t('upgrade.success'))
      userStore.setLoginUser(res.data)
      goBack()
    } else {
      ElMessage.error(res.message || t('upgrade.failed'))
    }
  } catch {
    ElMessage.error(t('upgrade.failed'))
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="mobile-settings-view">
    <Transition name="slide-fade" mode="out-in">
      <div v-if="currentSection === 'main'" key="main" class="settings-main">
        <div class="mobile-page-header">
          <h1 class="page-title">{{ t('mobile.tab_settings') }}</h1>
        </div>

        <div class="settings-list">
          <div v-if="isGuest" class="upgrade-banner" @click="goToSection('upgrade')">
            <el-icon class="banner-icon"><InfoFilled /></el-icon>
            <div class="banner-text">
              <span class="banner-title">{{ t('mobile.upgrade_banner_title') }}</span>
              <span class="banner-desc">{{ t('mobile.upgrade_banner_desc') }}</span>
            </div>
            <el-icon class="banner-arrow"><ArrowRight /></el-icon>
          </div>

          <div class="settings-section">
            <div class="settings-item" @click="goToSection('profile')">
              <el-icon class="item-icon"><UserIcon /></el-icon>
              <span class="item-label">{{ t('user_settings.profile_tab') }}</span>
              <el-icon class="item-arrow"><ArrowRight /></el-icon>
            </div>

            <div v-if="!isGuest" class="settings-item" @click="goToSection('security')">
              <el-icon class="item-icon"><Lock /></el-icon>
              <span class="item-label">{{ t('user_settings.security_tab') }}</span>
              <el-icon class="item-arrow"><ArrowRight /></el-icon>
            </div>
          </div>

          <div class="settings-section">
            <div class="settings-item logout-item" @click="handleLogout">
              <span class="item-label">{{ t('auth.logout') }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="currentSection === 'profile'" key="profile" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('user_settings.profile_tab') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content">
          <ProfileTab @close="goBack" />
        </div>
      </div>

      <div v-else-if="currentSection === 'security'" key="security" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('user_settings.security_tab') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content">
          <SecurityTab @close="goBack" />
        </div>
      </div>

      <div v-else-if="currentSection === 'upgrade'" key="upgrade" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('upgrade.dialog_title') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content upgrade-content">
          <div class="upgrade-avatar-section">
            <el-upload
              class="avatar-uploader"
              :show-file-list="false"
              :http-request="customUploadRequest"
              :on-success="handleAvatarSuccess"
              :before-upload="beforeAvatarUpload"
            >
              <Avatar
                v-if="form.avatar.imageUrl"
                :image="form.avatar"
                :size="80"
                class="upgrade-avatar"
              />
              <div v-else class="avatar-placeholder">
                <el-icon :size="32"><UserIcon /></el-icon>
              </div>
            </el-upload>
            <span class="avatar-tip">{{ t('upgrade.avatar_tip') }}</span>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="formRules"
            label-position="top"
            class="upgrade-form"
          >
            <el-form-item prop="nickname">
              <el-input
                v-model="form.nickname"
                :placeholder="t('upgrade.nickname_placeholder')"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="bio">
              <el-input
                v-model="form.bio"
                :placeholder="t('upgrade.bio_placeholder')"
                type="textarea"
                :rows="2"
              />
            </el-form-item>

            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                :placeholder="t('upgrade.username_placeholder')"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="password">
              <PasswordInput
                v-model="form.password"
                :placeholder="t('upgrade.password_placeholder')"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <PasswordInput
                v-model="form.confirmPassword"
                :placeholder="t('upgrade.confirm_password_placeholder')"
                size="large"
              />
            </el-form-item>

            <el-button
              type="primary"
              size="large"
              :loading="isSubmitting"
              class="upgrade-submit-btn"
              @click="handleUpgradeSubmit"
            >
              {{ t('upgrade.submit_button') }}
            </el-button>
          </el-form>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.mobile-settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  /* padding-bottom removed, layout handled by flex */
  /* padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom)); */
  box-sizing: border-box;
}

.settings-main {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.mobile-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.settings-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.upgrade-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  border-radius: var(--radius-base);
  margin-bottom: 16px;
  cursor: pointer;
  color: #fff;
}

.banner-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.banner-title {
  font-size: 14px;
  font-weight: 600;
}

.banner-desc {
  font-size: 12px;
  opacity: 0.9;
}

.banner-arrow {
  font-size: 16px;
  opacity: 0.8;
}

.settings-section {
  background: var(--bg-card);
  border-radius: var(--radius-base);
  overflow: hidden;
  margin-bottom: 16px;
}

.settings-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-light);
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item:active {
  background: var(--bg-page);
}

.item-icon {
  font-size: 20px;
  color: var(--text-500);
}

.item-label {
  flex: 1;
  font-size: 15px;
  color: var(--text-900);
}

.item-arrow {
  font-size: 14px;
  color: var(--text-400);
}

.logout-item {
  justify-content: center;
}

.logout-item .item-label {
  flex: none;
  color: #f56c6c;
  font-weight: 500;
}

.settings-subpage {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}

.subpage-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.subpage-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.header-spacer {
  width: 60px;
}

.subpage-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.upgrade-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.upgrade-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.avatar-uploader {
  cursor: pointer;
}

.upgrade-avatar {
  border-radius: 50%;
}

.avatar-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  border: 2px dashed var(--el-border-color-light);
}

.avatar-tip {
  font-size: 12px;
  color: var(--text-500);
}

.upgrade-form {
  width: 100%;
  max-width: 400px;
}

.upgrade-submit-btn {
  width: 100%;
  margin-top: 16px;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.2s ease;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
