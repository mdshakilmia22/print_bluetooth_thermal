package app.web.groons.print_bluetooth_thermal

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*

private const val TAG = "====> print: "
private var outputStream: OutputStream? = null
private lateinit var mac: String

class PrintBluetoothThermalPlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var mContext: Context
  private lateinit var channel : MethodChannel
  private var state:Boolean = false

  private val myPermissionCode = 34264
  private var activeResult: Result? = null
  private var permissionGranted: Boolean = false

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "groons.web.app/print")
    channel.setMethodCallHandler(this)
    this.mContext = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    var sdkversion:Int = Build.VERSION.SDK_INT
    var androidVersion:String = android.os.Build.VERSION.RELEASE
    activeResult = result
    permissionGranted = ContextCompat.checkSelfPermission(mContext,Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    when (call.method) {
      "ispermissionbluetoothgranted" -> {
        var permission: Boolean = true
        if(sdkversion >= 31){
          permission = permissionGranted
        }
        result.success(permission)
      }
      "getPlatformVersion" -> {
        result.success("Android $androidVersion")
      }
      "getBatteryLevel" -> {
        val batteryLevel = getBatteryLevel()
        if (batteryLevel != -1) {
          result.success(batteryLevel)
        } else {
          result.error("UNAVAILABLE", "Battery level not available.", null)
        }
      }
      "bluetoothenabled" -> {
        var state:Boolean = false
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
          state = true
        }
        result.success(state)
      }
      "connectionstatus" -> {
        if(outputStream != null) {
          try{
            outputStream?.run {
              write(" ".toByteArray())
              result.success(true)
            }
          }catch (e: Exception){
            result.success(false)
            outputStream = null
          }
        }else{
          result.success(false)
        }
      }
      "connect" -> {
        var macimpresora = call.arguments.toString()
        if(macimpresora.length>0){
          mac = macimpresora
        }else{
          result.success(false)
        }
        GlobalScope.launch(Dispatchers.Main) {
          if(outputStream == null) {
            outputStream = connect()?.also {
              result.success(state)
            }
          }else{
            result.success(false)
          }
        }
      }
      "writebytes" -> {
        var lista: List<Int> = call.arguments as List<Int>
        var bytes: ByteArray = "\n".toByteArray()

        lista.forEach {
          bytes += it.toByte()
        }
        if(outputStream != null) {
          try{
            outputStream?.run {
              write(bytes)
              result.success(true)
            }
          }catch (e: Exception){
            result.success(false)
            outputStream = null
            Log.d(TAG, "error state print: ${e.message}")
          }
        }else{
          result.success(false)
        }
      }
      "printstring" -> {
        var stringllego: String = call.arguments.toString()
        if(outputStream != null) {
          try{
            var size:Int = 0
            var texto:String = ""
            var linea = stringllego.split("///")
            if(linea.size>1) {
              size = linea[0].toInt()
              texto = linea[1]
              if (size < 1 || size > 5) size = 2
            }else{
              size = 2
              texto = stringllego
            }

            val charset = Charsets.UTF_8
            val byteArray = texto.toByteArray(charset)

            outputStream?.run {
              write(setBytes.size[0])
              write(setBytes.cancelar_chino)
              write(setBytes.caracteres_escape)
              write(setBytes.size[size])
              write(texto.toByteArray(charset("ISO-8859-1")))
              result.success(true)
            }
          }catch (e: Exception){
            result.success(false)
            outputStream = null
          }
        }else{
          result.success("false")
        }
      }
      "writebytesChinese" -> {
        var lista: List<Int> = call.arguments as List<Int>
        var bytes: ByteArray = "\n".toByteArray()

        lista.forEach {
          bytes += it.toByte()
        }
        if(outputStream != null) {
          try{
            outputStream?.run {
              write(bytes)
              result.success(true)
            }
          }catch (e: Exception){
            result.success(false)
            outputStream = null
          }
        }else{
          result.success(false)
        }
      }
      "pairedbluetooths" -> {
        var lista:List<String> = dispositivosVinculados()
        result.success(lista)
      }
      "disconnect" -> {
        if(outputStream != null){
          outputStream?.close()
          outputStream = null
          result.success(true)
        }else{
          result.success(true)
        }
      }
      else -> {
        result.notImplemented()
      }
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
    state = false
    return withContext(Dispatchers.IO) {
      var outputStream: OutputStream? = null
      val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
      if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
        try {
          val bluetoothAddress = mac
          val bluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetoothAddress)
          val bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
          )
          bluetoothAdapter.cancelDiscovery()
          bluetoothSocket?.connect()
          if (bluetoothSocket!!.isConnected) {
            outputStream = bluetoothSocket!!.outputStream
            state = true
          }else{
            state = false
            Log.d(TAG, "Desconectado: ")
          }
        } catch (e: Exception){
          state = false
          var code:Int = e.hashCode()
          Log.d(TAG, "connect: ${e.message} code $code")
          outputStream?.close()
        }
      }else{
        state = false
        Log.d(TAG, "Problema adapter: ")
      }
      outputStream
    }
  }

  private fun dispositivosVinculados():List<String>{
    val listItems: MutableList<String> = mutableListOf()
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null) {
      //lblmsj.setText("Esta aplicacion necesita de un telefono con bluetooth")
    }
    if (bluetoothAdapter?.isEnabled == false) {
      //mensajeToast("Bluetooth off")
    }
    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
    pairedDevices?.forEach { device ->
      val deviceName = device.name
      val deviceHardwareAddress = device.address
      listItems.add("$deviceName#$deviceHardwareAddress")
    }

    return listItems
  }

  private fun mensajeToast(mensaje: String){
    Toast.makeText(mContext, mensaje, Toast.LENGTH_SHORT).show()
  }

  class setBytes(){
    companion object {
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
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
