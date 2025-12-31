package hal.th50743.service.impl;

import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.LoginReq;
import hal.th50743.pojo.User;
import hal.th50743.service.FormalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 正式用户服务实现类
 * <p>
 * 负责正式用户的注册、登录及转正逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormalUserServiceImpl implements FormalUserService {

    private final UserMapper userMapper;

    /**
     * 添加正式用户
     *
     * @param formalUserReq 正式用户请求对象
     */
    @Override
    public void add(FormalUser formalUserReq) {
        userMapper.addFormalUser(formalUserReq);
    }

    /**
     * 根据 UId 添加正式用户（转正）
     *
     * @param formalUserReq 正式用户请求对象（包含 userUId）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addByUId(FormalUser formalUserReq) {
        log.info("add user by UId, user={}", formalUserReq);
        User user = userMapper.selectByUId(formalUserReq.getUserUId());
        formalUserReq.setUserId(user.getId());
        add(formalUserReq);
    }

    /**
     * 用户登录
     *
     * @param loginReq 登录请求对象
     * @return User 用户对象
     */
    @Override
    public User login(LoginReq loginReq) {
        return userMapper.selectByUsernameAndPassword(loginReq.getUsername(), loginReq.getPassword());
    }
}
