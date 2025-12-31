package hal.th50743.utils;

/**
 * 线程上下文持有者
 * <p>
 * 使用 ThreadLocal 存储当前线程的用户 ID，方便在不同层级间传递用户信息。
 */
public class CurrentHolder {

    // 创建一个ThreadLocal变量，用于存储Integer类型的值
    private static final ThreadLocal<Integer> CURRENT_LOCAL = new ThreadLocal<>();

    /**
     * 设置当前线程的用户ID
     * @param id 用户ID
     */
    public static void setCurrentId(Integer id) {
        CURRENT_LOCAL.set(id);
    }

    /**
     * 获取当前线程的用户ID
     * @return 用户ID
     */
    public static Integer getCurrentId() {
        return CURRENT_LOCAL.get();
    }

    /**
     * 移除当前线程的用户ID
     */
    public static void remove() {
        CURRENT_LOCAL.remove();
    }
}
