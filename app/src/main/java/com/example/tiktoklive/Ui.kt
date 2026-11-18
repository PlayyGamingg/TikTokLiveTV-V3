package com.example.tiktoklive

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

object Ui {
    fun btnBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused),
            GradientDrawable().apply { setColor(0xFFFE2C55.toInt()); cornerRadius = 20f })
        addState(intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply { setColor(0xFFFE2C55.toInt()); cornerRadius = 20f })
        addState(intArrayOf(),
            GradientDrawable().apply { setColor(0xFF2A2A2A.toInt()); cornerRadius = 20f })
    }

    fun mkButton(ctx: Context, sp: Float = 16f): Button = Button(ctx).apply {
        isAllCaps = false
        textSize = sp
        setTextColor(Color.WHITE)
        setPadding(28, 14, 28, 14)
        isFocusable = true
        background = btnBg()
    }
}
