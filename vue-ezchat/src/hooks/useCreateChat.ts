import {reactive, ref} from 'vue'
import type {Image} from '@/type'
import {ElMessage, type FormInstance, type FormRules, type UploadProps} from 'element-plus'

export const useCreateChat = () => {
  const createChatForm = ref<{
    avatar: Image
    chatName: string
    joinEnable: 0 | 1
    joinLinkExpiry: string
    password: string
    passwordConfirm: string
  }>({
    avatar: {
      objectName: '',
      objectUrl: '',
      objectThumbUrl: '',
    },
    chatName: '',
    joinEnable: 0,
    joinLinkExpiry: '',
    password: '',
    passwordConfirm: '',
  })

  const createFormRef = ref<FormInstance>()

  // 1. 头像上传逻辑
  const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
    const isImage = rawFile.type === 'image/jpeg' || rawFile.type === 'image/png'
    const isLt2M = rawFile.size / 1024 / 1024 < 2

    if (!isImage) {
      ElMessage.error('画像はJPG/PNG形式のみ可能です')
      return false
    }
    if (!isLt2M) {
      ElMessage.error('画像サイズは2MB以下にしてください')
      return false
    }
    return true
  }

  const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    if (response && response.data) {
      createChatForm.value.avatar = response.data
    }
  }

  // 2. 自定义密码校验规则
  const validatePassword = (rule: any, value: string, callback: any) => {
    if (createChatForm.value.joinEnable === 1) {
      if (!value) {
        callback(new Error('パスワードを入力してください'))
      } else {
        if (createChatForm.value.passwordConfirm !== '') {
          createFormRef.value?.validateField('passwordConfirm', () => {})
        }
        callback()
      }
    } else {
      callback()
    }
  }

  const validatePasswordConfirm = (rule: any, value: string, callback: any) => {
    if (createChatForm.value.joinEnable === 1) {
      if (!value) {
        callback(new Error('確認用パスワードを入力してください'))
      } else if (value !== createChatForm.value.password) {
        callback(new Error('パスワードが一致しません'))
      } else {
        callback()
      }
    } else {
      callback()
    }
  }

  // 3. 校验规则定义
  const createFormRules = reactive<FormRules>({
    chatName: [
      { required: true, message: 'ルーム名を入力してください', trigger: 'blur' },
      { min: 1, max: 20, message: '20文字以内で入力してください', trigger: 'blur' },
    ],
    password: [{ validator: validatePassword, trigger: 'blur' }],
    passwordConfirm: [{ validator: validatePasswordConfirm, trigger: 'blur' }],
  })

  // 4. 提交包装函数
  const handleCreate = async () => {
    if (!createFormRef.value) return
    await createFormRef.value.validate((valid) => {
      if (valid) {
        console.log('[INFO] [CreateChatHook] Create logic:', createChatForm.value)
      }
    })
  }

  return {
    createChatForm,
    handleCreate,
    createFormRef,
    createFormRules,
    beforeAvatarUpload,
    handleAvatarSuccess,
  }
}
