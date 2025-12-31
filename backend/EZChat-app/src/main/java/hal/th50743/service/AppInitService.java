package hal.th50743.service;

import hal.th50743.pojo.AppInitVO;

/**
 * 应用初始化服务接口
 */
public interface AppInitService {

    /**
     * 获取应用初始化状态
     *
     * @return 初始化数据视图对象
     */
    AppInitVO getInitState();

}
