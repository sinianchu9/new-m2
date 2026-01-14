package com.timaimee.vpdemo.activity;

import android.os.Bundle;
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
import com.veepoo.protocol.listener.data.IAllSetDataListener;
import com.veepoo.protocol.listener.data.ICustomSettingDataListener;
import com.veepoo.protocol.model.datas.AllSetData;
import com.veepoo.protocol.model.enums.EAllSetStatus;
import com.veepoo.protocol.model.enums.EAllSetType;
import com.veepoo.protocol.model.enums.ECustomStatus;
import com.veepoo.protocol.model.enums.EFunctionStatus;
import com.veepoo.protocol.model.settings.AllSetSetting;
import com.veepoo.protocol.model.settings.CustomSetting;
import com.veepoo.protocol.model.settings.CustomSettingData;

public class HealthSettingsActivity extends AppCompatActivity {
    private SwitchView mSpo2Switch;
    private SwitchView mHeartSwitch;
    private SwitchView mHrvSwitch;
    private SwitchView mBpSwitch;
    private SwitchView mTempSwitch;
    private TextView mStatus;
    private TextView mConnectionHint;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    private CustomSettingData mCustomData;
    private AllSetData mSpo2Data;

    private boolean mIsSettingSpo2 = false;
    private boolean mIsSettingCustom = false;
    private boolean mLastSpo2 = false;
    private boolean mLastHeart = false;
    private boolean mLastHrv = false;
    private boolean mLastBp = false;
    private boolean mLastTemp = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_settings);
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
        mSpo2Switch = findViewById(R.id.switch_spo2_auto);
        mHeartSwitch = findViewById(R.id.switch_auto_hr);
        mHrvSwitch = findViewById(R.id.switch_auto_hrv);
        mBpSwitch = findViewById(R.id.switch_auto_bp);
        mTempSwitch = findViewById(R.id.switch_auto_temp);
        mStatus = findViewById(R.id.tv_health_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
    }

    private void bindEvents() {
        mSpo2Switch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                mLastSpo2 = isSpo2Open();
                if (!canOperateSpo2()) {
                    view.toggleSwitch(mLastSpo2);
                    return;
                }
                mIsSettingSpo2 = true;
                updateStatus("血氧全天监测：正在设置");
                updateSwitchEnabled();
                sendSpo2Setting(true);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                mLastSpo2 = isSpo2Open();
                if (!canOperateSpo2()) {
                    view.toggleSwitch(mLastSpo2);
                    return;
                }
                mIsSettingSpo2 = true;
                updateStatus("血氧全天监测：正在设置");
                updateSwitchEnabled();
                sendSpo2Setting(false);
            }
        });

        mHeartSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastHeart);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("心率自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(true, null, null, null);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastHeart);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("心率自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(false, null, null, null);
            }
        });

        mHrvSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastHrv);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("HRV 自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, EFunctionStatus.SUPPORT_OPEN, null, null);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastHrv);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("HRV 自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, EFunctionStatus.SUPPORT_CLOSE, null, null);
            }
        });

        mBpSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastBp);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("血压自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, null, true, null);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastBp);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("血压自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, null, false, null);
            }
        });

        mTempSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastTemp);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("体温自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, null, null, EFunctionStatus.SUPPORT_OPEN);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                captureLastCustomStates();
                if (!canOperateCustom()) {
                    view.toggleSwitch(mLastTemp);
                    return;
                }
                mIsSettingCustom = true;
                updateStatus("体温自动监测：正在设置");
                updateSwitchEnabled();
                sendCustomSetting(null, null, null, EFunctionStatus.SUPPORT_CLOSE);
            }
        });
    }

    private void requestRead() {
        if (!mIsConnected) {
            updateStatus("状态：未连接");
            return;
        }
        updateStatus("状态：读取中");
        VPOperateManager.getInstance().readCustomSetting(mBleWriteResponse, mCustomSettingListener);
        VPOperateManager.getInstance().readSpo2hAutoDetect(mBleWriteResponse, mAllSetListener);
    }

    private final ICustomSettingDataListener mCustomSettingListener = new ICustomSettingDataListener() {
        @Override
        public void OnSettingDataChange(CustomSettingData customSettingData) {
            if (customSettingData == null) {
                updateStatus("状态：读取失败");
                mIsSettingCustom = false;
                updateSwitchEnabled();
                return;
            }
            mCustomData = customSettingData;
            if (customSettingData.getStatus() == ECustomStatus.FAIL) {
                if (mIsSettingCustom) {
                    updateStatus("状态：设置失败");
                    rollbackCustomSwitches();
                } else {
                    updateStatus("状态：读取失败");
                }
            } else if (customSettingData.getStatus() == ECustomStatus.SETTING_SUCCESS) {
                updateStatus("状态：设置成功");
            } else {
                updateStatus("状态：已同步设备");
            }
            mIsSettingCustom = false;
            updateCustomSwitches();
            updateSwitchEnabled();
        }
    };

    private final IAllSetDataListener mAllSetListener = new IAllSetDataListener() {
        @Override
        public void onAllSetDataChangeListener(AllSetData allSetData) {
            if (allSetData == null) {
                updateStatus("状态：读取失败");
                mIsSettingSpo2 = false;
                updateSwitchEnabled();
                return;
            }
            if (allSetData.getType() != EAllSetType.SPO2H_NIGHT_AUTO_DETECT) {
                return;
            }
            mSpo2Data = allSetData;
            EAllSetStatus status = allSetData.getOprateResult();
            if (status == EAllSetStatus.READ_FAIL) {
                updateStatus("血氧全天监测：读取失败");
            } else if (status == EAllSetStatus.SETTING_FAIL || status == EAllSetStatus.OPEN_FAIL || status == EAllSetStatus.CLOSE_FAIL) {
                updateStatus("血氧全天监测：设置失败");
                mSpo2Switch.setOpened(mLastSpo2);
            } else if (status == EAllSetStatus.UNSUPPORT) {
                updateStatus("血氧全天监测：设备不支持");
            } else if (status == EAllSetStatus.SETTING_SUCCESS || status == EAllSetStatus.OPEN_SUCCESS || status == EAllSetStatus.CLOSE_SUCCESS) {
                updateStatus("血氧全天监测：设置成功");
            } else if (status == EAllSetStatus.READ_SUCCESS) {
                updateStatus("血氧全天监测：已同步设备");
            }
            updateSpo2Switch();
            mIsSettingSpo2 = false;
            updateSwitchEnabled();
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                updateStatus("状态：指令发送失败");
                mIsSettingCustom = false;
                mIsSettingSpo2 = false;
                updateSwitchEnabled();
            }
        }
    };

    private void sendSpo2Setting(boolean open) {
        int startHour = mSpo2Data != null ? mSpo2Data.getStartHour() : 22;
        int startMinute = mSpo2Data != null ? mSpo2Data.getStartMinute() : 0;
        int endHour = mSpo2Data != null ? mSpo2Data.getEndHour() : 8;
        int endMinute = mSpo2Data != null ? mSpo2Data.getEndMinute() : 0;
        int oprate = 0;
        int openState = open ? 1 : 0;
        AllSetSetting setting = new AllSetSetting(EAllSetType.SPO2H_NIGHT_AUTO_DETECT, startHour, startMinute, endHour, endMinute, oprate, openState);
        VPOperateManager.getInstance().settingSpo2hAutoDetect(mBleWriteResponse, mAllSetListener, setting);
    }

    private void sendCustomSetting(Boolean autoHeart, EFunctionStatus autoHrv, Boolean autoBp, EFunctionStatus autoTemp) {
        if (mCustomData == null) {
            updateStatus("状态：未读取设备设置");
            mIsSettingCustom = false;
            updateSwitchEnabled();
            return;
        }
        CustomSetting setting = new CustomSetting(
                mCustomData.isHaveMetricSystem(),
                mCustomData.isMetricSystemValue(),
                mCustomData.is24Hour(),
                autoHeart != null ? autoHeart : mCustomData.isOpenAutoHeartDetect(),
                autoBp != null ? autoBp : mCustomData.isOpenAutoBpDetect()
        );
        setting.setIsOpenSportRemain(safeStatus(mCustomData.getSportOverRemain()));
        setting.setIsOpenVoiceBpHeart(safeStatus(mCustomData.getVoiceBpHeart()));
        setting.setIsOpenFindPhoneUI(safeStatus(mCustomData.getFindPhoneUi()));
        setting.setIsOpenStopWatch(safeStatus(mCustomData.getSecondsWatch()));
        setting.setIsOpenSpo2hLowRemind(safeStatus(mCustomData.getLowSpo2hRemain()));
        setting.setIsOpenWearDetectSkin(safeStatus(mCustomData.getSkin()));
        setting.setIsOpenAutoHRV(autoHrv != null ? autoHrv : safeStatus(mCustomData.getAutoHrv()));
        setting.setIsOpenAutoInCall(safeStatus(mCustomData.getAutoIncall()));
        setting.setIsOpenDisconnectRemind(safeStatus(mCustomData.getDisconnectRemind()));
        setting.setIsOpenSOS(safeStatus(mCustomData.getSOS()));
        setting.setIsOpenAutoTemperatureDetect(autoTemp != null ? autoTemp : safeStatus(mCustomData.getAutoTemperatureDetect()));
        setting.setIsOpenPPG(safeStatus(mCustomData.getPpg()));
        setting.setIsOpenMusicControl(safeStatus(mCustomData.getMusicControl()));
        setting.setIsOpenLongClickLockScreen(safeStatus(mCustomData.getLongClickLockScreen()));
        setting.setIsOpenMessageScreenLight(safeStatus(mCustomData.getMessageScreenLight()));
        setting.setIsOpenBloodGlucoseDetect(safeStatus(mCustomData.getBloodGlucoseDetection()));
        setting.setIsOpenBloodComponentDetect(safeStatus(mCustomData.getBloodComponentDetect()));
        setting.setEcgAlwaysOpen(safeStatus(mCustomData.getEcgAlwaysOpen()));
        setting.setMETDetect(safeStatus(mCustomData.getMETDetect()));
        setting.setStressDetect(safeStatus(mCustomData.getStressDetect()));
        setting.setTemperatureUnit(mCustomData.getTemperatureUnit());
        setting.setBloodGlucoseUnit(mCustomData.getBloodGlucoseUnit());
        setting.setUricAcidUnit(mCustomData.getUricAcidUnit());
        setting.setBloodFatUnit(mCustomData.getBloodFatUnit());
        setting.setSkinType(mCustomData.getSkinLevel());
        VPOperateManager.getInstance().changeCustomSetting(mBleWriteResponse, mCustomSettingListener, setting);
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
                requestRead();
            }
        }
    };

    private void updateCustomSwitches() {
        mHeartSwitch.setOpened(isAutoHeartOpen());
        mHrvSwitch.setOpened(isAutoHrvOpen());
        mBpSwitch.setOpened(isAutoBpOpen());
        mTempSwitch.setOpened(isAutoTempOpen());
    }

    private void updateSpo2Switch() {
        mSpo2Switch.setOpened(isSpo2Open());
    }

    private void rollbackCustomSwitches() {
        mHeartSwitch.setOpened(mLastHeart);
        mHrvSwitch.setOpened(mLastHrv);
        mBpSwitch.setOpened(mLastBp);
        mTempSwitch.setOpened(mLastTemp);
    }

    private void captureLastCustomStates() {
        mLastHeart = isAutoHeartOpen();
        mLastHrv = isAutoHrvOpen();
        mLastBp = isAutoBpOpen();
        mLastTemp = isAutoTempOpen();
    }

    private boolean isAutoHeartOpen() {
        return mCustomData != null && mCustomData.isOpenAutoHeartDetect();
    }

    private boolean isAutoBpOpen() {
        return mCustomData != null && mCustomData.isOpenAutoBpDetect();
    }

    private boolean isAutoHrvOpen() {
        return mCustomData != null && mCustomData.getAutoHrv() == EFunctionStatus.SUPPORT_OPEN;
    }

    private boolean isAutoTempOpen() {
        return mCustomData != null && mCustomData.getAutoTemperatureDetect() == EFunctionStatus.SUPPORT_OPEN;
    }

    private boolean isSpo2Open() {
        if (mSpo2Data == null) {
            return false;
        }
        return mSpo2Data.getIsOpen() == 1 || mSpo2Data.getOpenState() == 1;
    }

    private boolean canOperateSpo2() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return false;
        }
        if (mSpo2Data != null && mSpo2Data.getOprateResult() == EAllSetStatus.UNSUPPORT) {
            showMsg("设备不支持血氧监测");
            return false;
        }
        if (mIsSettingSpo2) {
            return false;
        }
        return true;
    }

    private boolean canOperateCustom() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return false;
        }
        if (mCustomData == null) {
            showMsg("请先读取设备状态");
            return false;
        }
        if (mIsSettingCustom) {
            return false;
        }
        return true;
    }

    private void updateSwitchEnabled() {
        boolean spo2Enabled = mIsConnected && !mIsSettingSpo2 && !isSpo2Unsupported();
        boolean customEnabled = mIsConnected && !mIsSettingCustom;
        mSpo2Switch.setEnabled(spo2Enabled);
        mSpo2Switch.setAlpha(spo2Enabled ? 1.0f : 0.5f);

        updateSwitchEnabledState(mHeartSwitch, customEnabled && isSupportStatus(mCustomData != null ? mCustomData.getAutoHeartDetect() : null));
        updateSwitchEnabledState(mHrvSwitch, customEnabled && isSupportStatus(mCustomData != null ? mCustomData.getAutoHrv() : null));
        updateSwitchEnabledState(mBpSwitch, customEnabled && isSupportStatus(mCustomData != null ? mCustomData.getAutoBpDetect() : null));
        updateSwitchEnabledState(mTempSwitch, customEnabled && isSupportStatus(mCustomData != null ? mCustomData.getAutoTemperatureDetect() : null));
    }

    private void updateSwitchEnabledState(SwitchView view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private boolean isSupportStatus(EFunctionStatus status) {
        return status != null && status != EFunctionStatus.UNSUPPORT;
    }

    private boolean isSpo2Unsupported() {
        return mSpo2Data != null && mSpo2Data.getOprateResult() == EAllSetStatus.UNSUPPORT;
    }

    private EFunctionStatus safeStatus(EFunctionStatus status) {
        return status == null ? EFunctionStatus.UNSUPPORT : status;
    }

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
