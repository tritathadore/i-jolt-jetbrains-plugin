package com.jolt.plugin.services

import com.intellij.util.messages.Topic

interface DaemonStatusListener {
    companion object {
        val TOPIC = Topic.create("Daemon Status Changes", DaemonStatusListener::class.java)
    }
    fun onStatusChanged(newStatus: DaemonStatus)
}
