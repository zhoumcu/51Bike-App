package com.joshua.a51bike.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import com.joshua.a51bike.bluetooth.utils.Protocol;
import com.joshua.a51bike.util.UiUtils;

import java.util.List;
import java.util.UUID;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * class description here
 *  BEL manager 管理一切蓝牙工作
 * @version 1.0.0
 * @outher wangqiang
 * @project 51Bike
 * @since 2017-04-02
 */
public class BlueToothManager {
    private String TAG = "BlueToothManager";
    private Context context;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private static final int REQUEST_ENABLE_BT = 0x01;
    private String mDeviceAddress;
    private String mDeviceId;
    private static final String serviceUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String writeUuid = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String readUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    private static final byte startCommand = 0x01;
    private static final byte stopCommand = 0x02;
    public BlueToothManager(Context context) {
        this.context = context;
    }

    /**
     * 检查设备是否支持蓝牙
     * @return true 是支持
     */
    public boolean checkPhoneState() {
        // 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            UiUtils.showToast("手机不支持低功耗蓝牙");
            ((Activity) context).finish();

        }
        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        final BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // 检查设备上是否支持蓝牙
        if (mBluetoothAdapter == null) {
            UiUtils.showToast("手机不支持蓝牙");
            ((Activity) context).finish();
        }
        return true;
    }

    public void checkOpenBLE(){
        context.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        // 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ((Activity) context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }
    public void unRegisterRecevier(){
        context.unregisterReceiver(mGattUpdateReceiver);

    }
    public void UnbindService(){
        context.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
    /**
     * 连接ble设备
     */
    public boolean connect_ble() {
        mDeviceId = "E899B6C8A9B9000000001036";//扫码获取的
        mDeviceAddress = getDeviceAddress(mDeviceId);//
        Intent gattServiceIntent = new Intent(context, BluetoothLeService.class);
        Log.i(TAG, "connect_ble: bindService");
        context.bindService(gattServiceIntent, mServiceConnection,
                BIND_AUTO_CREATE);
        return  true;
    }


    /**
     * 将扫描到的设备id转换成设备MAC地址
     * 格式为 E8:99:B6:C8:A9:B9
     */
    private String getDeviceAddress(String deviceId) {
        String preAddress = deviceId.substring(0, 12);
        StringBuilder deviceAddress = new StringBuilder();
        for (int i = 0; i < preAddress.length(); i++) {
            deviceAddress.append(preAddress.substring(i, i++ + 2));
            if (i != preAddress.length() - 1) {
                deviceAddress.append(":");
            }
        }
        return deviceAddress.toString();
    }

    /**
     * 连接Service的接口
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected: ");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                UiUtils.showToast("蓝牙初始化失败");
                ((Activity) context).finish();

            }
            //链接Service成功，则通过该Service尝试连接蓝牙设备
            if (mBluetoothLeService.connect(mDeviceAddress)) {
                UiUtils.showToast("设备连接成功");

            }
            else
                UiUtils.showToast("设备连接失败！");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected: ");
            mBluetoothLeService = null;
        }
    };


    /**
     * 根据uuid获取Characteristic
     */
    private BluetoothGattCharacteristic getCharacteristic(String uuid) {
        List<BluetoothGattService> services = mBluetoothLeService
                .getSupportedGattServices();
        for (BluetoothGattService service : services) {
            if (service.getUuid().toString().equals(serviceUuid)) {
                return service.getCharacteristic(UUID.fromString(uuid));
            }
        }
        return null;
    }

    /**
     * 执行具体的通信任务
     * 数据分两次发送
     * 发送完毕后读取设备返回信息
     */
    private void executeBle(byte[] bytes) {
        byte[] pre_20 = new byte[20];
        System.arraycopy(bytes, 0, pre_20, 0, 20);
        byte[] after_5 = new byte[5];
        System.arraycopy(bytes, 20, after_5, 0, 5);
        BluetoothGattCharacteristic writeCharacteristic = getCharacteristic(writeUuid);
        writeCharacteristic.setValue(pre_20);
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mBluetoothLeService.wirteCharacteristic(writeCharacteristic);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        writeCharacteristic.setValue(after_5);
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mBluetoothLeService.wirteCharacteristic(writeCharacteristic);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BluetoothGattCharacteristic readCharacteristic = getCharacteristic(readUuid);
        mBluetoothLeService.setCharacteristicNotification(
                readCharacteristic, true);
        mBluetoothLeService.readCharacteristic(readCharacteristic);
    }

    /**
     * 开锁
     */
    public void startBike() {
        byte[] bytes = Protocol.getBytes(mDeviceId, startCommand);
        executeBle(bytes);
    }


    /**
     * 还车
     */
    public void returnBike() {
        byte[] bytes = Protocol.getBytes(mDeviceId, stopCommand);
        executeBle(bytes);
    }

    /**
     * 锁车
     */
    public void lockBike() {

    }


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
                    .equals(action)) {
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] resultBytes = intent.getByteArrayExtra("resultBytes");
                parseBytes(resultBytes);

            }
        }
    };

    private void parseBytes(byte[] resultBytes) {
        //功能码为5
        byte model=resultBytes[0];
        String str_model=model+"";
        //// TODO: 2017/3/29

        //开关机
        byte state=resultBytes[3];
        String str_state=state+"";
        if(str_state.equals("0")){
            UiUtils.showToast("成功还车");
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}
