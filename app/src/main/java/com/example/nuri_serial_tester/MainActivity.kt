package com.example.nuri_serial_tester

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.balsikandar.crashreporter.CrashReporter
import com.example.nuri_serial_tester.databinding.ActivityMainBinding
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import org.apache.commons.codec.binary.Hex;

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private val list_of_baudrate = arrayOf(4800, 9600, 19200, 28800, 38400, 57600, 76800, 115200, 128000, 153600, 230400, 250000, 256000, 460800, 500000, 921600, 1000000)
    private lateinit var availableDrivers: List<UsbSerialDriver>
    private lateinit var driver_1: UsbSerialDriver
    private lateinit var driver_2: UsbSerialDriver
    private var port_1: UsbSerialPort? = null
    private var port_2: UsbSerialPort? = null
    private lateinit var m_usbManager: UsbManager
    private var connection_1: UsbDeviceConnection? = null
    private var connection_2: UsbDeviceConnection? = null
    private var serialThread: Thread? = null
    private val READ_WAIT_MILLIS = 1000
    private val WRITE_WAIT_MILLIS = 1000
    private val THREAD_SLEEP_MILLIS = arrayOf(210, 110, 60, 40, 30, 20, 15, 10, 8, 7, 5, 5, 5, 3, 3, 2, 2)
    private var MtoSsendCount: Int = 0
    private var StoMsendCount: Int = 0
    private var M_receiveCount: Int = 0
    private var S_receiveCount: Int = 0
    private var M_errorCount: Int = 0
    private var S_errorCount: Int = 0
    private var SelectId: Int = 0
    private val MASTER_to_SLAVE: Int = 1
    private val SLAVE_to_MASTER: Int = 2
    private var usbIOManager: SerialInputOutputManager? = null
    private val FONT_SIZE = 10
    private var container: ScrollView? = null
    private var SelectSleepMillis : Long = 210
    private val parser = SerialProtocol()
    private var dataSize: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.initialize(this)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        setSpinner()

        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)


        mBinding.serialconnectBtn.setOnClickListener {
            UsbConnecting()
        }
        mBinding.sendDataBtn.setOnClickListener {
            StratThread()
        }
        mBinding.serialdisconnectBtn.setOnClickListener {
            disconnect()
        }
        mBinding.sendDataCancelbtn.setOnClickListener {
            cancelSend()
        }
    }

    private fun setSpinner() {
        var arrayAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                list_of_baudrate
        )

        mBinding.spinnerBaudrate.adapter = arrayAdapter
        mBinding.spinnerBaudrate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                    adapterView: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
            ) {//스피너가 선택 되었을때
                creatTextview("Baud Rate : "+list_of_baudrate[position]+"선택했습니다.")
                SelectSleepMillis = THREAD_SLEEP_MILLIS[position].toLong()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {
                Toast.makeText(this@MainActivity, "Baud Rate를 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun UsbConnecting() {
        MtoSsendCount = 0
        StoMsendCount = 0
        M_errorCount = 0
        M_receiveCount = 0
        S_errorCount = 0
        S_receiveCount = 0

        val usbDevice: UsbDevice? = null
        availableDrivers = emptyList()
        // 사용 가능한 Usb diver 찾기
        m_usbManager = getSystemService(USB_SERVICE) as UsbManager

        //시리얼 통신가능한 포트만 search
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(m_usbManager)

        try {
            // 연결가능한 usb드라이버 열기
            driver_1 = availableDrivers[0]
            driver_2 = availableDrivers[1]

            //serial permission 요청하기
            val intent: PendingIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
            m_usbManager.requestPermission(availableDrivers[0].device, intent)

/*            val intent2: PendingIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
            m_usbManager.requestPermission(availableDrivers[1].device, intent2)*/
        }catch (e: Exception){
            creatTextview("USB Serial Port를 연결해주세요.")
        }
    }

    private val ACTION_USB_PERMISSION = "com.jeongmin.serial_nuri_tester.USB_PERMISSION"
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent?.action!! == ACTION_USB_PERMISSION) {
                val granted: Boolean =
                        intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                val selectBaudRate = mBinding.spinnerBaudrate.selectedItem

                if (granted) {

                    if (m_usbManager.hasPermission(driver_1.device) && port_1 == null) {
                        connection_1 = m_usbManager.openDevice(driver_1.device)
                        port_1 = driver_1.ports[0]
                        port_1!!.open(connection_1)
                        port_1!!.setParameters(
                                selectBaudRate as Int,
                                8,
                                UsbSerialPort.STOPBITS_1,
                                UsbSerialPort.PARITY_NONE
                        )
                        creatTextview("port1 : $port_1 정상 연결되었습니다.")
                        val intent2: PendingIntent =
                            PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
                        m_usbManager.requestPermission(availableDrivers[1].device, intent2)
                    }

                    if (m_usbManager.hasPermission(driver_2.device) && port_2 == null) {
                        connection_2 = m_usbManager.openDevice(driver_2.device)
                        port_2 = driver_2.ports[0]
                        port_2!!.open(connection_2)
                        port_2!!.setParameters(
                                selectBaudRate as Int,
                                8,
                                UsbSerialPort.STOPBITS_1,
                                UsbSerialPort.PARITY_NONE
                        )
                        creatTextview("port2 : $port_2 정상 연결되었습니다.")
                    }

                }
            } else if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
                UsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED) {
                creatTextview("Serial port가 연결되지 않았습니다.")
                disconnect()
            }
        }
    }

    private fun StratThread() {
        if (port_1 == null || port_2 == null)
            return

        serialThread = Thread {
            var data = parser.BuzzerOn(SelectId.toByte())
            dataSize = parser.getDataSize()
            port_2!!.purgeHwBuffers(true, true)
            port_1!!.purgeHwBuffers(true, true)
            while (true) {
                try {
                        sendData(MASTER_to_SLAVE, data!!)
                        Thread.sleep(SelectSleepMillis)
                        readData(MASTER_to_SLAVE)
                        Thread.sleep(SelectSleepMillis)
                        sendData(SLAVE_to_MASTER, data!!)
                        Thread.sleep(SelectSleepMillis)
                        readData(SLAVE_to_MASTER)
                        Thread.sleep(SelectSleepMillis)
                } catch (th: InterruptedException){
                  break
                } catch (e: Exception) {
                    Log.d("Error", "========================================")
                }
            }
        }
        serialThread?.start()
    }

    private fun sendData(port_index: Int, data: ByteArray) {

        when (port_index) {
            1 -> {
 //               port_1!!.purgeHwBuffers(true, true)
                port_1!!.write(data, WRITE_WAIT_MILLIS)
                MtoSsendCount++
                runOnUiThread {
                    mBinding.converter1RxBtn.setBackgroundResource(R.drawable.round_btn_off)
                    mBinding.converter1TxBtn.setBackgroundResource(R.drawable.round_btn_on)
                    mBinding.converter1TxTv.text = MtoSsendCount.toString()
                }
            }
            2 -> {
//                port_2!!.purgeHwBuffers(true, true)
                port_2!!.write(data, WRITE_WAIT_MILLIS)
                StoMsendCount++
                runOnUiThread {
                    mBinding.converter2RxBtn.setBackgroundResource(R.drawable.round_btn_off)
                    mBinding.converter2TxBtn.setBackgroundResource(R.drawable.round_btn_on)
                    mBinding.converter2TxTv.text = StoMsendCount.toString()
                }
            }
        }
    }

    private fun readData(port_index: Int = 0, retry: Int = 0) {
        val buff: ByteArray = ByteArray(1024)
        try {
            when (port_index) {
                1 -> {
                    val cnt = port_2!!.read(buff, READ_WAIT_MILLIS)
                    if (cnt < dataSize) {
/*                        return if (retry < 5)
                            readData(MASTER_to_SLAVE, retry + 1)
                        else*/
                        S_errorCount++
                        runOnUiThread {
                            mBinding.converter2RxTv.text = S_receiveCount.toString()
                            mBinding.converter2ErrorTv.text = S_errorCount.toString()
                            mBinding.converter2RxBtn.setBackgroundResource(R.drawable.round_btn_on)
                            mBinding.converter1TxBtn.setBackgroundResource(R.drawable.round_btn_off)
                        }
                        Log.d("checkSum 1 -> 2", "false")
//                        return null
                        return
                    }
                    S_receiveCount++
                    runOnUiThread {
                        mBinding.converter2RxBtn.setBackgroundResource(R.drawable.round_btn_on)
                        mBinding.converter1TxBtn.setBackgroundResource(R.drawable.round_btn_off)
                        mBinding.converter2RxTv.text = S_receiveCount.toString()
                    }
                    val recvData: ByteArray = ByteArray(cnt)
                    buff.copyInto(recvData, endIndex = cnt)
                    receive(recvData, port_index)
//                    return recvData
                }
                2 -> {
                    val cnt = port_1!!.read(buff, READ_WAIT_MILLIS)
                    if (cnt < dataSize) {
/*                        return if (retry < 5)
                            readData(SLAVE_to_MASTER, retry + 1)
                        else*/
                        M_errorCount++
                        runOnUiThread {
                            mBinding.converter1RxTv.text = M_receiveCount.toString()
                            mBinding.converter1ErrorTv.text = M_errorCount.toString()
                            mBinding.converter1RxBtn.setBackgroundResource(R.drawable.round_btn_on)
                            mBinding.converter2TxBtn.setBackgroundResource(R.drawable.round_btn_off)
                        }
                        Log.d("checkSum 2 -> 1", "false")
//                        return null
                        return
                    }
                    M_receiveCount++
                    runOnUiThread {
                        mBinding.converter1RxBtn.setBackgroundResource(R.drawable.round_btn_on)
                        mBinding.converter2TxBtn.setBackgroundResource(R.drawable.round_btn_off)
                        mBinding.converter1RxTv.text = M_receiveCount.toString()
                    }
                    val recvData: ByteArray = ByteArray(cnt)
                    buff.copyInto(recvData, endIndex = cnt)
                    receive(recvData, port_index)
//                    return recvData
                }
            }
        } catch (e: NumberFormatException) {
//            return readData()
        }
//        return null
    }

    private fun receive(data: ByteArray, port_index: Int) {
//        val pars = SerialProtocol()
        if (!parser.parse(data)) {
            if (port_index == 1) {
                S_errorCount++
                Log.d("checkSum 1 -> 2", "false")
            } else if (port_index == 2) {
                M_errorCount++
                Log.d("checkSum 2 -> 1", "false")
            }
        }

        runOnUiThread {
            mBinding.converter2ErrorTv.text = S_errorCount.toString()
            mBinding.converter1ErrorTv.text = M_errorCount.toString()
        }
    }

    fun creatTextview(text: String?) {
        //TextView 생성
        val addTextView = TextView(this)
        addTextView.text = text
        addTextView.textSize = FONT_SIZE.toFloat()
        addTextView.setTextColor(Color.BLACK)
        //layout_width, layout_height, gravity 설정
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        //addTextView.layoutParams

        runOnUiThread {
            //부모 뷰에 추가
            mBinding.textViewer.addView(addTextView)
        }
    }
    private fun disconnect() {
        try {
            port_1?.close()
            port_2?.close()
        } catch (e: IOException) {
        }
        port_1 = null
        port_2 = null
        mBinding.textViewer.removeAllViews()
        creatTextview("Serial port가 연결을 해제하였습니다.")
    }

    private fun cancelSend() {
        serialThread?.interrupt()
        creatTextview("데이터 보내기를 취소하셨습니다.")
    }

    override fun onDestroy() {
        serialThread?.interrupt()
        disconnect()
        super.onDestroy()
    }
}
