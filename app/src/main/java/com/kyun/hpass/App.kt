package com.kyun.hpass

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.kakao.auth.*
import com.kyun.hpass.Service.HService
import com.kyun.hpass.util.objects.Singleton
import io.realm.Realm


/**
 * Created by kyun on 2018. 3. 13..
 */
class App : Application() {

    var mAppStatus = AppStatus.BACKGROUND

    var TopActivity : Activity? = null

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        KakaoSDK.init(KakaoSDKAdapter())
        startService(Intent(this, HService::class.java))

        registerActivityLifecycleCallbacks(LifecycleCallbacks())
    }

    fun getAppstatus() : AppStatus { return mAppStatus }

    fun isForeground(): Boolean {
        return mAppStatus == AppStatus.FOREGROUND
    }

    fun isBackground(): Boolean {
        return mAppStatus == AppStatus.BACKGROUND
    }

    enum class AppStatus {
        BACKGROUND, // app is background
        RETURNED_TO_FOREGROUND, // app returned to foreground(or first launch)
        FOREGROUND
        // app is foreground
    }

    fun DoAppDestory() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    inner class LifecycleCallbacks : ActivityLifecycleCallbacks {

        var running = 0

        override fun onActivityPaused(p0: Activity?) {
        }

        override fun onActivityResumed(p0: Activity?) {
        }

        override fun onActivityStarted(p0: Activity?) {
            TopActivity = p0
            if (++running == 1) {
                // running activity is 1,
                // app must be returned from background just now (or first launch)
                mAppStatus = AppStatus.RETURNED_TO_FOREGROUND
            } else if (running > 1) {
                // 2 or more running activities,
                // should be foreground already.
                mAppStatus = AppStatus.FOREGROUND
            }
        }

        override fun onActivityDestroyed(p0: Activity?) {
        }

        override fun onActivitySaveInstanceState(p0: Activity?, p1: Bundle?) {
        }

        override fun onActivityStopped(p0: Activity?) {
            if (--running == 0) {
                // no active activity
                // app goes to background
                mAppStatus = AppStatus.BACKGROUND
            }
        }

        override fun onActivityCreated(p0: Activity?, p1: Bundle?) {
        }

    }

    inner class KakaoSDKAdapter : KakaoAdapter() {
        /**
         * Session Config에 대해서는 default값들이 존재한다.
         * 필요한 상황에서만 override해서 사용하면 됨.
         * @return Session의 설정값.
         */
        override fun getSessionConfig(): ISessionConfig {
            return object : ISessionConfig {
                override fun getAuthTypes(): Array<AuthType> {
                    return arrayOf(AuthType.KAKAO_LOGIN_ALL)
                }

                override fun isUsingWebviewTimer(): Boolean {
                    return false
                }

                override fun isSecureMode(): Boolean {
                    return false
                }

                override fun getApprovalType(): ApprovalType {
                    return ApprovalType.INDIVIDUAL
                }

                override fun isSaveFormData(): Boolean {
                    return true
                }
            }
        }

        override fun getApplicationConfig(): IApplicationConfig {
            return IApplicationConfig { applicationContext }
        }

    }
}