package com.filedroid.ssh

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshSessionManager @Inject constructor() {

    private val sessions = mutableMapOf<String, SshSession>()

    fun createSession(label: String): SshSession {
        val session = SshSession(id = UUID.randomUUID().toString(), label = label)
        sessions[session.id] = session
        return session
    }

    fun getSession(id: String): SshSession? = sessions[id]

    fun allSessions(): List<SshSession> = sessions.values.toList()

    fun closeSession(id: String) {
        sessions.remove(id)?.disconnect()
    }

    fun closeAll() {
        sessions.values.forEach { it.disconnect() }
        sessions.clear()
    }
}
