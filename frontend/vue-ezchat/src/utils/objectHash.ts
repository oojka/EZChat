/**
 * 计算对象的 SHA-256 哈希值（hex 字符串）
 * <p>
 * 使用 Web Crypto API 异步计算，避免阻塞主线程。
 * 与后端 ObjectHashUtils.calculateSHA256() 算法一致。
 *
 * @param file 文件对象
 * @returns SHA-256 hex 字符串（64 字符，小写）的 Promise
 * @throws Error 如果对象读取或哈希计算失败
 */
export async function calculateObjectHash(file: File): Promise<string> {
    try {
        // 1. 读取文件为 ArrayBuffer
        const buffer = await file.arrayBuffer()
        
        // 2. 使用 Web Crypto API 计算 SHA-256 哈希
        const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
        
        // 3. 转换为 Uint8Array
        const hashArray = Array.from(new Uint8Array(hashBuffer))
        
        // 4. 转换为十六进制字符串（小写，与后端一致）
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
    } catch (error) {
        console.error('[ERROR] [calculateObjectHash] Failed to calculate object hash:', error)
        throw new Error('Failed to calculate object hash')
    }
}

