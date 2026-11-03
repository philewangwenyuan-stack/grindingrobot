package com.sinelynx.grindingrobot.core.model.state

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class DrawingTaskPolicyTest {
    @After fun cleanup(){DrawingTaskPolicy.clear()}
    @Test fun bypassIsBoundToMapTaskAndVersion(){
        DrawingTaskPolicy.grant("task-A","map-A",4)
        assertTrue(DrawingTaskPolicy.allows("task-A","map-A",4))
        assertFalse(DrawingTaskPolicy.allows("task-B","map-A",4))
        assertFalse(DrawingTaskPolicy.allows("task-A","map-B",4))
        assertFalse(DrawingTaskPolicy.allows("task-A","map-A",5))
        DrawingTaskPolicy.revoke("task-A")
        assertFalse(DrawingTaskPolicy.allows("task-A","map-A",4))
    }
    @Test fun connectionResetClearsBothLayersAndBypasses(){
        DrawingTaskPolicy.update("map",DrawingLayerState("layer",1,2,"地图重建","基础.dxf","sha"))
        DrawingTaskPolicy.grant("task","map",1);DrawingTaskPolicy.clear()
        assertNull(DrawingTaskPolicy.layer("map"));assertFalse(DrawingTaskPolicy.allows("task","map",1))
    }
}
