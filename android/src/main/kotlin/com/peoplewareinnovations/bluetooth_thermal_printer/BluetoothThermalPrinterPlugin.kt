package com.peoplewareinnovations.bluetooth_thermal_printer

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*
import androidx.annotation.NonNull
import android.bluetooth.BluetoothSocket
import java.io.IOException
import kotlinx.coroutines.delay


private const val TAG = "====> mio: "
private var outputStream: OutputStream? = null
private lateinit var mac: String
//val REQUEST_ENABLE_BT = 2

class BluetoothThermalPrinterPlugin: FlutterPlugin, MethodCallHandler{
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var mContext: Context
  private lateinit var channel : MethodChannel
  private lateinit var state:String

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "bluetooth_thermal_printer")
    channel.setMethodCallHandler(this)
    this.mContext = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    }else if (call.method == "getBatteryLevel") {
      val batteryLevel = getBatteryLevel()
      if (batteryLevel != -1) {
        result.success(batteryLevel)
      } else {
        result.error("UNAVAILABLE", "Battery level not available.", null)
      }
    }else if (call.method == "BluetoothStatus") {
      var state:String = "false"
      val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
      if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
        state = "true"
      }
      result.success(state)
    }else if (call.method == "connectionStatus") {

      if(outputStream != null) {
        try{
          outputStream?.run {
            write(" ".toByteArray())
            result.success("true")
            //Log.d(TAG, "step yes connection")
          }
        }catch (e: Exception){
          result.success("false")
          outputStream = null
          //Log.d(TAG, "state print: ${e.message}")
        }
      }else{
        result.success("false")
        //Log.d(TAG, "no paso es false ")
      }

    } else if (call.method == "connectPrinter") {
      val printerMAC = call.arguments.toString()
      if (printerMAC.isEmpty()) {
        result.success("false")
        return
      }
      mac = printerMAC

      if (outputStream != null) {
        result.success("already_connected")
        return
      }

      CoroutineScope(Dispatchers.Main).launch {
        try {
          outputStream = connect()
          if (outputStream != null) {
            result.success("connected")
          } else {
            result.success("connection_failed")
          }
        } catch (e: Exception) {
          result.error("CONNECT_ERROR", e.message, null)
        }
      }
      } else if (call.method == "disconnectPrinter") {
        if (outputStream == null) {
          result.success("already_disconnected")
          return
        }
      
        CoroutineScope(Dispatchers.Main).launch {
          try {
            val wasDisconnected = disconnect()
            if (wasDisconnected) {
              result.success("disconnected")
            } else {
              result.success("disconnect_failed")
            }
          } catch (e: Exception) {
            result.error("DISCONNECT_ERROR", e.message, null)
          }
        }
     } else if (call.method == "writeBytes") {

      var lista: List<Int> = call.arguments as List<Int>
      var bytes: ByteArray = "\n".toByteArray()

      lista.forEach {
        bytes += it.toByte() //Log.d(TAG, "foreach: ${it}")
      }
      if(outputStream != null) {
        try{
          outputStream?.run {
            write(bytes)
            result.success("true")
          }
        }catch (e: Exception){
          result.success("false")
          outputStream = null
          // Log.d(TAG, "state print: ${e.message}")
          /*var ex:String = e.message.toString()
          if(ex=="Broken pipe"){
            Log.d(TAG, "Device was disconnected, reconnect")
            ShowToast("Device was disconnected, reconnect")
          }*/
        }
      }else{
        result.success("false")
      }

    }else if (call.method == "printText") {

      var stringArrived: String = call.arguments.toString()
      //var list = stringArrived.split("*")
      //println("list ${list.toString()}")

      if(outputStream != null) {
        try{
          var size:Int = 0
          var texto:String = ""
          var line = stringArrived.split("//")
          //Log.d(TAG, "list arrived: ${line.size}")
          if(line.size>1) {
            size = line[0].toInt()
            texto = line[1]
            if (size < 1 || size > 5) size = 2
          }else{
            size = 2
            texto = stringArrived
            //Log.d(TAG, "list came 2 text: ${texto} size: $size")
          }

          outputStream?.run {
            write(setBytes.size[0])
            write(setBytes.cancelar_chino)
            write(setBytes.caracteres_escape)
            write(setBytes.size[size])
            write(texto.toByteArray(charset("iso-8859-1")))
            result.success("true")
          }
        }catch (e: Exception){
          result.success("false")
          outputStream = null
        }
      }else{
        result.success("false")
      }

    }else if (call.method == "bluetothLinked") {

      var list:List<String> = getLinkedDevices()

      result.success(list)

    }else {
      result.notImplemented()
    }
  }

  private fun getBatteryLevel(): Int {
    val batteryLevel: Int
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val batteryManager = mContext?.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
      batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } else {
      val intent = ContextWrapper(mContext?.applicationContext).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      batteryLevel = intent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    }

    return batteryLevel
  }


  private suspend fun connect(): OutputStream? {
      state = "false"
      return withContext(Dispatchers.IO) {
          val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
          if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
              Log.d(TAG, "Adapter not available or disabled")
              return@withContext null
          }
  
          val device = bluetoothAdapter.getRemoteDevice(mac)
          bluetoothAdapter.cancelDiscovery()
          delay(500) // Let discovery fully cancel
  
          // First attempt: standard UUID connection
          try {
              val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
              val socket = device.createRfcommSocketToServiceRecord(uuid)
              socket.connect()
              if (socket.isConnected) {
                  Log.d(TAG, "Connected via UUID")
                  state = "true"
                  return@withContext socket.outputStream
              }
          } catch (e: IOException) {
              Log.e(TAG, "UUID connect failed: ${e.message}")
          }
  
          // Fallback: Try channel 2 (commonly used by these printers)
          try {
              val fallbackSocket = device.javaClass
                  .getMethod("createRfcommSocket", Int::class.java)
                  .invoke(device, 2) as BluetoothSocket
              fallbackSocket.connect()
              if (fallbackSocket.isConnected) {
                  Log.d(TAG, "Connected via channel 2 fallback")
                  state = "true"
                  return@withContext fallbackSocket.outputStream
              }
          } catch (e: Exception) {
              Log.e(TAG, "Fallback connect failed: ${e.message}", e)
          }
  
          state = "false"
          null
      }
  }


  private suspend fun disconnect(): Boolean {
    state = "false"
    return withContext(Dispatchers.IO) {
      try {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
          if (mac.isNotEmpty()) {
            val bluetoothDevice = bluetoothAdapter.getRemoteDevice(mac)
            val bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(
              UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            bluetoothSocket.close()
          }
          outputStream?.close()
          outputStream = null
          Log.d(TAG, "Disconnected")
          true
        } else {
          Log.d(TAG, "Adapter problem")
          outputStream = null
          false
        }
      } catch (e: Exception) {
        Log.d(TAG, "disconnect: ${e.message}")
        outputStream?.close()
        outputStream = null
        false
      }
    }
  }

  private fun getLinkedDevices():List<String>{

    val listItems: MutableList<String> = mutableListOf()

    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null) {
      //lblmsj.setText("This application needs a phone with bluetooth")
    }
    //if blue tooth is not on
    if (bluetoothAdapter?.isEnabled == false) {
      //val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
      //ShowToast("Bluetooth off")
    }
    //search bluetooth
    //Log.d(TAG, "searching for devices: ")
    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
    pairedDevices?.forEach { device ->
      val deviceName = device.name
      val deviceHardwareAddress = device.address
      listItems.add("$deviceName#$deviceHardwareAddress")
      //Log.d(TAG, "device: ${device.name}")
    }

    return listItems;
  }


  class setBytes(){
    companion object {
      //val info = "This is info"
      //fun getMoreInfo():String { return "This is more fun" }

      val enter = "\n".toByteArray()
      val resetear_impresora = byteArrayOf(0x1b, 0x40, 0x0a)
      val cancelar_chino = byteArrayOf(0x1C, 0x2E)
      val caracteres_escape = byteArrayOf(0x1B, 0x74, 0x10)

      val size = arrayOf(
              byteArrayOf(0x1d, 0x21, 0x00), // La fuente no se agranda 0
              byteArrayOf(0x1b, 0x4d, 0x01), // Fuente ASCII comprimida 1
              byteArrayOf(0x1b, 0x4d, 0x00), //Fuente estándar ASCII    2
              byteArrayOf(0x1d, 0x21, 0x11), // Altura doblada 3
              byteArrayOf(0x1d, 0x21, 0x22), // Altura doblada 4
              byteArrayOf(0x1d, 0x21, 0x33) // Altura doblada 5
      )


      //deprecated codes
      const val HT: Byte = 9
      const val LF: Byte = 10
      const val CR: Byte = 13
      const val ESC: Byte = 27
      const val DLE: Byte = 16
      const val GS: Byte = 29
      const val FS: Byte = 28
      const val STX: Byte = 2
      const val US: Byte = 31
      const val CAN: Byte = 24
      const val CLR: Byte = 12
      const val EOT: Byte = 4
      val INIT = byteArrayOf(27, 64)
      var FEED_LINE = byteArrayOf(10)
      var SELECT_FONT_A = byteArrayOf(20, 33, 0)
      var SET_BAR_CODE_HEIGHT = byteArrayOf(29, 104, 100)
      var PRINT_BAR_CODE_1 = byteArrayOf(29, 107, 2)
      var SEND_NULL_BYTE = byteArrayOf(0)
      var SELECT_PRINT_SHEET = byteArrayOf(27, 99, 48, 2)
      var FEED_PAPER_AND_CUT = byteArrayOf(29, 86, 66, 0)
      var SELECT_CYRILLIC_CHARACTER_CODE_TABLE = byteArrayOf(27, 116, 17)
      var SELECT_BIT_IMAGE_MODE = byteArrayOf(27, 42, 33, -128, 0)
      var SET_LINE_SPACING_24 = byteArrayOf(27, 51, 24)
      var SET_LINE_SPACING_30 = byteArrayOf(27, 51, 30)
      var TRANSMIT_DLE_PRINTER_STATUS = byteArrayOf(16, 4, 1)
      var TRANSMIT_DLE_OFFLINE_PRINTER_STATUS = byteArrayOf(16, 4, 2)
      var TRANSMIT_DLE_ERROR_STATUS = byteArrayOf(16, 4, 3)
      var TRANSMIT_DLE_ROLL_PAPER_SENSOR_STATUS = byteArrayOf(16, 4, 4)
      val ESC_FONT_COLOR_DEFAULT = byteArrayOf(27, 114, 0)
      val FS_FONT_ALIGN = byteArrayOf(28, 33, 1, 27, 33, 1)
      val ESC_ALIGN_LEFT = byteArrayOf(27, 97, 0)
      val ESC_ALIGN_RIGHT = byteArrayOf(27, 97, 2)
      val ESC_ALIGN_CENTER = byteArrayOf(27, 97, 1)
      val ESC_CANCEL_BOLD = byteArrayOf(27, 69, 0)
      val ESC_HORIZONTAL_CENTERS = byteArrayOf(27, 68, 20, 28, 0)
      val ESC_CANCLE_HORIZONTAL_CENTERS = byteArrayOf(27, 68, 0)
      val ESC_ENTER = byteArrayOf(27, 74, 64)
      val PRINTE_TEST = byteArrayOf(29, 40, 65)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
