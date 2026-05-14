package net.agolyakov.agoslider.service.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import javax.inject.Inject

class BluetoothAdapterProvider @Inject constructor(
    private val context: Context
) {
    fun getAdapter(): BluetoothAdapter {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    fun getContext(): Context {
        return context
    }
}