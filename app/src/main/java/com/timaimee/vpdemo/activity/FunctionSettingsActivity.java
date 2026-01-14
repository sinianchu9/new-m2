package com.timaimee.vpdemo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.timaimee.vpdemo.R;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.INightTurnWristeDataListener;
import com.veepoo.protocol.listener.data.IScreenStyleListener;
import com.veepoo.protocol.listener.data.IZT163DeviceAlwaysOffScreenOptListener;
import com.veepoo.protocol.model.datas.NightTurnWristeData;
import com.veepoo.protocol.model.datas.ScreenStyleData;
import com.veepoo.protocol.model.datas.TimeData;
import com.veepoo.protocol.model.enums.EUIFromType;
import com.veepoo.protocol.model.enums.ENightTurnWristeStatus;
import com.veepoo.protocol.model.settings.NightTurnWristSetting;

public class FunctionSettingsActivity extends AppCompatActivity implements IZT163DeviceAlwaysOffScreenOptListener {
    private SwitchView mRaiseWristSwitch;
    private SwitchView mAlwaysOffSwitch;
    private TextView mDirectStatus;
    private TextView mConnectionHint;
    private LinearLayout mItemScreenTime;
    private LinearLayout mItemHealthSettings;
    private LinearLayout mItemFindDevice;
    private LinearLayout mItemEventRemind;
    private LinearLayout mItemAlarm;
    private LinearLayout mItemWatchFace;
    private LinearLayout mItemFactoryReset;
    private LinearLayout mItemReset;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    private boolean mRaiseReady = false;
    private boolean mRaiseSetting = false;
    private boolean mRaiseLastState = false;

    private NightTurnWristeData mRaiseCache;

    private boolean mAlwaysOffReady = false;
    private boolean mAlwaysOffSetting = false;
    private boolean mAlwaysOffLastState = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_function_settings);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        bindEvents();
        updateConnectionUI();
        updateSwitchEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionState();
        requestReadIfNeeded();
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
        mRaiseWristSwitch = findViewById(R.id.switch_raise_wrist);
        mDirectStatus = findViewById(R.id.tv_direct_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
        mItemScreenTime = findViewById(R.id.item_screen_time);
        mItemHealthSettings = findViewById(R.id.item_health_settings);
        mItemFindDevice = findViewById(R.id.item_find_device);
        mItemEventRemind = findViewById(R.id.item_event_remind);
        mItemAlarm = findViewById(R.id.item_alarm);
        mItemWatchFace = findViewById(R.id.item_watch_face);
        mItemFactoryReset = findViewById(R.id.item_factory_reset);
        mItemReset = findViewById(R.id.item_reset);
        mAlwaysOffSwitch = findViewById(R.id.switch_always_off);
    }

    private void bindEvents() {
        mRaiseWristSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                mRaiseLastState = mRaiseCache != null && mRaiseCache.isNightTureWirsteStatusOpen();
                if (!canOperateSwitch()) {
                    view.toggleSwitch(mRaiseLastState);
                    return;
                }
                mRaiseSetting = true;
                updateDirectStatus("抬腕亮屏：正在设置");
                updateSwitchEnabled();
                sendRaiseWristSetting(true);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                mRaiseLastState = mRaiseCache != null && mRaiseCache.isNightTureWirsteStatusOpen();
                if (!canOperateSwitch()) {
                    view.toggleSwitch(mRaiseLastState);
                    return;
                }
                mRaiseSetting = true;
                updateDirectStatus("抬腕亮屏：正在设置");
                updateSwitchEnabled();
                sendRaiseWristSetting(false);
            }
        });

        mItemScreenTime.setOnClickListener(v -> startEntry(ScreenLightTimeActivity.class));
        mItemHealthSettings.setOnClickListener(v -> startEntry(HealthSettingsActivity.class));
        mItemFindDevice.setOnClickListener(v -> startEntry(FindDeviceActivity.class));
        mItemEventRemind.setOnClickListener(v -> startEntry(EventRemindActivity.class));
        mItemAlarm.setOnClickListener(v -> startEntry(CustomTextAlarmActivity.class)); // 使用自定义文字闹钟界面
        mItemWatchFace.setOnClickListener(v -> {
            if (!mIsConnected) {
                showMsg("未连接设备");
                return;
            }
            readWatchFace();
        });
        mAlwaysOffSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                if (!mIsConnected) {
                    showMsg("未连接设备");
                    view.toggleSwitch(mAlwaysOffLastState);
                    return;
                }
                mAlwaysOffSetting = true;
                updateDirectStatus("常灭屏：正在设置");
                updateSwitchEnabled();
                VPOperateManager.getInstance().setZT163DeviceAlwaysOffScreen(true, mBleWriteResponse,
                        FunctionSettingsActivity.this);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                if (!mIsConnected) {
                    showMsg("未连接设备");
                    view.toggleSwitch(mAlwaysOffLastState);
                    return;
                }
                mAlwaysOffSetting = true;
                updateDirectStatus("常灭屏：正在设置");
                updateSwitchEnabled();
                VPOperateManager.getInstance().setZT163DeviceAlwaysOffScreen(false, mBleWriteResponse,
                        FunctionSettingsActivity.this);
            }
        });
        mItemFactoryReset.setOnClickListener(v -> {
            if (!mIsConnected) {
                showMsg("未连接设备");
                return;
            }
            showConfirmDialog("恢复出厂设置", "确定要恢复出厂设置吗？这将清除设备上的所有数据。", () -> {
                VPOperateManager.getInstance().clearDeviceData(mBleWriteResponse);
                showMsg("已发送恢复出厂设置指令");
                finish();
            });
        });
        mItemReset.setOnClickListener(v -> {
            if (!mIsConnected) {
                showMsg("未连接设备");
                return;
            }
            showConfirmDialog("复位", "确定要复位设备吗？", () -> {
                VPOperateManager.getInstance().resetDeviceData(mBleWriteResponse);
                showMsg("已发送复位指令");
            });
        });
    }

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void readWatchFace() {
        VPOperateManager.getInstance().readScreenStyle(mBleWriteResponse, new IScreenStyleListener() {
            @Override
            public void onScreenStyleDataChange(ScreenStyleData screenStyleData) {
                showWatchFaceDialog(screenStyleData);
            }
        });
    }

    private void showWatchFaceDialog(ScreenStyleData currentData) {
        String[] items = { "表盘 0", "表盘 1", "表盘 2", "表盘 3", "表盘 4", "表盘 5", "表盘 6" };
        new AlertDialog.Builder(this)
                .setTitle("选择表盘 (当前: " + currentData.getScreenIndex() + ")")
                .setItems(items, (dialog, which) -> {
                    VPOperateManager.getInstance().settingScreenStyle(mBleWriteResponse, new IScreenStyleListener() {
                        @Override
                        public void onScreenStyleDataChange(ScreenStyleData screenStyleData) {
                            showMsg("设置表盘成功: " + screenStyleData.getScreenIndex());
                        }
                    }, which, EUIFromType.DEFAULT);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startEntry(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        intent.putExtra("deviceaddress", mDeviceAddress);
        startActivity(intent);
    }

    private boolean canOperateSwitch() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return false;
        }
        if (!mRaiseReady) {
            showMsg("抬腕亮屏状态未就绪");
            return false;
        }
        return true;
    }

    private void sendRaiseWristSetting(boolean open) {
        NightTurnWristeData cache = mRaiseCache;
        TimeData start = cache != null && cache.getStartTime() != null ? cache.getStartTime() : new TimeData(0, 0);
        TimeData end = cache != null && cache.getEndTime() != null ? cache.getEndTime() : new TimeData(23, 59);
        int level = cache != null ? cache.getLevel() : 2;
        NightTurnWristSetting setting = new NightTurnWristSetting(open, start, end, level);
        VPOperateManager.getInstance().settingNightTurnWriste(mBleWriteResponse, mNightTurnListener, setting);
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
            updateSwitchEnabled();
            if (mIsConnected) {
                requestReadIfNeeded();
            } else {
                mRaiseReady = false;
                mAlwaysOffReady = false;
            }
        }
    };

    private void requestReadIfNeeded() {
        if (!mIsConnected) {
            return;
        }
        readRaiseWristState();
        readAlwaysOffState();
    }

    private void readAlwaysOffState() {
        VPOperateManager.getInstance().readZT163DeviceAlwaysOffScreen(mBleWriteResponse, this);
        updateDirectStatus("常灭屏：读取中");
    }

    private void readRaiseWristState() {
        VPOperateManager.getInstance().readNightTurnWriste(mBleWriteResponse, mNightTurnListener);
        updateDirectStatus("抬腕亮屏：读取中");
    }

    private final INightTurnWristeDataListener mNightTurnListener = new INightTurnWristeDataListener() {
        @Override
        public void onNightTurnWristeDataChange(NightTurnWristeData nightTurnWristeData) {
            mRaiseCache = nightTurnWristeData;
            ENightTurnWristeStatus status = nightTurnWristeData.getOprateStauts();
            boolean isSuccess = status == ENightTurnWristeStatus.SUCCESS;
            if (mRaiseSetting) {
                mRaiseSetting = false;
                if (isSuccess) {
                    mRaiseReady = true;
                    mRaiseWristSwitch.setOpened(nightTurnWristeData.isNightTureWirsteStatusOpen());
                    updateDirectStatus("抬腕亮屏：" + (nightTurnWristeData.isNightTureWirsteStatusOpen() ? "已开启" : "已关闭"));
                } else {
                    mRaiseWristSwitch.setOpened(mRaiseLastState);
                    updateDirectStatus("抬腕亮屏：设置失败");
                }
            } else {
                if (isSuccess) {
                    mRaiseReady = true;
                    mRaiseWristSwitch.setOpened(nightTurnWristeData.isNightTureWirsteStatusOpen());
                    updateDirectStatus("抬腕亮屏：" + (nightTurnWristeData.isNightTureWirsteStatusOpen() ? "已开启" : "已关闭"));
                } else {
                    mRaiseReady = false;
                    updateDirectStatus("抬腕亮屏：读取失败");
                }
            }
            updateSwitchEnabled();
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                updateDirectStatus("指令发送失败，请重试");
            }
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
    }

    private void updateSwitchEnabled() {
        boolean raiseEnabled = mIsConnected && mRaiseReady && !mRaiseSetting;
        mRaiseWristSwitch.setEnabled(raiseEnabled);
        mRaiseWristSwitch.setAlpha(raiseEnabled ? 1.0f : 0.5f);

        boolean alwaysOffEnabled = mIsConnected && mAlwaysOffReady && !mAlwaysOffSetting;
        mAlwaysOffSwitch.setEnabled(alwaysOffEnabled);
        mAlwaysOffSwitch.setAlpha(alwaysOffEnabled ? 1.0f : 0.5f);
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenSettingSuccess(boolean isOpen) {
        mAlwaysOffSetting = false;
        mAlwaysOffReady = true;
        mAlwaysOffLastState = isOpen;
        mAlwaysOffSwitch.setOpened(isOpen);
        updateDirectStatus("常灭屏：" + (isOpen ? "已开启" : "已关闭"));
        updateSwitchEnabled();
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenSettingFailed() {
        mAlwaysOffSetting = false;
        mAlwaysOffSwitch.setOpened(mAlwaysOffLastState);
        updateDirectStatus("常灭屏：设置失败");
        updateSwitchEnabled();
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenReport(boolean isOpen) {
        mAlwaysOffReady = true;
        mAlwaysOffLastState = isOpen;
        mAlwaysOffSwitch.setOpened(isOpen);
        updateDirectStatus("常灭屏：" + (isOpen ? "已开启" : "已关闭"));
        updateSwitchEnabled();
    }

    @Override
    public void onFunctionNotSupport() {
        mAlwaysOffReady = false;
        mAlwaysOffSwitch.setEnabled(false);
        mAlwaysOffSwitch.setAlpha(0.5f);
        updateDirectStatus("常灭屏：设备不支持");
    }

    private void updateDirectStatus(String text) {
        mDirectStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
