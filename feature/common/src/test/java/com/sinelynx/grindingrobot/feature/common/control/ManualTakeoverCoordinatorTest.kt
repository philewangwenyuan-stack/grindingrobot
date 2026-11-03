package com.sinelynx.grindingrobot.feature.common.control

import com.sinelynx.grindingrobot.core.model.state.ManualTaskGate
import com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload
import com.sinelynx.grindingrobot.core.model.state.manualTaskGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ManualTakeoverCoordinatorTest {
    private fun report(state: Int, id: String = "") = TaskStatusReportPayload(
        taskId=id, stateValue=state, progress=0f, mapVersion=0, message="",
        positionX=null, positionY=null, headingDeg=null, replanRequested=false,
        pathPointCount=0, pathVersion=0, remainWorkArea=0f, totalWorkArea=0f,
        remainTimeS=0f, currentRegionId="", currentRegionRepeatIndex=0, currentRegionRepeatTotal=0)

    @Test fun inactiveStatesAndReadyAllowManualWithoutStoppingTaskOrDisc() = runBlocking {
        for (state in listOf(0,1,5,6,7)) {
            val gate=report(state).manualTaskGate()
            var discOn=true
            val stoppedTasks=mutableListOf<String>()
            val sent=mutableListOf<ManualDriveTarget>()
            lateinit var controller: ManualDriveController
            val takeover=ManualTakeoverCoordinator({gate}, {id ->
                stoppedTasks.add(id); discOn=false; true }, {controller.stop()}, {})
            controller=ManualDriveController(this, {true}, {sent.add(it);true}, {},
                prepare={takeover.canPrepareManual()})
            takeover.observeTask();controller.refreshAvailability()
            controller.travel(TravelDirection.FORWARD);yield()
            assertTrue(controller.state.hasMotion)
            controller.steer(.5f,controller.state.inputEpoch)
            controller.stop()
            assertTrue("ordinary input must not stop explicit disc",discOn)
            assertTrue(stoppedTasks.isEmpty())
            assertEquals(0f,sent.last().speedRatio,0f)
        }
    }

    @Test fun onlyPlanningRunningPausedRequireTakeover() {
        for (state in listOf(2,3,4)) {
            val gate=report(state," job ").manualTaskGate()
            assertTrue(gate.requiresTakeover)
            assertEquals("job",gate.taskId)
            assertFalse(gate.canDriveWithoutTakeover)
        }
        assertFalse((null as TaskStatusReportPayload?).manualTaskGate().known)
        assertFalse(report(99).manualTaskGate().known)
    }

    @Test fun activeTaskWithEmptyIdCannotSendAmbiguousStop() = runBlocking {
        var calls=0
        val takeover=ManualTakeoverCoordinator({report(3).manualTaskGate()}, {calls++;true},{},{})
        takeover.observeTask()
        assertFalse(takeover.request())
        assertFalse(takeover.canPrepareManual())
        assertNull(takeover.confirm())
        assertEquals(0,calls)
    }

    @Test fun ownDialogBlurStopsMotionButKeepsConfirmationAndNeverReplaysGesture() = runBlocking {
        val gate=report(3,"job").manualTaskGate()
        val sent=mutableListOf<ManualDriveTarget>()
        var stopCalls=0
        lateinit var controller: ManualDriveController
        val takeover=ManualTakeoverCoordinator({gate},{stopCalls++;true},{controller.stop()},{})
        controller=ManualDriveController(this,{true},{sent.add(it);true},{},
            prepare={takeover.canPrepareManual()})
        takeover.observeTask();controller.refreshAvailability()
        assertTrue(takeover.request())
        val oldEpoch=controller.state.inputEpoch
        takeover.windowBlurred()
        assertTrue(takeover.state.visible)
        assertTrue(controller.state.inputEpoch>oldEpoch)
        assertEquals(true,takeover.confirm())
        assertFalse(takeover.state.visible)
        assertEquals(1,stopCalls)
        assertTrue(sent.none { it.speedRatio>0 })
        controller.steer(1f,oldEpoch)
        assertTrue(sent.none { it.speedRatio>0 })
        controller.travel(TravelDirection.FORWARD);yield()
        assertTrue(sent.any { it.speedRatio>0 })
        controller.stop()
    }

    @Test fun cancellationWhileStopAckPendingCannotApproveOrMove() = runBlocking {
        val ack=CompletableDeferred<Boolean>()
        val takeover=ManualTakeoverCoordinator({report(3,"job").manualTaskGate()}, {ack.await()},{},{})
        takeover.observeTask();takeover.request()
        var result:Boolean?=true
        val job=launch {result=takeover.confirm()};yield()
        assertTrue(takeover.state.pending)
        takeover.cancel() // page exit, disconnect or emergency
        ack.complete(true);job.join()
        assertNull(result)
        assertFalse(takeover.canPrepareManual())
        assertFalse(takeover.state.visible)
    }

    @Test fun changedTaskAndFailedStopCannotGrantOldTakeover() = runBlocking {
        var gate=report(3,"one").manualTaskGate()
        val ack=CompletableDeferred<Boolean>()
        val takeover=ManualTakeoverCoordinator({gate},{ack.await()},{},{})
        takeover.observeTask();takeover.request()
        val job=launch {takeover.confirm()};yield()
        gate=report(3,"two").manualTaskGate();takeover.observeTask()
        ack.complete(true);job.join()
        assertFalse(takeover.canPrepareManual())
        val failure=ManualTakeoverCoordinator({gate},{false},{},{})
        failure.observeTask();failure.request()
        assertEquals(false,failure.confirm())
        assertFalse(failure.canPrepareManual())
    }

    @Test fun sameTaskRestartAfterIdleRequiresNewConfirmation() = runBlocking {
        var gate=ManualTaskGate(true,true,"job")
        val takeover=ManualTakeoverCoordinator({gate},{true},{},{})
        takeover.observeTask();takeover.request();takeover.confirm()
        assertTrue(takeover.canPrepareManual())
        gate=ManualTaskGate(true,false);takeover.observeTask()
        gate=ManualTaskGate(true,true,"job");takeover.observeTask()
        assertFalse(takeover.canPrepareManual())
        assertTrue(takeover.needsConfirmation())
    }
}
