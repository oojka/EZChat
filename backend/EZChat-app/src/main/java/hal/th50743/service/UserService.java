package hal.th50743.service;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.User;
import hal.th50743.pojo.UserReq;
import hal.th50743.pojo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 用户服务接口
 * <p>
 * 定义用户信息的查询、更新、注册及头像上传业务逻辑。
 */
public interface UserService {

    /**
     * 注册用户
     *
     * @param user 用户对象
     * @return 注册后的用户对象
     */
    User add(User user);

    /**
     * 根据 UId 获取用户 ID
     *
     * @param uId 用户唯一标识
     * @return 用户 ID
     */
    Integer getIdByUId(String uId);

    /**
     * 更新用户信息
     *
     * @param userReq 用户更新请求对象
     */
    void update(UserReq userReq);

    /**
     * 上传头像
     *
     * @param file 头像文件
     * @return 图片对象
     */
    Image uploadAvatar(MultipartFile file);

    /**
     * 获取用户信息
     *
     * @param uId 用户唯一标识
     * @return 用户视图对象
     */
    UserVO getUserInfoByUId(String uId);

    /**
     * 更新用户最后活跃时间
     *
     * @param userId          用户ID
     * @param currentChatCode 当前聊天室代码
     * @param now             当前时间
     */
    void updateLastSeenAt(Integer userId, String currentChatCode, LocalDateTime now);
}
