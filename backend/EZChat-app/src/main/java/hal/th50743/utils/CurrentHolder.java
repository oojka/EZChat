package hal.th50743.utils;

/**
 * 线程上下文持有者
 *
 * <p>使用 ThreadLocal 存储当前请求线程的用户 ID，便于在各层之间传递用户信息。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>拦截器验证 Token 后设置用户 ID</li>
 *   <li>Service 层获取当前登录用户 ID</li>
 *   <li>AOP 审计日志记录操作者</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@code TokenInterceptor.preHandle()} - 验证 Token 后调用 {@link #setCurrentId(Integer)}</li>
 *   <li>业务逻辑 - 通过 {@link #getCurrentId()} 获取用户 ID</li>
 *   <li>{@code TokenInterceptor.afterCompletion()} - 请求完成后调用 {@link #remove()} 清理</li>
 * </ol>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>必须在请求结束时调用 {@link #remove()}，防止内存泄漏</li>
 *   <li>异步任务中无法使用（ThreadLocal 不会自动传递到子线程）</li>
 * </ul>
 *
 * @see hal.th50743.interceptor.TokenInterceptor Token 拦截器
 */
public class CurrentHolder {

    /**
     * 存储当前线程用户 ID 的 ThreadLocal 变量
     */
    private static final ThreadLocal<Integer> CURRENT_LOCAL = new ThreadLocal<>();

    /**
     * 设置当前线程的用户 ID
     *
     * @param id 用户 ID（数据库主键）
     */
    public static void setCurrentId(Integer id) {
        CURRENT_LOCAL.set(id);
    }

    /**
     * 获取当前线程的用户 ID
     *
     * @return 用户 ID，未设置时返回 null
     */
    public static Integer getCurrentId() {
        return CURRENT_LOCAL.get();
    }

    /**
     * 清除当前线程的用户 ID
     *
     * <p>必须在请求处理完成后调用，防止线程池复用时数据污染和内存泄漏。
     */
    public static void remove() {
        CURRENT_LOCAL.remove();
    }
}
