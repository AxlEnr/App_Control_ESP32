package com.example.carritoapp;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // Configuración BLE
    private static final String TAG = "CarritoBLE";
    private static final String DEVICE_NAME = "MAKA";
    private static final UUID SERVICE_UUID = UUID.fromString("0000faf0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000faf1-0000-1000-8000-00805f9b34fb");

    // UI
    private Button btnAdelante, btnAtras, btnIzquierda, btnDerecha, btnAlto, btnLedOn, btnLedOff;

    // BLE
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bleGatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private Handler mainHandler;
    private boolean isConnected = false;
    private boolean isScanning = false;

    // Códigos de permisos
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        setupUI();
        checkBluetoothSupport();
    }

    private void setupUI() {
        btnAdelante = findViewById(R.id.btnAdelante);
        btnAtras = findViewById(R.id.btnAtras);
        btnIzquierda = findViewById(R.id.btnIzquierda);
        btnDerecha = findViewById(R.id.btnDerecha);
        btnAlto = findViewById(R.id.btnAlto);
        btnLedOn = findViewById(R.id.btnLedOn);
        btnLedOff = findViewById(R.id.btnLedOff);

        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(v -> connectToDevice());

        // Configurar listeners para los botones de control
        btnAdelante.setOnClickListener(v -> sendCommand("adelante"));
        btnAtras.setOnClickListener(v -> sendCommand("atras"));
        btnIzquierda.setOnClickListener(v -> sendCommand("izquierda"));
        btnDerecha.setOnClickListener(v -> sendCommand("derecha"));
        btnAlto.setOnClickListener(v -> sendCommand("alto"));
        btnLedOn.setOnClickListener(v -> sendCommand("led_on"));
        btnLedOff.setOnClickListener(v -> sendCommand("led_off"));
    }

    private void checkBluetoothSupport() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showToast("Este dispositivo no soporta Bluetooth");
            finish();
        }
    }

    private void connectToDevice() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
            return;
        }

        if (isConnected) {
            showToast("Ya está conectado");
            return;
        }

        startBleScan();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                    },
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    private void startBleScan() {
        if (isScanning) {
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            showToast("Error al iniciar escáner BLE");
            return;
        }

        showToast("Buscando dispositivo...");
        isScanning = true;

        // Configurar filtro para nuestro dispositivo
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(DEVICE_NAME)
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            bleScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar escaneo", e);
            showToast("Error al escanear");
            isScanning = false;
            return;
        }

        // Timeout de escaneo
        mainHandler.postDelayed(() -> {
            if (!isConnected && isScanning) {
                stopBleScan();
                showToast("Dispositivo no encontrado");
            }
        }, 10000); // 10 segundos
    }

    private void stopBleScan() {
        if (bleScanner != null && isScanning) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error al detener escaneo", e);
            }
            isScanning = false;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null && device.getName().equals(DEVICE_NAME)) {
                stopBleScan();
                connectToBleDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            isScanning = false;
            showToast("Escaneo fallido: " + errorCode);
        }
    };

    private void connectToBleDevice(BluetoothDevice device) {
        showToast("Conectando a " + device.getName() + "...");

        if (bleGatt != null) {
            bleGatt.close();
        }

        bleGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post(() -> showToast("Conectado, descubriendo servicios..."));
                if (!gatt.discoverServices()) {
                    showToast("Error al iniciar descubrimiento de servicios");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                mainHandler.post(() -> {
                    showToast("Desconectado");
                    reconnectDevice();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (commandCharacteristic != null) {
                        isConnected = true;
                        mainHandler.post(() -> showToast("Listo para controlar"));
                    } else {
                        mainHandler.post(() -> showToast("Característica no encontrada"));
                    }
                } else {
                    mainHandler.post(() -> showToast("Servicio no encontrado"));
                }
            } else {
                mainHandler.post(() -> showToast("Error al descubrir servicios"));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Error al escribir característica");
            }
        }
    };

    private void reconnectDevice() {
        mainHandler.postDelayed(() -> {
            if (!isConnected) {
                showToast("Intentando reconectar...");
                connectToDevice();
            }
        }, 3000); // Reintentar después de 3 segundos
    }

    private void sendCommand(String command) {
        if (!isConnected || commandCharacteristic == null || bleGatt == null) {
            showToast("No conectado al dispositivo");
            return;
        }

        if (!checkPermissions()) {
            requestPermissions();
            return;
        }

        commandCharacteristic.setValue(command);
        if (!bleGatt.writeCharacteristic(commandCharacteristic)) {
            showToast("Error al enviar comando");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                connectToDevice();
            } else {
                showToast("Bluetooth debe estar activado");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                showToast("Permisos necesarios no concedidos");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromDevice();
    }

    private void disconnectFromDevice() {
        if (bleGatt != null) {
            try {
                bleGatt.disconnect();
                bleGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error al desconectar", e);
            }
            bleGatt = null;
        }
        isConnected = false;
        stopBleScan();
    }
}