package com.windrr.hyundae.reminder

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.windrr.hyundae.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 길안내 중 목적지 도착 + P단을 감지해 실었던 짐을 챙기도록 알린다.
 * 길안내를 시작할 때 [start], 알림을 띄우면 스스로 종료.
 */
class ArrivalReminderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rule = ArrivalReminderRule()
    private var planId: String? = null
    private var vehicle: Vehicle? = null
    private var naviHelper: NaviHelper? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            scope.launch { if (rule.onGearChanged(isPark = gear == VehicleGear.GEAR_PARK)) remind() }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 변경 구독 실패", e)
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            scope.launch { if (rule.onDestinationArrived()) remind() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        planId = intent?.getStringExtra(EXTRA_PLAN_ID) ?: planId
        ServiceCompat.startForeground(
            this,
            ReminderNotifier.MONITORING_NOTIFICATION_ID,
            ReminderNotifier.monitoringNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (vehicle == null) connect()
        return START_NOT_STICKY
    }

    private fun connect() {
        vehicle = Vehicle(this).apply {
            initialize()
            getGear().registerGearSelection(gearListener)
        }
        naviHelper = NaviHelper(this).also { helper ->
            helper.initialize(
                { helper.addListener(naviListener) },
                { Log.w(TAG, "NaviHelper 초기화 실패", it) },
            )
        }
    }

    private suspend fun remind() {
        val items = planId?.let { AppDatabase.getInstance(this).planDao().getPlan(it) }?.items.orEmpty()
        if (items.isNotEmpty()) ReminderNotifier.notifyArrival(this, items)
        stopSelf()
    }

    override fun onDestroy() {
        vehicle?.let {
            runCatching { it.getGear().unregisterGearSelection(gearListener) }
            it.release()
        }
        naviHelper?.let {
            it.removeListener(naviListener)
            it.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArrivalReminder"
        private const val EXTRA_PLAN_ID = "plan_id"

        /** 길안내를 시작할 때 호출. 저장된 계획(plan_id)의 짐이 있을 때만 알림 */
        fun start(context: Context, planId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ArrivalReminderService::class.java).putExtra(EXTRA_PLAN_ID, planId),
            )
        }

        /** 길안내 취소 등으로 감시가 필요 없을 때 */
        fun stop(context: Context) {
            context.stopService(Intent(context, ArrivalReminderService::class.java))
        }
    }
}
