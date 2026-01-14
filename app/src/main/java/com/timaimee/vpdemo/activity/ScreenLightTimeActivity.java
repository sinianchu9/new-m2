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
import com.veepoo.protocol.listener.data.IScreenLightTimeListener;
import com.veepoo.protocol.model.datas.ScreenLightTimeData;
import com.veepoo.protocol.model.enums.EScreenLightTime;

import java.util.HashMap;
import java.util.Map;

public class ScreenLightTimeActivity extends AppCompatActivity {
    private final int[] mOptions = new int[] { 5, 10, 15, 20, 25, 30 };
    private final Map<Integer, TextView> mOptionViews = new HashMap<>();

    private TextView mRangeHint;
    private TextView mStatus;
    private TextView mConnectionHint;
    private Button mSaveButton;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;
    private boolean mIsSaving = false;
    private int mSelectedSeconds = 10;
    private int mMinDuration = 0;
    private int mMaxDuration = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_light_time);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        bindEvents();
        updateConnectionUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionState();
        requestRead();
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
        mRangeHint = findViewById(R.id.tv_range_hint);
        mStatus = findViewById(R.id.tv_screen_light_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
        mSaveButton = findViewById(R.id.btn_save);
        mOptionViews.put(5, findViewById(R.id.btn_time_5));
        mOptionViews.put(10, findViewById(R.id.btn_time_10));
        mOptionViews.put(15, findViewById(R.id.btn_time_15));
        mOptionViews.put(20, findViewById(R.id.btn_time_20));
        mOptionViews.put(25, findViewById(R.id.btn_time_25));
        mOptionViews.put(30, findViewById(R.id.btn_time_30));
    }

    private void bindEvents() {
        for (int option : mOptions) {
            TextView view = mOptionViews.get(option);
            if (view != null) {
                view.setOnClickListener(v -> onOptionSelected(option));
            }
        }
        mSaveButton.setOnClickListener(v -> saveSelection());
    }

    private void onOptionSelected(int seconds) {
        if (!isOptionEnabled(seconds)) {
            showMsg("当前设备不支持该时长");
            return;
        }
        mSelectedSeconds = seconds;
        updateOptionUI();
    }

    private void saveSelection() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        if (mIsSaving) {
            return;
        }
        if (!isOptionEnabled(mSelectedSeconds)) {
            showMsg("请选择设备支持的时长");
            return;
        }
        mIsSaving = true;
        updateStatus("状态：正在设置");
        updateSaveEnabled();
        VPOperateManager.getInstance().setScreenLightTime(mBleWriteResponse, mScreenLightTimeListener,
                mSelectedSeconds);
    }

    private void requestRead() {
        if (!mIsConnected) {
            updateStatus("状态：未连接");
            return;
        }
        updateStatus("状态：读取中");
        VPOperateManager.getInstance().readScreenLightTime(mBleWriteResponse, mScreenLightTimeListener);
    }

    private final IScreenLightTimeListener mScreenLightTimeListener = new IScreenLightTimeListener() {
        @Override
        public void onScreenLightTimeDataChange(ScreenLightTimeData screenLightTimeData) {
            if (screenLightTimeData == null) {
                updateStatus("状态：读取失败");
                mIsSaving = false;
                updateSaveEnabled();
                return;
            }
            EScreenLightTime state = screenLightTimeData.getScreenLightState();
            boolean isRead = state == EScreenLightTime.READ_SUCCESS || state == EScreenLightTime.READ_FAIL;
            boolean isSetting = state == EScreenLightTime.SETTING_SUCCESS || state == EScreenLightTime.SETTING_FAIL;
            if (state == EScreenLightTime.READ_FAIL) {
                updateStatus("状态：读取失败");
                mIsSaving = false;
                updateSaveEnabled();
                return;
            }
            if (state == EScreenLightTime.SETTING_FAIL) {
                updateStatus("状态：设置失败");
                mIsSaving = false;
                updateSaveEnabled();
                return;
            }
            if (isRead || isSetting) {
                mMinDuration = screenLightTimeData.getMinDuration();
                mMaxDuration = screenLightTimeData.getMaxDuration();
                mSelectedSeconds = screenLightTimeData.getCurrentDuration();
                if (!isOptionAvailable(mSelectedSeconds)) {
                    int recommend = screenLightTimeData.getRecommendDuration();
                    if (isOptionAvailable(recommend)) {
                        mSelectedSeconds = recommend;
                    } else {
                        mSelectedSeconds = findNearestOption();
                    }
                }
                updateRangeHint();
                updateOptionUI();
            }
            if (state == EScreenLightTime.SETTING_SUCCESS) {
                updateStatus("状态：设置成功");
                mIsSaving = false;
                updateSaveEnabled();
                finish();
            } else if (state == EScreenLightTime.READ_SUCCESS) {
                updateStatus("状态：已同步设备");
                mIsSaving = false;
                updateSaveEnabled();
            }
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                updateStatus("状态：指令发送失败");
                mIsSaving = false;
                updateSaveEnabled();
            }
        }
    };

    private void updateOptionUI() {
        for (int option : mOptions) {
            TextView view = mOptionViews.get(option);
            if (view == null) {
                continue;
            }
            boolean selected = option == mSelectedSeconds;
            view.setSelected(selected);
            int textColor = selected ? getResources().getColor(android.R.color.white)
                    : getResources().getColor(R.color.text_primary);
            view.setTextColor(textColor);
            boolean enabled = isOptionEnabled(option);
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    private void updateRangeHint() {
        if (mMinDuration > 0 && mMaxDuration > 0) {
            mRangeHint.setText("设备支持范围：" + mMinDuration + " - " + mMaxDuration + " 秒");
        } else {
            mRangeHint.setText("设备支持范围：--");
        }
    }

    private boolean isOptionEnabled(int seconds) {
        if (mMinDuration <= 0 || mMaxDuration <= 0) {
            return true;
        }
        return seconds >= mMinDuration && seconds <= mMaxDuration;
    }

    private boolean isOptionAvailable(int seconds) {
        for (int option : mOptions) {
            if (option == seconds) {
                return isOptionEnabled(option);
            }
        }
        return false;
    }

    private int findNearestOption() {
        int candidate = mOptions[0];
        for (int option : mOptions) {
            if (isOptionEnabled(option)) {
                candidate = option;
                break;
            }
        }
        return candidate;
    }

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
            updateSaveEnabled();
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
        updateSaveEnabled();
    }

    private void updateSaveEnabled() {
        mSaveButton.setEnabled(mIsConnected && !mIsSaving);
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
