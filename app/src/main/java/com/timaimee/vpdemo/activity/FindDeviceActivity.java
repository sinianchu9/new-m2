package com.timaimee.vpdemo.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.timaimee.vpdemo.R;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.IFindDevicelistener;

public class FindDeviceActivity extends AppCompatActivity {
    private Button mFindButton;
    private TextView mStatus;
    private TextView mConnectionHint;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;
    private boolean mIsFinding = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_device);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        bindEvents();
        updateConnectionUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionState();
    }

    @Override
    protected void onDestroy() {
        if (mDeviceAddress != null && mIsConnectListenerRegistered) {
            VPOperateManager.getInstance().unregisterConnectStatusListener(mDeviceAddress, mBleConnectStatusListener);
            mIsConnectListenerRegistered = false;
        }
        super.onDestroy();
    }

    private void initViews() {
        mFindButton = findViewById(R.id.btn_find_device);
        mStatus = findViewById(R.id.tv_find_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
    }

    private void bindEvents() {
        mFindButton.setOnClickListener(v -> triggerFindDevice());
    }

    private void triggerFindDevice() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        if (mIsFinding) {
            return;
        }
        mIsFinding = true;
        updateStatus("状态：正在寻找设备...");
        updateButtonState();
        VPOperateManager.getInstance().startFindDeviceByPhone(mBleWriteResponse, mFindListener);
    }

    private final IFindDevicelistener mFindListener = new IFindDevicelistener() {
        @Override
        public void unSupportFindDeviceByPhone() {
            mIsFinding = false;
            updateStatus("状态：设备不支持查找");
            updateButtonState();
        }

        @Override
        public void findedDevice() {
            mIsFinding = false;
            updateStatus("状态：已找到设备");
            updateButtonState();
        }

        @Override
        public void unFindDevice() {
            mIsFinding = false;
            updateStatus("状态：未找到设备");
            updateButtonState();
        }

        @Override
        public void findingDevice() {
            mIsFinding = true;
            updateStatus("状态：设备正在响应");
            updateButtonState();
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                mIsFinding = false;
                updateStatus("状态：指令发送失败");
                updateButtonState();
            }
        }
    };

    private void refreshConnectionState() {
        if (mDeviceAddress == null || mDeviceAddress.trim().isEmpty()) {
            mIsConnected = false;
            updateConnectionUI();
            return;
        }
        int status = VPOperateManager.getInstance().getConnectStatus(mDeviceAddress);
        mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
        if (!mIsConnectListenerRegistered) {
            VPOperateManager.getInstance().registerConnectStatusListener(mDeviceAddress, mBleConnectStatusListener);
            mIsConnectListenerRegistered = true;
        }
        updateConnectionUI();
    }

    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
            updateConnectionUI();
            updateButtonState();
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
    }

    private void updateButtonState() {
        boolean enabled = mIsConnected && !mIsFinding;
        mFindButton.setEnabled(enabled);
        mFindButton.setAlpha(enabled ? 1.0f : 0.6f);
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
