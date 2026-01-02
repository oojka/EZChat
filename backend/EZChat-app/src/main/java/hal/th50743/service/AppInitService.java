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

    /**
     * 获取应用初始化状态（轻量版：只返回 chatList 必要字段，不带 chatMembers）
     *
     * 业务目的：
     * - refresh 首屏优先渲染 AsideList，减少黑屏/转圈时长
     *
     * @return 初始化数据视图对象
     */
    AppInitVO getInitChatListState();

}
