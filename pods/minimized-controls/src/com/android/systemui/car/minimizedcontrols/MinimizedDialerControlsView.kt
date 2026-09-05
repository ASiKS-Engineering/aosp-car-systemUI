/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.minimizedcontrols

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.car.telephony.common.TelecomUtils.createLetterTile
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

/**
 * A custom view to display minimized dialer controls.
 */
class MinimizedDialerControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var profileContainer: View
    private lateinit var muteButton: ImageButton
    private lateinit var endCallButton: ImageButton
    private lateinit var dialpadButton: ImageButton
    private lateinit var contactImage: ImageView
    private lateinit var appIcon: ImageView
    private var displayNameText: android.widget.TextView? = null
    private var durationText: android.widget.Chronometer? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.minimized_dialer_controls_view, this, true)

        profileContainer = findViewById(R.id.dialer_profile_container)
        muteButton = findViewById(R.id.dialer_mute_button)
        endCallButton = findViewById(R.id.dialer_end_call_button)
        dialpadButton = findViewById(R.id.dialer_dialpad_button)

        contactImage = findViewById(R.id.dialer_contact_image)
        appIcon = findViewById(R.id.dialer_app_icon)

        displayNameText = findViewById(R.id.dialer_display_name)
        durationText = findViewById(R.id.dialer_duration)
    }

    fun setOnMuteClickListener(listener: OnClickListener) {
        muteButton.setOnClickListener(listener)
    }

    fun setOnEndCallClickListener(listener: OnClickListener) {
        endCallButton.setOnClickListener(listener)
    }

    fun setOnDialpadClickListener(listener: OnClickListener) {
        dialpadButton.setOnClickListener(listener)
    }

    fun updateAppIcon(icon: Drawable?) {
        val container = appIcon.parent as View
        if (icon != null) {
            appIcon.setImageDrawable(icon)
            container.visibility = View.VISIBLE
        } else {
            container.visibility = View.GONE
        }
    }

    fun updateAvatar(avatarUri: Uri?, initials: String?, identifier: String?) {
        val letterTileDrawable = createLetterTile(context, initials, identifier)
        if (avatarUri == null) {
            contactImage.setImageDrawable(letterTileDrawable)
            return
        }

        Glide.with(context.applicationContext)
            .load(avatarUri)
            .apply(
                RequestOptions()
                .placeholder(letterTileDrawable)
                .centerCrop()
                .error(letterTileDrawable)
            )
            .into(contactImage)
    }

    fun updateDisplay(displayName: String?, connectTimeMillis: Long?) {
        displayNameText?.text = displayName ?: ""
        if (connectTimeMillis != null) {
            durationText?.base = connectTimeMillis - System.currentTimeMillis() +
                SystemClock.elapsedRealtime()
            durationText?.start()
        } else {
            durationText?.stop()
            durationText?.text = ""
        }
    }

    fun updateAudioState(isMuted: Boolean) {
        muteButton.isSelected = isMuted
    }

    private var primaryActionClickListener: OnClickListener? = null

    fun setPrimaryActionClickListener(listener: OnClickListener) {
        primaryActionClickListener = listener
        updateClickListenerForOrientation()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateClickListenerForOrientation()
    }

    private fun updateClickListenerForOrientation() {
        val isPortrait = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val cardContainer = findViewById<View>(R.id.card_container)

        if (isPortrait) {
            profileContainer.setOnClickListener(null)
            profileContainer.isClickable = false
            profileContainer.isFocusable = false
            cardContainer?.isFocusable = true
            cardContainer?.setOnClickListener(primaryActionClickListener)
        } else {
            cardContainer?.setOnClickListener(null)
            cardContainer?.isClickable = false
            cardContainer?.isFocusable = false
            profileContainer.isFocusable = true
            profileContainer.setOnClickListener(primaryActionClickListener)
        }
    }

    companion object {
        private const val TAG = "MinimizedDialerControlsView"
    }
}
