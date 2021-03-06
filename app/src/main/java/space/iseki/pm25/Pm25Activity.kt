package space.iseki.pm25

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pm25.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes


private val neededPermissions =
    arrayOf("android.permission.BLUETOOTH", "android.permission.ACCESS_FINE_LOCATION")


class Pm25Activity : AppCompatActivity() {
    private val bleManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bleAdapter by lazy { bleManager.adapter!! }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val scanner by lazy { BleScanner(this, bleAdapter) }
    private val bgService: SensorBackgroundService
        get() = serviceConnection.service

    private val serviceConnection =
        localServiceConnection<SensorBackgroundService, SensorBackgroundService.LocalBinder>()

    private val mainList by lazy {
        DeviceListAdapter(this).apply {
            this.userActionHandler = { action, update ->
                val address = update.info.address
                when (action) {
                    DeviceListAdapter.UserAction.Connect -> bgService.connectDevice(address)
                    DeviceListAdapter.UserAction.Disconnect -> bgService.disconnectDevice(address)
                    DeviceListAdapter.UserAction.SetFreq -> showNotImplement()
                    DeviceListAdapter.UserAction.Remove -> showNotImplement()
                    DeviceListAdapter.UserAction.Shutdown -> bgService.writeCommandToDevice(
                        ShutdownCommand,
                        address
                    )
                    else -> showNotImplement()
                }
            }
        }
    }

    private val updateWatcher = { update: DeviceStatusUpdate ->
        mainList.processUpdate(update)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCenter.start(
            application, "9e060c4a-9366-4232-a03c-30ef47af6fdb",
            Analytics::class.java, Crashes::class.java
        )
    }

    // TODO: refactor WTF???
    private var canceler = {}

    override fun onStart() {
        super.onStart()
        setContentView(R.layout.pm25_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener(::clickAddButton)
        findViewById<RecyclerView>(R.id.dlist).apply {
            setHasFixedSize(false)
            layoutManager = LinearLayoutManager(this@Pm25Activity)
            adapter = mainList
        }

        startService(Intent(this, SensorBackgroundService::class.java))
        if (!serviceConnection.isBind)
            bindBackgroundService()
        serviceConnection.onceBind {
            refreshDeviceList()
            canceler = bgService.watch(updateWatcher)
        }
    }

    override fun onStop() {
        super.onStop()
        canceler.invoke()
        if (serviceConnection.isBind)
            unbindService(serviceConnection)
    }

    private fun addDevice(device: BluetoothDevice) {
        bgService.addDevice(
            SensorDeviceInfo(
                address = device.address,
                name = device.name ?: "<no name>",
            )
        )
        refreshDeviceList()
        bgService.connectDevice(device.address)
    }

    private fun refreshDeviceList() {
        val l = bgService.deviceInfos
        println(l)
        mainList.sensors = l
    }


    private fun clickAddButton(view: View) {
        if (!(ensureBTEnabled() && ensureLocationEnabled() && checkNeededPermission())) return
        scanner.doScan()
        AlertDialog.Builder(this)
            .setTitle("Select your device")
            .setAdapter(scanner.viewAdapter) { _, i ->
                // TODO: ugly code
                scanner.stopScan()
                val device = scanner.viewAdapter.getItem(i)
                val address = device.address
                val name = device.name ?: "<no name>"
                Snackbar.make(
                    findViewById(R.id.root_view),
                    "selected: $name ($address)",
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                addDevice(device)
            }
            .setOnCancelListener { scanner.stopScan() }
            .show()
    }


    private fun ensureBTEnabled(): Boolean {
        if (bleAdapter.isEnabled) return true
        AlertDialog.Builder(this)
            .setMessage("Bluetooth is required.")
            .setPositiveButton("Ok") { _, _ -> startBluetoothActivity() }
            .show()
        return false
    }

    private fun ensureLocationEnabled(): Boolean {
        if (locationManager.isEnable) return true
        AlertDialog.Builder(this)
            .setMessage("Location is required.")
            .setPositiveButton("Ok") { _, _ -> startLocationActivity() }
            .show()
        return false
    }

    private fun checkNeededPermission(): Boolean {
        if (checkPermissions(this, *neededPermissions)) return true
        AlertDialog.Builder(this).setMessage("Location and Bluetooth permission is required.")
            .setPositiveButton("Ok") { _, _ ->
                requestPermissions(neededPermissions, 1)
            }
            .show()
        return false
    }

    private fun bindBackgroundService() {
        bindService(
            Intent(this, SensorBackgroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
}

fun Context.showNotImplement() {
    Toast.makeText(this, "Not Implement.", Toast.LENGTH_SHORT).show()
}

