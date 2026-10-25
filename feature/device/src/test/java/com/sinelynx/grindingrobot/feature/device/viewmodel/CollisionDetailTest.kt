package com.sinelynx.grindingrobot.feature.device.viewmodel
import org.junit.Test
import org.junit.Assert.*
class CollisionDetailTest {
    @Test fun distinguishesSensorFaultsFromObstacles() {
        assertEquals("扫描数据过期", collisionDetailLabel("reason=scan_timeout"))
        assertEquals("扫描坐标变换失败", collisionDetailLabel("reason=tf_failed"))
        assertEquals("侧方障碍：0.05 m", collisionDetailLabel("reason=side_obstacle side_m=0.05"))
        assertEquals("正常", collisionDetailLabel("reason=clear"))
        assertEquals("扫描数据无效", collisionDetailLabel("reason=scan_invalid"))
    }
}
