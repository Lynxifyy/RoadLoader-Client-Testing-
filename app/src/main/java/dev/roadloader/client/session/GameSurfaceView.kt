package dev.roadloader.client.session

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.KeyEvent
import android.view.MotionEvent
import dev.roadloader.core.RoadConstants

/**
 * Fullscreen GLES 3 surface for the streamed frame, the HUD and the mod menu.
 *
 * Pointer 0 is offered to the overlay first (menu button, menu window); every
 * other pointer and everything the overlay ignores is forwarded into the
 * companion's input ring.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    private var renderer: GameRenderer? = null
    private var host: EngineHost? = null
    private var overlayCapturedPointer0 = false

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun attach(host: EngineHost, renderer: GameRenderer) {
        this.host = host
        this.renderer = renderer
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun detach() {
        val active = renderer
        renderer = null
        host = null
        // The overlay owns GL objects, so it has to be torn down on the thread
        // that owns the context.
        if (active != null) queueEvent { active.release() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val renderer = renderer ?: return false
        val session = host?.session
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                overlayCapturedPointer0 =
                    renderer.touch(GameRenderer.ACTION_DOWN, event.x, event.y)
                if (!overlayCapturedPointer0) {
                    forward(event, 0, RoadConstants.INPUT_DOWN)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                forward(event, event.actionIndex, RoadConstants.INPUT_DOWN)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!overlayCapturedPointer0) {
                    renderer.touch(GameRenderer.ACTION_MOVE, event.getX(0), event.getY(0))
                }
                for (index in 0 until event.pointerCount) {
                    if (index == 0 && overlayCapturedPointer0) continue
                    forward(event, index, RoadConstants.INPUT_MOVE)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                forward(event, event.actionIndex, RoadConstants.INPUT_UP)
            }
            MotionEvent.ACTION_UP -> {
                if (overlayCapturedPointer0) {
                    renderer.touch(GameRenderer.ACTION_UP, event.x, event.y)
                    overlayCapturedPointer0 = false
                } else {
                    forward(event, 0, RoadConstants.INPUT_UP)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                renderer.touch(GameRenderer.ACTION_CANCEL, event.x, event.y)
                overlayCapturedPointer0 = false
                session?.pushInput(
                    kind = RoadConstants.INPUT_CANCEL,
                    pointerId = 0,
                    action = MotionEvent.ACTION_CANCEL,
                    x = 0f,
                    y = 0f,
                )
            }
        }
        return true
    }

    private fun forward(event: MotionEvent, index: Int, kind: Int) {
        val session = host?.session ?: return
        if (width == 0 || height == 0) return
        val scaleX = session.width.toFloat() / width.toFloat()
        val scaleY = session.height.toFloat() / height.toFloat()
        session.pushInput(
            kind = kind,
            pointerId = event.getPointerId(index),
            action = event.actionMasked,
            x = event.getX(index) * scaleX,
            y = event.getY(index) * scaleY,
            pressure = event.getPressure(index),
            size = event.getSize(index),
            timestampNs = event.eventTime * 1_000_000L,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val host = host ?: return super.dispatchKeyEvent(event)
        val gamepad = event.source and android.view.InputDevice.SOURCE_GAMEPAD != 0 ||
            event.source and android.view.InputDevice.SOURCE_JOYSTICK != 0
        if (!gamepad) return super.dispatchKeyEvent(event)
        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (event.repeatCount > 0) return true
        host.pushKey(event.keyCode, pressed)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val host = host ?: return super.dispatchGenericMotionEvent(event)
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == 0) {
            return super.dispatchGenericMotionEvent(event)
        }
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        host.session?.pushInput(
            kind = RoadConstants.INPUT_STICK,
            pointerId = 0,
            action = 1,
            x = x,
            y = y,
            pressure = 1f,
            button = ((rx * 100f).toInt() shl 16) or (ry * 100f).toInt(),
        )
        return true
    }
}
