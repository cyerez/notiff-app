package com.rez.notif.notif

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.rez.notif.NotifApp
import com.rez.notif.i18n.t
import com.rez.notif.ui.components.fa

/**
 * Quick-settings tile "Copy code": pull down the status bar, tap, and the newest login code
 * (from the last 30 minutes) is on the clipboard. Asks to unlock the phone first, since codes are private.
 */
class CodeTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val has = runCatching { NotifApp.repo(this).latestCode() }.getOrNull() != null
        tile.state = if (has) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = t("کپی کد")
        tile.subtitle = if (has) t("کد تازه داری") else t("کد تازه‌ای نیست")
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { copy() } else copy()
    }

    private fun copy() {
        val found = runCatching { NotifApp.repo(this).latestCode() }.getOrNull()
        if (found == null) {
            Toast.makeText(this, t("توی ۳۰ دقیقه اخیر کدی نیومده."), Toast.LENGTH_SHORT).show()
            return
        }
        CodeNotifier.copy(this, found.first)
        Toast.makeText(this, t("کد ") + fa(found.first) + t(" از ") + found.second + t(" کپی شد"), Toast.LENGTH_SHORT).show()
    }
}
