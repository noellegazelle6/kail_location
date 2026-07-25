package com.kail.locationxposed.xposed.bridge

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IKailXpBridge : IInterface {
    fun loadModule(dexPath: String, className: String, nativeLibDir: String): Boolean
    fun isModuleLoaded(): Boolean

    abstract class Stub : Binder(), IKailXpBridge {
        @Volatile
        private var _moduleLoaded = false

        override fun isModuleLoaded(): Boolean = _moduleLoaded

        final override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                1 -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = loadModule(
                        data.readString() ?: "",
                        data.readString() ?: "",
                        data.readString() ?: ""
                    )
                    if (result) _moduleLoaded = true
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                2 -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = isModuleLoaded()
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun asBinder(): IBinder = this

        companion object {
            const val DESCRIPTOR = "com.kail.location.xposed.bridge"

            fun asInterface(binder: IBinder): IKailXpBridge? {
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IKailXpBridge) return local
                return Proxy(binder)
            }
        }

        private class Proxy(private val remote: IBinder) : IKailXpBridge {
            override fun loadModule(dexPath: String, className: String, nativeLibDir: String): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(dexPath)
                    data.writeString(className)
                    data.writeString(nativeLibDir)
                    remote.transact(1, data, reply, 0)
                    reply.readException()
                    return reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun isModuleLoaded(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(2, data, reply, 0)
                    reply.readException()
                    return reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun asBinder(): IBinder = remote
        }
    }
}
