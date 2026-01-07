package hal.th50743.assembler;

import hal.th50743.pojo.Asset;
import hal.th50743.pojo.Image;
import hal.th50743.pojo.User;
import hal.th50743.pojo.UserVO;
import hal.th50743.service.AssetService;
import io.minio.MinioOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户视图对象组装器
 * <p>
 * 提供 User Entity 到 UserVO 的转换逻辑，封装头像解析等复杂操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAssembler {

    private final MinioOSSOperator minioOSSOperator;
    private final AssetService assetService;

    /**
     * 将 User Entity 转换为 UserVO
     *
     * @param user 用户实体对象
     * @return UserVO 用户视图对象
     */
    public UserVO toUserVO(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User entity cannot be null");
        }

        Image avatar = null;
        String avatarObjectName = user.getAvatarObjectName();

        // 1. 尝试使用 JOIN 查询的头像名
        if (avatarObjectName != null) {
            avatar = buildImage(avatarObjectName, user.getAssetId());
        }
        // 2. 如果只有 objectId 但没有 objectName (很少见，防御性逻辑)
        else if (user.getAssetId() != null) {
            Asset file = assetService.findById(user.getAssetId());
            if (file != null) {
                avatar = buildImage(file.getAssetName(), user.getAssetId());
            } else {
                log.warn("User avatar objectId={} exists but not found in DB", user.getAssetId());
            }
        }

        return new UserVO(
                user.getUid(),
                user.getNickname(),
                avatar,
                user.getBio());
    }

    /**
     * 解析并验证头像对象ID
     * <p>
     * 用于更新用户信息时，根据传入的 objectName 查找对应的 objectId。
     *
     * @param avatarObjectName 头像对象名称
     * @return objectId 如果找到，否则返回 null
     */
    public Integer resolveAvatarId(String avatarObjectName) {
        if (avatarObjectName == null || avatarObjectName.isBlank()) {
            return null;
        }
        Asset objectEntity = assetService.findByObjectName(avatarObjectName);
        if (objectEntity != null) {
            // 激活文件
            assetService.activateAvatarFile(avatarObjectName);
            return objectEntity.getId();
        } else {
            log.warn("Avatar object not found: {}", avatarObjectName);
            return null;
        }
    }

    private Image buildImage(String objectName, Integer objectId) {
        return new Image(
                objectName,
                minioOSSOperator.toUrl(objectName),
                minioOSSOperator.toThumbUrl(objectName),
                objectId);
    }
}
