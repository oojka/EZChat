package hal.th50743.utils;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.User;
import hal.th50743.pojo.UserVO;
import io.minio.MinioOSSOperator;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户视图对象转换工具类
 * <p>
 * 提供 User Entity 到 UserVO 的转换逻辑，确保转换逻辑的统一性和可维护性。
 * <p>
 * 职责：
 * <ul>
 *     <li>用户实体到视图对象的转换</li>
 *     <li>头像 URL 的构建（完整 URL、缩略图 URL）</li>
 *     <li>数据脱敏和安全处理</li>
 * </ul>
 */
@Slf4j
public final class UserVOConverter {

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private UserVOConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 将 User Entity 转换为 UserVO
     * <p>
     * 自动处理头像对象的构建，包括完整 URL 和缩略图 URL 的生成。
     *
     * @param user            用户实体对象
     * @param minioOSSOperator MinIO 操作器，用于生成头像 URL
     * @return UserVO 用户视图对象
     * @throws IllegalArgumentException 如果 user 为 null
     */
    public static UserVO toUserVO(User user, MinioOSSOperator minioOSSOperator) {
        if (user == null) {
            throw new IllegalArgumentException("User entity cannot be null");
        }

        // 构建头像对象（如果存在）
        Image avatar = null;
        if (user.getAvatarObject() != null) {
            avatar = new Image(
                    user.getAvatarObject(),
                    minioOSSOperator.toUrl(user.getAvatarObject()),
                    minioOSSOperator.toThumbUrl(user.getAvatarObject())
            );
        }

        // 构建并返回 UserVO（只包含前端需要的字段，不包含敏感信息）
        return new UserVO(
                user.getUId(),
                user.getNickname(),
                avatar,
                user.getBio()
        );
    }
}
