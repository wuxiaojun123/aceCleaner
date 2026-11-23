package com.nice.library_float

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.nice.library_float.window.callback.FloatWindowCallback
import com.nice.library_float.window.core.IFloatWindow
import com.nice.library_float.window.linker.FloatWindowLinker
import com.nice.library_float.window.manager.FloatWindowManager
import com.nice.library_float.window.model.FloatGravity
import com.nice.library_float.window.model.FloatMode
import com.nice.library_float.window.model.FloatWindowConfig
import com.nice.library_float.window.model.FloatWindowState
import com.nice.library_float.window.util.FloatUtil
import com.nice.library_float.R
import com.nice.library_float.databinding.ViewFloatBottomBinding
import com.nice.library_float.databinding.ViewFloatNetSpeedBinding
import com.nice.library_float.databinding.ViewFloatSliderBinding
import com.nice.library_float.databinding.ViewFloatTipBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale



object FloatManager {

    private var sliderFloat : IFloatWindow?= null
    private var bottomFloat : IFloatWindow?= null
    private var netTestFloat : IFloatWindow?= null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val netTestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var netTestJob: Job? = null
    private var bottomNetworkSpeedUpdater: Runnable? = null
    private const val SLIDER_FLOAT_TAG = "slider"
    private const val TIP_FLOAT_TAG = "tip"
    private const val BOTTOM_FLOAT_TAG = "bottom"
    private const val NET_TEST_FLOAT_TAG = "netTest"
    private const val FLOAT_TAG = "FloatManager"
    private const val NETWORK_SPEED_REFRESH_INTERVAL_MS = 1_000L
    private const val SLIDER_BREATH_DURATION_MS = 1_400L
    private const val SLIDER_BREATH_SCALE = 0.96f
    private const val SLIDER_BREATH_ALPHA = 0.92f

    private var sliderBreathingAnimator: AnimatorSet? = null
    private var type: Int = FloatTipType.DEFAULT
    private var permissionGuideType: String? = null
    private var functionGuideType: String? = null

    fun initSliderFloat(context : Context){
        LogUtils.d("showSliderFloat: existing=${sliderFloat != null}", FLOAT_TAG)
        if(sliderFloat == null){
            sliderFloat = createSliderFloat(context)
            sliderFloat?.setCallback(object : FloatWindowCallback {
                override fun onClick() {


                    when (type) {
                        FloatTipType.NETWORK_SPEED -> {
                            reportFunctionGuideClick()
                            showNetTestFloat(context)
                        }
                        FloatTipType.PERMISSION -> {
                            permissionGuideType?.let { guideType ->
                                reportEvent(
                                    EventUtils.EVENT_FLOATBALL_GUIDE_CLICK,
                                    mapOf(EventUtils.PARAM_TYPE to guideType),
                                )
                            }
                            openMissingPermissionRoute(context)
                        }
                        FloatTipType.JUNK -> {
                            reportFunctionGuideClick()
                            openHomeRoute(context,FloatRoute.QUICK_SCAN)
                        }
                        FloatTipType.IMAGE -> {
                            reportFunctionGuideClick()
                            openHomeRoute(context,FloatRoute.PHOTOS)
                        }
                        FloatTipType.NOTIFICATION -> {
                            reportFunctionGuideClick()
                            openHomeRoute(context,FloatRoute.NOTIFY)
                        }
                        FloatTipType.PROCESS -> {
                            reportFunctionGuideClick()
                            openHomeRoute(context,FloatRoute.WIDGET_PROCESS_MANAGER)
                        }
                        else -> {
                            showBottomFloat(context)
                        }
                    }
                }
            })
        }

        try {
            sliderFloat?.add(true)
        } catch (error: Exception) {
            LogUtils.w("Unable to attach floating ball", FLOAT_TAG, error)
            dismissSliderFloat()
        }
        LogUtils.d("showSliderFloat: add invoked", FLOAT_TAG)
    }

    
    private fun openMissingPermissionRoute(context: Context) {
        when {
            !FloatBallCoordinator.hasStorageAccess(context) ->
                openHomeRoute(context, FloatRoute.QUICK_SCAN)

            !FloatBallCoordinator.hasNotificationListenerAccess(context) ->
                openHomeRoute(context, FloatRoute.NOTIFY)

            else -> {

                restoreDefaultSliderFloat(context)
                showBottomFloat(context)
            }
        }
    }

    
    fun showSlideFloat(context : Context) {
        if (sliderFloat == null) {
            initSliderFloat(context)
            return
        }
        if (sliderFloat?.state == FloatWindowState.ATTACHED_HIDDEN) {
            sliderFloat?.setVisible(true)
        }
    }

    
    fun hideSliderFloat() {
        if(sliderFloat == null) {
            LogUtils.d("hideSliderFloat: ignored because slider is null", FLOAT_TAG)
            return
        }
        stopSliderBreathing()
        (sliderFloat?.getBinding() as? ViewFloatSliderBinding)?.orbHalo?.cancelAnimation()
        LogUtils.d("hideSliderFloat: setVisible(false)", FLOAT_TAG)
        sliderFloat?.setVisible(false)
    }


    
    fun showFloatTips(context : Context,
                      iconResourceId: Int,
                      value : String,
                      type: Int,
                      memoryValue: String = "") {
        this.type = type
        permissionGuideType = if (type == FloatTipType.PERMISSION) {
            resolvePermissionGuideType(context)
        } else {
            null
        }
        functionGuideType = resolveFunctionGuideType(type)
        when (type) {
            FloatTipType.PERMISSION -> setSliderFloatForFilesAccessNeed(memoryValue)
            FloatTipType.JUNK -> setSliderFloatForJunkClean()
            FloatTipType.IMAGE -> setSliderFloatForImage()
            FloatTipType.NOTIFICATION -> setSliderFloatForNotifyClean()
            FloatTipType.PROCESS -> setSliderFloatForAppClean()
            FloatTipType.NETWORK_SPEED -> setSliderFloatForNetTest()
            else -> setSliderFloatForJunkClean()
        }

        if (showTipFloat(context,iconResourceId,value)) {
            if (type == FloatTipType.PERMISSION) {
                permissionGuideType?.let { guideType ->
                    reportEvent(
                        EventUtils.EVENT_FLOATBALL_GUIDE_SHOW,
                        mapOf(EventUtils.PARAM_TYPE to guideType),
                    )
                }
            } else {
                functionGuideType?.let { guideType ->
                    reportEvent(
                        EventUtils.EVENT_FLOATBALL_FUNC_GUIDE_SHOW,
                        mapOf(EventUtils.PARAM_TYPE to guideType),
                    )
                }
            }
        }
    }

    private fun resolvePermissionGuideType(context: Context): String? = when {
        !FloatBallCoordinator.hasStorageAccess(context) -> EventUtils.FLOATBALL_GUIDE_TYPE_FILE
        !FloatBallCoordinator.hasNotificationListenerAccess(context) ->
            EventUtils.FLOATBALL_GUIDE_TYPE_NOTIFICATION
        else -> null
    }

    private fun resolveFunctionGuideType(type: Int): String? = when (type) {
        FloatTipType.JUNK -> EventUtils.FLOATBALL_FUNC_TYPE_JUNK
        FloatTipType.IMAGE -> EventUtils.FLOATBALL_FUNC_TYPE_PHOTO
        FloatTipType.NOTIFICATION -> EventUtils.FLOATBALL_FUNC_TYPE_NOTIFICATION
        FloatTipType.PROCESS -> EventUtils.FLOATBALL_FUNC_TYPE_APPS
        FloatTipType.NETWORK_SPEED -> EventUtils.FLOATBALL_FUNC_TYPE_WIFI
        else -> null
    }

    private fun reportFunctionGuideClick() {
        functionGuideType?.let { guideType ->
            reportEvent(
                EventUtils.EVENT_FLOATBALL_FUNC_GUIDE_CLICK,
                mapOf(EventUtils.PARAM_TYPE to guideType),
            )
        }
    }

    
    
    fun showBottomFloat(context : Context) {
        if(bottomFloat == null){
            bottomFloat = createBottomFloat(context)
        }
        val binding = (bottomFloat?.getBinding() as? ViewFloatBottomBinding)

        binding?.clAllContent?.apply {
            animate().cancel()
            alpha = 0f
            translationY = 48f * resources.displayMetrics.density
        }
        bottomFloat?.add(true)
        binding?.clAllContent?.animate()?.alpha(1f)?.translationY(0f)
            ?.setInterpolator(DecelerateInterpolator(1.8f))?.setDuration(320)?.start()

        reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_SHOW)
        (bottomFloat?.getBinding() as? ViewFloatBottomBinding)?.let {
            it.tvAppName.text = context.applicationInfo.loadLabel(context.packageManager)
            it.ivLogo.setImageDrawable(context.packageManager.getApplicationIcon(context.packageName))
            it.tvImageClean.setOnClickListener {
                reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_CLICK,mapOf(EventUtils.PARAM_TYPE to "photo"))
                openHomeRoute(context, FloatRoute.PHOTOS)

            }
            it.tvJunkClean.setOnClickListener { _->
                reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_CLICK,mapOf(EventUtils.PARAM_TYPE to "junk"))
                openHomeRoute(context, FloatRoute.QUICK_SCAN)

            }
            it.tvAppProcess.setOnClickListener {
                reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_CLICK,mapOf(EventUtils.PARAM_TYPE to "apps"))
                openHomeRoute(context, FloatRoute.WIDGET_PROCESS_MANAGER)

            }
            it.tvNotifyClean.setOnClickListener {
                reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_CLICK,mapOf(EventUtils.PARAM_TYPE to "notification"))
                openHomeRoute(context, FloatRoute.NOTIFY)

            }
            it.animCheck.setOnClickListener {

                reportEvent(EventUtils.EVENT_FLOATBALL_PANEL_CLICK,mapOf(EventUtils.PARAM_TYPE to "check"))
                openHomeRoute(context, FloatRoute.WIDGET_PROCESS_MANAGER)
            }

            it.animCheck.playAnimation()

            val memoryUsedPercent = MemoryUsageUtils.getMemoryUsedPercent(context)
            it.tvCheckValue.text = java.text.NumberFormat.getIntegerInstance().format(memoryUsedPercent)

            startBottomNetworkSpeedUpdates(it)

            bottomFloat?.setCallback(object : FloatWindowCallback {
                override fun onClick() {
                    destroyBottomFloat()
                }
            })
        }
    }

    private fun destroyBottomFloat() {
        LogUtils.d("destroy--bottomFloat",FLOAT_TAG)
        val binding = (bottomFloat?.getBinding() as? ViewFloatBottomBinding)

        binding?.animCheck?.cancelAnimation()
        stopBottomNetworkSpeedUpdates()
        binding?.clAllContent?.animate()?.cancel()
        bottomFloat?.destroy()
        bottomFloat = null
    }

    private fun openHomeRoute(context: Context, route: FloatRoute) {
        destroyBottomFloat()
        FloatingBall.host.onBeforeExternalEntry()
        FloatingBall.host.openRoute(context, route)

    }

    
    private fun startBottomNetworkSpeedUpdates(binding: ViewFloatBottomBinding) {
        stopBottomNetworkSpeedUpdates()

        var lastRxBytes = TrafficStats.getTotalRxBytes()
        var lastTxBytes = TrafficStats.getTotalTxBytes()
        var lastSampleTime = SystemClock.elapsedRealtime()
        binding.tvNetUp.text = formatNetworkSpeed(0L)
        binding.tvNetDown.text = formatNetworkSpeed(0L)

        val updater = object : Runnable {
            override fun run() {

                if (bottomFloat?.getBinding() !== binding) {
                    bottomNetworkSpeedUpdater = null
                    return
                }

                val now = SystemClock.elapsedRealtime()
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTxBytes = TrafficStats.getTotalTxBytes()
                val elapsedMs = (now - lastSampleTime).coerceAtLeast(1L)

                binding.tvNetUp.text = formatNetworkSpeed(
                    calculateBytesPerSecond(currentTxBytes, lastTxBytes, elapsedMs),
                )
                binding.tvNetDown.text = formatNetworkSpeed(
                    calculateBytesPerSecond(currentRxBytes, lastRxBytes, elapsedMs),
                )

                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes
                lastSampleTime = now
                mainHandler.postDelayed(this, NETWORK_SPEED_REFRESH_INTERVAL_MS)
            }
        }
        bottomNetworkSpeedUpdater = updater
        mainHandler.postDelayed(updater, NETWORK_SPEED_REFRESH_INTERVAL_MS)
    }

    private fun stopBottomNetworkSpeedUpdates() {
        bottomNetworkSpeedUpdater?.let(mainHandler::removeCallbacks)
        bottomNetworkSpeedUpdater = null
    }

    private fun calculateBytesPerSecond(current: Long, previous: Long, elapsedMs: Long): Long? {
        if (current == TrafficStats.UNSUPPORTED.toLong() ||
            previous == TrafficStats.UNSUPPORTED.toLong()
        ) {
            return null
        }
        return ((current - previous).coerceAtLeast(0L) * 1_000L) / elapsedMs
    }

    private fun formatNetworkSpeed(bytesPerSecond: Long?): String {
        if (bytesPerSecond == null) return FloatingBall.context.getString(R.string.string_float_unavailable)
        return when {
            bytesPerSecond < 1_024L -> FloatingBall.context.getString(R.string.string_float_bytes_speed, bytesPerSecond)
            bytesPerSecond < 1_024L * 1_024L ->
                FloatingBall.context.getString(R.string.string_float_kilobytes_speed, bytesPerSecond / 1_024f)
            else -> FloatingBall.context.getString(
                R.string.string_float_megabytes_speed,
                bytesPerSecond / (1_024f * 1_024f),
            )
        }
    }

    fun showTipFloat(
        context : Context,
        iconResourceId: Int,
        value : String
    ): Boolean =
        runCatching {
            dismissTipFloat()
            val tipConfig = FloatWindowConfig.builder(context.applicationContext)
                .mode(FloatMode.FIXED)
                .initialGravity(FloatGravity.TOP_RIGHT)
                .startY((FloatUtil.getScreenHeight(context.applicationContext) * 0.2).toInt())
                .build()
            val tipFloat = FloatWindowManager.getInstance().createFloatWindow(
                tag = TIP_FLOAT_TAG,
                bindingInflater = ViewFloatTipBinding::inflate,
                config = tipConfig
            )

            (tipFloat.getBinding() as? ViewFloatTipBinding)?.let {
                it.tvTipValue.text = value
                it.ivTipIcon.setImageResource(iconResourceId)
            }

            tipFloat.add(true)

            FloatWindowLinker.link(
                mainTag = SLIDER_FLOAT_TAG ,
                tipTag = TIP_FLOAT_TAG ,
                offsetY = 0,
                space = 0,
                followDrag = false
            )
            true
        }.onFailure {
            LogUtils.w("show floating tip failed: ${it.message}", FLOAT_TAG)
        }.getOrDefault(false)

    fun showNetTestFloat(context : Context) {
        runCatching {
            dismissNetTestFloat()
            FloatingBall.host.preloadNetworkResult(context)
            netTestFloat = createNetTestFloat(context)

            (netTestFloat?.getBinding() as? ViewFloatNetSpeedBinding)?.let {
                val layoutAnim = it.clSpeedAnim
                val layoutResult = it.clSpeedResult
                val animTest = it.animSpeed
                layoutAnim.visibility = View.VISIBLE
                layoutResult.visibility = View.GONE
                animTest.playAnimation()
            }
            netTestFloat?.add(true)
            reportEvent(EventUtils.EVENT_FLOATBALL_SCAN_SHOW)

            startNetTest(netTestFloat, context.applicationContext)
        }
    }

    
    private fun startNetTest(testingFloat: IFloatWindow?, context: Context) {
        if (testingFloat == null) return

        netTestJob = netTestScope.launch {
            try {
                val result = if (NetworkSpeedTester.hasNetwork(context)) {
                    val downloadResult = NetworkSpeedTester.runDownloadTest { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            updateNetTestSpeed(
                                testingFloat = testingFloat,
                                speedMbps = progress.instantaneousMbps,
                            )
                        }
                    }
                    var uploadSucceeded = true
                    val uploadMbps = try {
                        NetworkSpeedTester.runUploadTest()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        uploadSucceeded = false
                        LogUtils.w(
                            "floating upload test failed: ${t.javaClass.simpleName}: ${t.message}",
                            FLOAT_TAG,
                        )
                        0.0
                    }
                    NetTestResult(
                        uploadMbps = uploadMbps,
                        downloadMbps = downloadResult.averageMbps,
                        result = if (uploadSucceeded) {
                            EventUtils.RESULT_SUCCESS
                        } else {
                            EventUtils.RESULT_FAILED
                        },
                    )
                } else {
                    NetTestResult(
                        uploadMbps = 0.0,
                        downloadMbps = 0.0,
                        result = EventUtils.RESULT_FAILED,
                    )
                }
                if (netTestFloat === testingFloat) {
                    finishNetTest(testingFloat, context, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                LogUtils.w(
                    "floating download test failed: ${t.javaClass.simpleName}: ${t.message}",
                    FLOAT_TAG,
                )
                if (netTestFloat === testingFloat) {
                    finishNetTest(
                        testingFloat,
                        context,
                        NetTestResult(
                            uploadMbps = 0.0,
                            downloadMbps = 0.0,
                            result = EventUtils.RESULT_FAILED,
                        ),
                    )
                }
            }
        }
    }

    
    private fun finishNetTest(
        testingFloat: IFloatWindow,
        context: Context,
        result: NetTestResult,
    ) {
        if (netTestFloat !== testingFloat) return

        netTestJob = null
        reportEvent(
            EventUtils.EVENT_FLOATBALL_SCAN_SUCC,
            mapOf(EventUtils.PARAM_RESULT to result.result),
        )
        val resultText = context.getString(
            R.string.string_float_network_status_result,
            networkSpeedDescription(context, result.downloadMbps),
        )
        val activityStarted = runCatching {
            FloatingBall.host.onBeforeExternalEntry()
            FloatNetworkResultActivity.start(
                context = context,
                resultText = resultText,
                uploadMbps = result.uploadMbps,
                downloadMbps = result.downloadMbps,
            )
        }.onFailure {
            LogUtils.e("open floating network result failed: ${it.message}", FLOAT_TAG, it)
        }.isSuccess

        if (activityStarted) {
            netTestFloat = null
            testingFloat.destroy()
        } else {
            setNetTestFloatToResult(
                value = resultText,
                uploadMbps = result.uploadMbps,
                downloadMbps = result.downloadMbps,
            )
        }
    }

    
    fun restoreDefaultSliderFloat(context: Context) {
        type = FloatTipType.DEFAULT
        showSlideFloat(context)
        setSliderFloatForDefault(context.getString(R.string.string_float_percent, MemoryUsageUtils.getMemoryUsedPercent(context)))
    }

    
    private fun updateNetTestSpeed(testingFloat: IFloatWindow, speedMbps: Double) {
        if (netTestFloat !== testingFloat) return

        (testingFloat.getBinding() as? ViewFloatNetSpeedBinding)?.let {
            it.tvSpeed.text = String.format(Locale.getDefault(), "%.2f", speedMbps)
        }
    }

    private fun networkSpeedDescription(context: Context, instantaneousMbps: Double): String {
        return when {
            instantaneousMbps > 8.0 -> context.getString(R.string.string_float_network_speed_very_fast)
            instantaneousMbps > 5.0 -> context.getString(R.string.string_float_network_speed_good)
            instantaneousMbps > 1.0 -> context.getString(R.string.string_float_network_speed_normal)
            instantaneousMbps > 0.2 -> context.getString(R.string.string_float_network_speed_slow)
            else -> context.getString(R.string.string_float_network_speed_very_slow)
        }
    }

    private fun setNetTestFloatToResult(
        value: String,
        uploadMbps: Double,
        downloadMbps: Double,
    ) {
        val float = netTestFloat ?: return
        (float.getBinding() as? ViewFloatNetSpeedBinding)?.let {
            val layoutAnim = it.clSpeedAnim
            val layoutResult = it.clSpeedResult
            val animTest = it.animSpeed
            val tvNetResult = it.tvNetResult
            val tvNetUp = it.tvNetUp
            val tvNetDown = it.tvNetDown

            layoutResult.visibility = View.VISIBLE
            layoutAnim.visibility = View.GONE
            animTest.cancelAnimation()
            tvNetResult.text = value
            tvNetUp.text = formatNetTestResultSpeed(uploadMbps)
            tvNetDown.text = formatNetTestResultSpeed(downloadMbps)

            it.ivClose.setOnClickListener { _->
                dismissNetTestFloat()
                restoreDefaultSliderFloat(it.root.context.applicationContext)
            }
        }

    }

    fun formatNetTestResultSpeed(speedMbps: Double): String {
        return FloatingBall.context.getString(R.string.string_float_result_speed, speedMbps.coerceAtLeast(0.0))
    }

    private data class NetTestResult(
        val uploadMbps: Double,
        val downloadMbps: Double,
        val result: String,
    )

    private fun reportEvent(event: String, params: Map<String, String> = emptyMap()) {
        runCatching { FloatingBall.host.reportEvent(event, params) }
            .onFailure { LogUtils.w("report $event failed: ${it.message}", FLOAT_TAG) }
    }

    fun setSliderFloatForDefault(value : String) {
        runCatching {
            (sliderFloat?.getBinding() as? ViewFloatSliderBinding)?.let {
                stopSliderBreathing()
                it.tvSliderValue.visibility = View.VISIBLE
                it.ivSliderBg.visibility = View.GONE
                it.orbHalo.playAnimation()

                it.tvSliderValue.text = value
            }
        }
    }

    
    fun setSliderFloatForFilesAccessNeed(value : String) {
        setSliderFloatForReminder(
            iconResourceId = R.drawable.float_ic_permission,
            value = value,
        )
    }




    
    fun setSliderFloatForJunkClean() {
        setSliderFloatForReminder(R.drawable.float_ic_clean)
    }

    
    fun setSliderFloatForImage() {
        setSliderFloatForReminder(R.drawable.float_ic_photo)
    }


    fun setSliderFloatForNotifyClean(){
        setSliderFloatForReminder(R.drawable.float_ic_notification)
    }

    fun setSliderFloatForAppClean(){
        setSliderFloatForReminder(R.drawable.float_ic_apps)
    }

    fun setSliderFloatForNetTest(){
        setSliderFloatForReminder(R.drawable.float_ic_network)
    }

    
    private fun setSliderFloatForReminder(iconResourceId: Int, value: String? = null) {
        runCatching {
            (sliderFloat?.getBinding() as? ViewFloatSliderBinding)?.let { binding ->
                binding.orbHalo.playAnimation()
                binding.ivSliderBg.visibility = if (value == null) View.VISIBLE else View.GONE
                binding.ivSliderBg.setImageResource(iconResourceId)
                binding.tvSliderValue.apply {
                    visibility = if (value == null) View.GONE else View.VISIBLE
                    text = value.orEmpty()
                }
                startSliderBreathing(binding.root)
            }
        }
    }


    private fun startSliderBreathing(target: View) {
        if (sliderBreathingAnimator?.isRunning == true) return
        resetSliderTransform(target)
        sliderBreathingAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, SLIDER_BREATH_SCALE),
                ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, SLIDER_BREATH_SCALE),
                ObjectAnimator.ofFloat(target, View.ALPHA, 1f, SLIDER_BREATH_ALPHA),
            )
            duration = SLIDER_BREATH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            childAnimations.forEach { animator ->
                (animator as ObjectAnimator).apply {
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
            }
            start()
        }
    }

    private fun stopSliderBreathing() {
        sliderBreathingAnimator?.cancel()
        sliderBreathingAnimator = null
        (sliderFloat?.getBinding() as? ViewFloatSliderBinding)?.root?.let(::resetSliderTransform)
    }

    private fun resetSliderTransform(target: View) {
        target.scaleX = 1f
        target.scaleY = 1f
        target.alpha = 1f
    }

    fun dismissSliderFloat(){
        stopSliderBreathing()
        (sliderFloat?.getBinding() as? ViewFloatSliderBinding)?.orbHalo?.cancelAnimation()
        FloatWindowLinker.unlink(SLIDER_FLOAT_TAG)
        sliderFloat?.destroy()
        sliderFloat = null
    }

    fun dismissTipFloat(){
        val tipFloat = FloatWindowManager.getInstance().getFloatWindow(TIP_FLOAT_TAG)
        if(tipFloat != null){
            FloatWindowLinker.unlink(SLIDER_FLOAT_TAG)
            tipFloat.destroy()
        }
    }

    fun dismissNetTestFloat(){
        (netTestFloat?.getBinding() as? ViewFloatNetSpeedBinding)?.animSpeed?.cancelAnimation()
        netTestJob?.cancel()
        netTestJob = null
        if(netTestFloat != null){
            netTestFloat?.destroy()
            netTestFloat = null
        }
    }

    
    fun dismissAllFloatWindows() {
        dismissTipFloat()
        dismissPanels()
        dismissSliderFloat()
    }

    fun dismissPanels() {
        destroyBottomFloat()
        dismissNetTestFloat()
    }





    private fun createSliderFloat(context: Context) : IFloatWindow{
        val size = (40 * context.resources.displayMetrics.density).toInt()
        val sliderConfig = FloatWindowConfig.builder(context.applicationContext)
            .width(size)
            .height(size)
            .mode(FloatMode.SLIDE)
            .initialGravity(FloatGravity.TOP_RIGHT)
            .autoEdge(true)
            .edgePadding(12)
            .edgeAnimDuration(250)
            .startX(12)
            .startY((FloatUtil.getScreenHeight(context) * 0.2).toInt())
            .build()

        return FloatWindowManager.getInstance().createFloatWindow(
            tag = SLIDER_FLOAT_TAG,
            bindingInflater = ViewFloatSliderBinding::inflate,
            config = sliderConfig
        )
    }

    private fun createBottomFloat(context: Context) : IFloatWindow{
        val bottomConfig = FloatWindowConfig.builder(context.applicationContext)
            .mode(FloatMode.FIXED)
            .initialGravity(FloatGravity.BOTTOM_CENTER)
            .width(WindowManager.LayoutParams.MATCH_PARENT)
            .height(WindowManager.LayoutParams.MATCH_PARENT)
            .fullScreen(true)
            .build()

        return FloatWindowManager.getInstance().createFloatWindow(
            tag = BOTTOM_FLOAT_TAG,
            bindingInflater = ViewFloatBottomBinding::inflate,
            config = bottomConfig
        )
    }

    private fun createNetTestFloat(context: Context) : IFloatWindow{
        val netTestConfig = FloatWindowConfig.builder(context.applicationContext)
            .mode(FloatMode.FIXED)
            .initialGravity(FloatGravity.CENTER)
            .width(WindowManager.LayoutParams.MATCH_PARENT)
            .height(WindowManager.LayoutParams.MATCH_PARENT)
            .fullScreen(true)
            .build()

        return FloatWindowManager.getInstance().createFloatWindow(
            tag = NET_TEST_FLOAT_TAG,
            bindingInflater = ViewFloatNetSpeedBinding::inflate,
            config = netTestConfig
        )
    }

}
