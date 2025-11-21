package com.insightcrayon.autotest

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlin.random.Random

class AutoTouchAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var targetPackageName: String? = null
    private val clickableNodes = mutableListOf<Rect>()

    private var overlayView: TouchOverlayView? = null
    private var windowManager: WindowManager? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return

        // 현재 활성 앱이 목표 앱이 아니면 터치 중지
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackage = event.packageName?.toString()

            // 시스템 UI, 다이얼로그 등은 무시
            val ignoredPackages = listOf(
                "android",
                "com.android.systemui",
                "com.android.launcher",
                "com.google.android",
                "com.sec.android",  // 삼성 시스템 UI
                "com.insightcrayon.autotest"  // 컨트롤 앱 자체도 무시
            )

            val shouldIgnore = ignoredPackages.any { currentPackage?.startsWith(it) == true }

            if (currentPackage != null && currentPackage != targetPackageName && !shouldIgnore) {
                // 실제로 다른 앱으로 이동했으면 터치 중지
                android.util.Log.d("AutoTouch", "앱이 전환됨: $targetPackageName -> $currentPackage, 터치 중지")
                stopTouching()
                return
            }
        }

        // UI가 변경될 때마다 클릭 가능한 영역 업데이트
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            updateClickableAreas()
        }
    }

    override fun onInterrupt() {
        stopTouching()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "자동 터치 서비스 연결됨", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TOUCHING -> {
                targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                startTouching()
            }
            ACTION_STOP_TOUCHING -> {
                stopTouching()
            }
        }
        return START_STICKY
    }

    private fun startTouching() {
        if (isRunning) return

        isRunning = true
        Toast.makeText(this, "무작위 터치 시작! (UI 인식)", Toast.LENGTH_SHORT).show()

        // 터치 오버레이 표시
        showTouchOverlay()

        // 초기 UI 스캔
        updateClickableAreas()

        val runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    performSmartClick()
                    // 3~8초 후 다시 터치
                    handler.postDelayed(this, Random.nextLong(3000, 8000))
                }
            }
        }

        handler.post(runnable)
    }

    private fun stopTouching() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        clickableNodes.clear()

        // 터치 오버레이 제거
        hideTouchOverlay()

        Toast.makeText(this, "무작위 터치 종료", Toast.LENGTH_SHORT).show()
    }

    private fun showTouchOverlay() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = TouchOverlayView(this)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START

            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            // 오버레이 추가 실패 시 무시
        }
    }

    private fun hideTouchOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            // 오버레이 제거 실패 시 무시
        }
    }

    private fun updateClickableAreas() {
        clickableNodes.clear()

        try {
            val rootNode = rootInActiveWindow ?: return
            findClickableNodes(rootNode)
            rootNode.recycle()
        } catch (e: Exception) {
            // 에러 무시
        }
    }

    private fun findClickableNodes(node: AccessibilityNodeInfo) {
        try {
            // 클릭 가능하거나 포커스 가능한 UI 요소 찾기
            if (node.isClickable || node.isFocusable || node.isLongClickable) {
                // 위험한 버튼 필터링 (로그아웃, 삭제, 계정 등)
                if (isSafeToClick(node)) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)

                    // 화면에 보이고 크기가 적절한 요소만 추가
                    if (rect.width() > 50 && rect.height() > 50 &&
                        rect.top > 100 && rect.bottom < resources.displayMetrics.heightPixels - 100) {
                        clickableNodes.add(rect)
                    }
                }
            }

            // 자식 노드들도 재귀적으로 검색
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    findClickableNodes(child)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            // 에러 무시
        }
    }

    private fun isSafeToClick(node: AccessibilityNodeInfo): Boolean {
        try {
            // 텍스트나 contentDescription에서 위험한 키워드 체크
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val combinedText = "$text $contentDesc".lowercase().trim()

            // 위험한 키워드 목록 (로그아웃, 삭제, 계정 관련)
            val dangerousKeywords = listOf(
                // 로그아웃 관련
                "logout", "log out", "log-out", "로그아웃", "로그 아웃",
                "sign out", "signout", "sign-out", "사인아웃",
                "로그 아웃", "로그아웃하기", "로그아웃 하기",

                // 삭제/제거 관련
                "delete", "삭제", "제거", "삭제하기",
                "remove", "탈퇴", "탈퇴하기",
                "uninstall", "언인스톨",
                "clear", "초기화", "clear data",

                // 리셋 관련
                "reset", "리셋", "재설정",

                // 종료 관련
                "exit", "종료", "나가기", "exit app",
                "quit", "닫기", "close",

                // 확인/취소 (위험한 다이얼로그 방지)
                "cancel", "취소",
                "confirm", "확인", "확인하기",
                "yes", "네", "예", "okay",
                "ok", "오케이", "승인",
                "agree", "동의", "동의합니다",
                "accept", "수락",

                // 설정/프로필 관련
                "setting", "설정", "환경설정", "settings",
                "profile", "프로필", "내 정보", "my info",
                "account", "계정", "계정 관리", "account setting",

                // 회원 관련
                "withdraw", "회원탈퇴", "회원 탈퇴",
                "leave", "나가기", "leave group",
                "unsubscribe", "구독 취소",

                // 추가 위험 키워드
                "done", "완료", "finish",
                "apply", "적용", "적용하기",
                "save", "저장", "저장하기",
                "change", "변경", "변경하기",
                "edit", "수정", "편집"
            )

            // 위험한 키워드가 포함되어 있으면 터치 금지
            for (keyword in dangerousKeywords) {
                if (combinedText.contains(keyword)) {
                    return false
                }
            }

            // 버튼의 리소스 ID 체크 (로그아웃 관련)
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("logout") || viewId.contains("signout") ||
                viewId.contains("delete") || viewId.contains("remove") ||
                viewId.contains("account") || viewId.contains("setting")) {
                return false
            }

            return true
        } catch (e: Exception) {
            // 에러 발생 시 안전하게 터치하지 않음
            return false
        }
    }

    private fun performSmartClick() {
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            var x: Float
            var y: Float

            // 안전한 UI 요소가 있으면 그걸 터치, 없으면 스와이프만
            if (clickableNodes.isNotEmpty()) {
                // UI 요소 중 하나를 랜덤 선택
                val targetRect = clickableNodes.random()

                // 요소 중앙 근처를 터치 (약간의 랜덤성 추가)
                x = targetRect.centerX() + Random.nextInt(-20, 20).toFloat()
                y = targetRect.centerY() + Random.nextInt(-20, 20).toFloat()

                // 디버깅: 터치할 UI 요소 정보 출력
                logClickedElement(x, y)
            } else {
                // UI 요소가 없으면 화면 중앙 근처 좌표 사용 (스와이프용)
                x = screenWidth / 2f + Random.nextInt(-200, 200)
                y = screenHeight / 2f + Random.nextInt(-200, 200)
                android.util.Log.d("AutoTouch", "클릭 가능한 UI 없음, 스와이프만 실행")
            }

            // 랜덤으로 액션 선택
            val random = Random.nextFloat()

            // UI 요소가 있을 때만 터치, 없으면 스와이프만
            if (clickableNodes.isNotEmpty()) {
                when {
                    random < 0.6f -> {
                        // 60% 일반 터치
                val gesture = createTapGesture(x, y)
                overlayView?.showTouch(x, y)
                recordTouch()
                dispatchGesture(gesture, createGestureCallback(), null)
                    }
                    random < 0.8f -> {
                        // 20% 스와이프 (스크롤)
                    val gesture = createSwipeGesture(x, y)
                    overlayView?.showTouch(x, y)
                    recordTouch()
                    dispatchGesture(gesture, createGestureCallback(), null)
                    }
                    else -> {
                        // 20% 뒤로가기 (메인 화면이 아닐 때만)
                        performSmartBackPress()
                    }
                }
            } else {
                // UI 요소가 없으면 스와이프만 실행
            val gesture = createSwipeGesture(x, y)
            overlayView?.showTouch(x, y)
            recordTouch()
            dispatchGesture(gesture, createGestureCallback(), null)
            }

        } catch (e: Exception) {
            // 에러 무시
        }
    }

    private fun createGestureCallback(): GestureResultCallback {
        return object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // 터치 성공 후 가끔 UI 업데이트
                if (Random.nextFloat() < 0.3f) {
                    handler.postDelayed({ updateClickableAreas() }, 500)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                // 터치 실패 (무시)
            }
        }
    }

    private fun performSmartBackPress() {
        try {
            // 현재 화면이 메인 화면인지 확인
            val rootNode = rootInActiveWindow ?: return
            val currentPackage = rootNode.packageName?.toString() ?: ""
            rootNode.recycle()

            // 현재 Activity의 클래스 이름을 확인
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningTasks = try {
                activityManager.getRunningTasks(1)
            } catch (e: Exception) {
                null
            }

            val currentActivity = runningTasks?.firstOrNull()?.topActivity?.className ?: ""

            // 메인 Activity인지 확인 (일반적으로 MainActivity, LauncherActivity 등)
            val isMainActivity = currentActivity.contains("MainActivity", ignoreCase = true) ||
                                 currentActivity.contains("LauncherActivity", ignoreCase = true) ||
                                 currentActivity.contains("HomeActivity", ignoreCase = true) ||
                                 currentActivity.endsWith(".Main", ignoreCase = true)

            // 메인 화면이 아닐 때만 뒤로가기 실행
            if (!isMainActivity) {
                performGlobalAction(GLOBAL_ACTION_BACK)

                // 뒤로가기 표시 (화면 중앙에 작은 효과)
                val displayMetrics = resources.displayMetrics
                val centerX = displayMetrics.widthPixels / 2f
                val centerY = displayMetrics.heightPixels / 2f
                overlayView?.showTouch(centerX, centerY)
                recordTouch()

                android.util.Log.d("AutoTouch", "뒤로가기 실행: $currentActivity")
            } else {
                android.util.Log.d("AutoTouch", "메인 화면이므로 뒤로가기 건너뜀: $currentActivity")
            }
        } catch (e: Exception) {
            // 뒤로가기 실패 시 무시
            android.util.Log.e("AutoTouch", "뒤로가기 실패: ${e.message}")
        }
    }

    private fun performBackPress() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            // 뒤로가기 표시 (화면 중앙에 작은 효과)
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2f
            val centerY = displayMetrics.heightPixels / 2f
            overlayView?.showTouch(centerX, centerY)
            recordTouch()
        } catch (e: Exception) {
            // 뒤로가기 실패 시 무시
        }
    }

    private fun recordTouch() {
        val logId = TestLogTracker.currentTestId ?: return
        try {
            TestLogDatabase.getInstance(this).incrementTouchCount(logId)
        } catch (_: Exception) {
            // ignore failures
        }
    }

    private fun logClickedElement(x: Float, y: Float) {
        try {
            val rootNode = rootInActiveWindow ?: return
            val node = findNodeAtPosition(rootNode, x, y)

            if (node != null) {
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val viewId = node.viewIdResourceName ?: ""

                if (text.isNotEmpty() || desc.isNotEmpty()) {
                    android.util.Log.d("AutoTouch", "터치: text='$text', desc='$desc', id='$viewId'")
                    // Toast로도 표시 (디버깅용)
                    // Toast.makeText(this, "터치: $text $desc", Toast.LENGTH_SHORT).show()
                }
                node.recycle()
            }
            rootNode.recycle()
        } catch (e: Exception) {
            // 로깅 실패 무시
        }
    }

    private fun findNodeAtPosition(node: AccessibilityNodeInfo, x: Float, y: Float): AccessibilityNodeInfo? {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.contains(x.toInt(), y.toInt())) {
                // 자식 노드 중에서 더 정확한 노드 찾기
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        val found = findNodeAtPosition(child, x, y)
                        if (found != null) {
                            return found
                        }
                        child.recycle()
                    }
                }
                return node
            }
        } catch (e: Exception) {
            // 에러 무시
        }
        return null
    }

    private fun createTapGesture(x: Float, y: Float): GestureDescription {
        val path = Path()
        path.moveTo(x, y)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        return gestureBuilder.build()
    }

    private fun createSwipeGesture(startX: Float, startY: Float): GestureDescription {
        val path = Path()
        path.moveTo(startX, startY)

        // 랜덤 방향으로 스와이프 (주로 위아래)
        val endX = startX + Random.nextInt(-100, 100)
        val endY = if (Random.nextBoolean()) {
            startY - Random.nextInt(200, 400) // 위로 스와이프
        } else {
            startY + Random.nextInt(200, 400) // 아래로 스와이프
        }

        path.lineTo(endX.toFloat(), endY.toFloat())

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
        return gestureBuilder.build()
    }

    companion object {
        const val ACTION_START_TOUCHING = "com.insightcrayon.autotest.START_TOUCHING"
        const val ACTION_STOP_TOUCHING = "com.insightcrayon.autotest.STOP_TOUCHING"
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun startTouching(context: Context, packageName: String) {
            val intent = Intent(context, AutoTouchAccessibilityService::class.java).apply {
                action = ACTION_START_TOUCHING
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            context.startService(intent)
        }

        fun stopTouching(context: Context) {
            val intent = Intent(context, AutoTouchAccessibilityService::class.java).apply {
                action = ACTION_STOP_TOUCHING
            }
            context.startService(intent)
        }
    }
}
