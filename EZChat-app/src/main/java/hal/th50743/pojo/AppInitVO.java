package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 应用初始化视图对象
 * <p>
 * 包含用户加入的聊天室列表及相关用户的在线状态。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppInitVO {

    /**
     * 聊天室列表
     */
    List<ChatVO> chatList;

    /**
     * 用户状态列表（在线/离线）
     */
    List<UserStatus> userStatusList;

}
