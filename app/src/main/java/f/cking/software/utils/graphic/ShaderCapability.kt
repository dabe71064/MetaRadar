package f.cking.software.utils.graphic

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.os.PowerManager
import timber.log.Timber

object ShaderCapability {

    private var cachedResult: Boolean? = null
    private var lastPowerSaveMode: Boolean? = null

    fun canUseShader(context: Context): Boolean {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isPowerSave = powerManager.isPowerSaveMode

            // Recompute if power mode changed or cache is empty
            if (cachedResult != null && lastPowerSaveMode == isPowerSave) {
                return cachedResult!!
            }

            // If power-saving mode is on, shader should not run
            if (isPowerSave) {
                cachedResult = false
                lastPowerSaveMode = true
                return false
            }

            lastPowerSaveMode = false
            return checkGPU()
        } catch (e: Throwable) {
            Timber.e(e)
            cachedResult = false
            return false
        }
    }

    private fun checkGPU(): Boolean {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)

        val contextEGL = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, contextEGL)

        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.lowercase() ?: ""
        val versionStr = GLES20.glGetString(GLES20.GL_VERSION)?.lowercase() ?: ""
        val glEsVersion = versionStr.substringAfter("opengl es ").substringBefore(" ").toFloatOrNull() ?: 2.0f

        val lowEndGpus = listOf("mali-400", "powervr sgx 5", "adreno 3")
        cachedResult = !lowEndGpus.any { renderer.contains(it) } && glEsVersion >= 2.0f

        EGL14.eglDestroyContext(display, contextEGL)
        EGL14.eglTerminate(display)

        return cachedResult!!
    }
}