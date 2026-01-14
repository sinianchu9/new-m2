package com.timaimee.vpdemo.activity;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.inuker.bluetooth.library.Constants;
import com.timaimee.vpdemo.R;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.IHealthRemindListener;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.model.datas.HealthRemind;
import com.veepoo.protocol.model.datas.TimeData;
import com.veepoo.protocol.model.enums.HealthRemindType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class HealthRemindActivity extends AppCompatActivity implements IHealthRemindListener {
    private static final String TAG = "HealthRemindActivity";
    private SwitchView mSwitchMaster;
    private TextView mMasterDesc;
    private LinearLayout mSettingsContainer;
    private TextView mStartTimeView;
    private TextView mEndTimeView;
    private TextView mTimeError;
    private LinearLayout mModeSingleSoft;
    private LinearLayout mModeSingleStrong;
    private LinearLayout mModeDoubleSoft;
    private LinearLayout mModeSoftStrong;
    private LinearLayout mModeDoubleStrong;
    private TextView mModeDesc;
    private TextView mIntervalValue;
    private SeekBar mIntervalSeek;
    private TextView mConnectionHint;
    private Button mSaveButton;
    private View mLoadingOverlay;

    private boolean mMasterEnabled = false;
    private boolean mIsConnected = false;
    private boolean mIsApplied = false;
    private boolean mHasPendingChanges = false;
    private boolean mIsSaving = false;
    private String mDeviceAddress;
    private boolean mIsConnectListenerRegistered = false;
    private boolean mIsReadRequested = false;

    private int mStartHour = -1;
    private int mStartMinute = -1;
    private int mEndHour = -1;
    private int mEndMinute = -1;
    private int mIntervalMinutes = 30;
    private Mode mMode = Mode.SINGLE_SOFT;

    private long mLastModeClickMs = 0L;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final EnumMap<HealthRemindType, HealthRemind> mRemindCache = new EnumMap<>(HealthRemindType.class);
    private int mPendingWriteCount = 0;

    private enum Mode {
        SINGLE_SOFT, // 单次柔和提醒（读书）
        SINGLE_STRONG, // 单次强度提醒（吃药）
        DOUBLE_SOFT, // 双次柔和提醒（读书+出行）
        SOFT_STRONG, // 一柔一强提醒（读书+洗手）
        DOUBLE_STRONG // 双次强度提醒（吃药+洗手）
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_remind);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        bindEvents();
        updateModeUI();
        updateIntervalUI();
        updateMasterUI();
        updateConnectionUI();
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
        mSwitchMaster = findViewById(R.id.switch_master);
        mMasterDesc = findViewById(R.id.tv_master_desc);
        mSettingsContainer = findViewById(R.id.settings_container);
        mStartTimeView = findViewById(R.id.tv_start_time);
        mEndTimeView = findViewById(R.id.tv_end_time);
        mTimeError = findViewById(R.id.tv_time_error);
        mModeSingleSoft = findViewById(R.id.btn_mode_single_soft);
        mModeSingleStrong = findViewById(R.id.btn_mode_single_strong);
        mModeDoubleSoft = findViewById(R.id.btn_mode_double_soft);
        mModeSoftStrong = findViewById(R.id.btn_mode_soft_strong);
        mModeDoubleStrong = findViewById(R.id.btn_mode_double_strong);
        mModeDesc = findViewById(R.id.tv_mode_desc);
        mIntervalValue = findViewById(R.id.tv_interval_value);
        mIntervalSeek = findViewById(R.id.seek_interval);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
        mSaveButton = findViewById(R.id.btn_save);
        mLoadingOverlay = findViewById(R.id.health_remind_root);
    }

    private void bindEvents() {
        mSwitchMaster.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                view.toggleSwitch(true);
                mMasterEnabled = true;
                markPending();
                updateMasterUI();
            }

            @Override
            public void toggleToOff(SwitchView view) {
                view.toggleSwitch(false);
                mMasterEnabled = false;
                mIsApplied = false;
                markPending();
                updateMasterUI();
                sendCloseAllHealthRemind();
            }
        });

        mStartTimeView.setOnClickListener(v -> showTimePicker(true));
        mEndTimeView.setOnClickListener(v -> showTimePicker(false));

        mModeSingleSoft.setOnClickListener(v -> onModeClicked(Mode.SINGLE_SOFT));
        mModeSingleStrong.setOnClickListener(v -> onModeClicked(Mode.SINGLE_STRONG));
        mModeDoubleSoft.setOnClickListener(v -> onModeClicked(Mode.DOUBLE_SOFT));
        mModeSoftStrong.setOnClickListener(v -> onModeClicked(Mode.SOFT_STRONG));
        mModeDoubleStrong.setOnClickListener(v -> onModeClicked(Mode.DOUBLE_STRONG));

        mIntervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mIntervalMinutes = progress + 1;
                updateIntervalUI();
                markPending();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mSaveButton.setOnClickListener(v -> onSaveClicked());
    }

    private void showTimePicker(final boolean isStart) {
        int hour = isStart ? (mStartHour >= 0 ? mStartHour : 9) : (mEndHour >= 0 ? mEndHour : 21);
        int minute = isStart ? (mStartMinute >= 0 ? mStartMinute : 0) : (mEndMinute >= 0 ? mEndMinute : 0);
        TimePickerDialog dialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (isStart) {
                    mStartHour = hourOfDay;
                    mStartMinute = minute;
                } else {
                    mEndHour = hourOfDay;
                    mEndMinute = minute;
                }
                updateTimeUI();
                markPending();
            }
        }, hour, minute, true);
        dialog.show();
    }

    private void onModeClicked(Mode mode) {
        long now = SystemClock.elapsedRealtime();
        if (now - mLastModeClickMs < 300) {
            return;
        }
        mLastModeClickMs = now;
        if (mMode == mode) {
            return;
        }
        mMode = mode;
        updateModeUI();
        markPending();
    }

    private void onSaveClicked() {
        if (!mMasterEnabled) {
            return;
        }
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        if (!hasTimeSet()) {
            mTimeError.setVisibility(View.VISIBLE);
            showMsg("请设置提醒时间");
            return;
        }
        if (!isTimeValid()) {
            mTimeError.setVisibility(View.VISIBLE);
            showMsg("时间范围有误");
            return;
        }
        sendHealthRemindSettings();
    }

    private void updateMasterUI() {
        mSettingsContainer.setAlpha(mMasterEnabled ? 1.0f : 0.6f);
        if (mMasterEnabled && mIsApplied && mIsConnected && !mHasPendingChanges) {
            mMasterDesc.setText("设备将按照以下设置进行提醒");
        } else {
            mMasterDesc.setText("当前提醒未生效");
        }
        updateSaveEnabled();
    }

    private void updateTimeUI() {
        if (mStartHour >= 0) {
            mStartTimeView.setText(formatTime(mStartHour, mStartMinute));
        }
        if (mEndHour >= 0) {
            mEndTimeView.setText(formatTime(mEndHour, mEndMinute));
        }
        if (!hasTimeSet()) {
            mTimeError.setVisibility(View.GONE);
        } else {
            mTimeError.setVisibility(isTimeValid() ? View.GONE : View.VISIBLE);
        }
        updateSaveEnabled();
    }

    private void updateModeUI() {
        setModeSelected(mModeSingleSoft, mMode == Mode.SINGLE_SOFT);
        setModeSelected(mModeSingleStrong, mMode == Mode.SINGLE_STRONG);
        setModeSelected(mModeDoubleSoft, mMode == Mode.DOUBLE_SOFT);
        setModeSelected(mModeSoftStrong, mMode == Mode.SOFT_STRONG);
        setModeSelected(mModeDoubleStrong, mMode == Mode.DOUBLE_STRONG);

        switch (mMode) {
            case SINGLE_SOFT:
                mModeDesc.setText("单次柔和：下发【读书提醒】");
                break;
            case SINGLE_STRONG:
                mModeDesc.setText("单次强度：下发【吃药提醒】");
                break;
            case DOUBLE_SOFT:
                mModeDesc.setText("双次柔和：下发【读书提醒】4秒后【出行提醒】");
                break;
            case SOFT_STRONG:
                mModeDesc.setText("一柔一强：下发【读书提醒】4秒后【洗手提醒】");
                break;
            case DOUBLE_STRONG:
                mModeDesc.setText("双次强度：下发【吃药提醒】7秒后【洗手提醒】");
                break;
        }
    }

    private void setModeSelected(LinearLayout view, boolean selected) {
        view.setSelected(selected);
        int textColor = selected ? getResources().getColor(android.R.color.white)
                : getResources().getColor(R.color.text_primary);
        TextView text = (TextView) view.getChildAt(1);
        text.setTextColor(textColor);
    }

    private void updateIntervalUI() {
        mIntervalValue.setText(String.valueOf(mIntervalMinutes));
        if (mIntervalSeek.getProgress() != mIntervalMinutes - 1) {
            mIntervalSeek.setProgress(mIntervalMinutes - 1);
        }
        updateSaveEnabled();
    }

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
        updateSaveEnabled();
    }

    private void updateSaveEnabled() {
        boolean canSave = mMasterEnabled && mIsConnected && hasTimeSet() && isTimeValid() && !mIsSaving;
        mSaveButton.setEnabled(canSave);
    }

    private boolean isTimeValid() {
        if (mStartHour < 0 || mEndHour < 0) {
            return false;
        }
        int startTotal = mStartHour * 60 + mStartMinute;
        int endTotal = mEndHour * 60 + mEndMinute;
        return startTotal < endTotal;
    }

    private boolean hasTimeSet() {
        return mStartHour >= 0 && mEndHour >= 0;
    }

    private String formatTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private void markPending() {
        mHasPendingChanges = true;
        mIsApplied = false;
        updateMasterUI();
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
            updateMasterUI();
            if (mIsConnected) {
                requestReadIfNeeded();
            } else {
                mIsReadRequested = false;
            }
        }
    };

    private void requestReadIfNeeded() {
        if (!mIsConnected || mIsReadRequested) {
            return;
        }
        mIsReadRequested = true;
        VPOperateManager.getInstance().readHealthRemind(HealthRemindType.ALL, this, mBleWriteResponse);
    }

    private void sendHealthRemindSettings() {
        List<HealthRemind> settings = buildHealthRemindSettings();
        if (settings.isEmpty()) {
            showMsg("当前设置无效");
            return;
        }

        mIsSaving = true;
        setPageLocked(true);
        mSaveButton.setText("保存中...");

        // 第一步：先关闭所有提醒
        TimeData start = new TimeData(mStartHour, mStartMinute);
        TimeData end = new TimeData(mEndHour, mEndMinute);

        List<HealthRemindType> typesToClose = new ArrayList<>();
        typesToClose.add(HealthRemindType.READING);
        typesToClose.add(HealthRemindType.TAKE_MEDICINE);
        typesToClose.add(HealthRemindType.GOING_OUT);
        typesToClose.add(HealthRemindType.WASH);

        mPendingWriteCount = typesToClose.size() + settings.size();

        // 依次关闭所有提醒类型，每个间隔500ms
        for (int i = 0; i < typesToClose.size(); i++) {
            final HealthRemindType type = typesToClose.get(i);
            int delayMs = i * 500;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    HealthRemind remind = new HealthRemind(type, start, end, mIntervalMinutes, false);
                    VPOperateManager.getInstance().settingHealthRemind(remind, HealthRemindActivity.this,
                            mBleWriteResponse);
                }
            }, delayMs);
        }

        // 第二步：关闭完成后，开启目标模式，间隔时间根据模式而定
        int closeAllDelayMs = typesToClose.size() * 500 + 1000; // 关闭完成后额外1秒
        for (int i = 0; i < settings.size(); i++) {
            final HealthRemind remind = settings.get(i);
            int intervalMs = getIntervalForMode(i); // 根据模式确定间隔
            int delayMs = closeAllDelayMs + intervalMs;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    VPOperateManager.getInstance().settingHealthRemind(remind, HealthRemindActivity.this,
                            mBleWriteResponse);
                }
            }, delayMs);
        }
    }

    private int getIntervalForMode(int index) {
        if (index == 0) {
            return 0; // 第一个提醒立即发送
        }
        // 第二个提醒的间隔时间
        switch (mMode) {
            case DOUBLE_SOFT:
            case SOFT_STRONG:
                return 4000; // 4秒
            case DOUBLE_STRONG:
                return 8000; // 8秒
            default:
                return 0;
        }
    }

    private List<HealthRemind> buildHealthRemindSettings() {
        List<HealthRemind> settings = new ArrayList<>();
        TimeData start = new TimeData(mStartHour, mStartMinute);
        TimeData end = new TimeData(mEndHour, mEndMinute);

        switch (mMode) {
            case SINGLE_SOFT:
                // 单次柔和：只下发读书提醒
                settings.add(new HealthRemind(HealthRemindType.READING, start, end, mIntervalMinutes, true));
                break;
            case SINGLE_STRONG:
                // 单次强度：只下发吃药提醒
                settings.add(new HealthRemind(HealthRemindType.TAKE_MEDICINE, start, end, mIntervalMinutes, true));
                break;
            case DOUBLE_SOFT:
                // 双次柔和：读书 + 出行
                settings.add(new HealthRemind(HealthRemindType.READING, start, end, mIntervalMinutes, true));
                settings.add(new HealthRemind(HealthRemindType.GOING_OUT, start, end, mIntervalMinutes, true));
                break;
            case SOFT_STRONG:
                // 一柔一强：读书 + 洗手
                settings.add(new HealthRemind(HealthRemindType.READING, start, end, mIntervalMinutes, true));
                settings.add(new HealthRemind(HealthRemindType.WASH, start, end, mIntervalMinutes, true));
                break;
            case DOUBLE_STRONG:
                // 双次强度：吃药 + 洗手
                settings.add(new HealthRemind(HealthRemindType.TAKE_MEDICINE, start, end, mIntervalMinutes, true));
                settings.add(new HealthRemind(HealthRemindType.WASH, start, end, mIntervalMinutes, true));
                break;
        }
        return settings;
    }

    private void showMsg(String msg) {
        Toast.makeText(HealthRemindActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
        }
    };

    @Override
    public void functionNotSupport() {
        showMsg("暂不支持该功能");
    }

    @Override
    public void onHealthRemindRead(@NotNull HealthRemind healthRemind) {
        Log.d(TAG, "onHealthRemindRead type=" + healthRemind.getRemindType()
                + " interval=" + healthRemind.getInterval()
                + " status=" + healthRemind.getStatus());
        if (healthRemind.getRemindType() != null) {
            mRemindCache.put(healthRemind.getRemindType(), healthRemind);
        }
    }

    @Override
    public void onHealthRemindReadFailed() {
        showMsg("读取失败");
    }

    @Override
    public void onHealthRemindReport(@NotNull HealthRemind healthRemind) {
    }

    @Override
    public void onHealthRemindReportFailed() {
    }

    @Override
    public void onHealthRemindSettingSuccess(@NotNull HealthRemind healthRemind) {
        Log.d(TAG, "onHealthRemindSettingSuccess type=" + healthRemind.getRemindType()
                + " interval=" + healthRemind.getInterval()
                + " status=" + healthRemind.getStatus());
        onSettingFinished();
    }

    @Override
    public void onHealthRemindSettingFailed(@NotNull HealthRemindType healthRemindType) {
        Log.d(TAG, "onHealthRemindSettingFailed type=" + healthRemindType);
        showMsg("设置失败:" + healthRemindType.getDes());
        onSettingFinished();
    }

    @Override
    public void onHealthRemindReadingComplete() {
        applyDeviceSettings();
    }

    private void onSettingFinished() {
        if (mPendingWriteCount > 0) {
            mPendingWriteCount--;
        }
        if (mPendingWriteCount == 0) {
            mIsSaving = false;
            mHasPendingChanges = false;
            mIsApplied = mMasterEnabled;
            mSaveButton.setText("启用 / 保存");
            setPageLocked(false);
            updateMasterUI();
            showMsg("已保存");
        }
    }

    private void applyDeviceSettings() {
        HealthRemind takeMedicine = mRemindCache.get(HealthRemindType.TAKE_MEDICINE);
        HealthRemind reading = mRemindCache.get(HealthRemindType.READING);
        HealthRemind wash = mRemindCache.get(HealthRemindType.WASH);
        HealthRemind goingOut = mRemindCache.get(HealthRemindType.GOING_OUT);

        // 状态判断逻辑：优先判断吃药提醒，其次看书提醒
        HealthRemind primaryRemind = null;
        if (takeMedicine != null && takeMedicine.getStatus()) {
            primaryRemind = takeMedicine;
        } else if (reading != null && reading.getStatus()) {
            primaryRemind = reading;
        }

        if (primaryRemind != null) {
            TimeData start = primaryRemind.getStartTime();
            TimeData end = primaryRemind.getEndTime();
            if (start != null) {
                mStartHour = start.getHour();
                mStartMinute = start.getMinute();
            }
            if (end != null) {
                mEndHour = end.getHour();
                mEndMinute = end.getMinute();
            }
            int interval = primaryRemind.getInterval();
            if (interval >= 1 && interval <= 90) {
                mIntervalMinutes = interval;
            }
            mMasterEnabled = true;
        } else {
            mMasterEnabled = false;
        }

        // 根据开启的提醒类型判断模式
        boolean takeMedicineOn = takeMedicine != null && takeMedicine.getStatus();
        boolean readingOn = reading != null && reading.getStatus();
        boolean washOn = wash != null && wash.getStatus();
        boolean goingOutOn = goingOut != null && goingOut.getStatus();

        if (takeMedicineOn && washOn) {
            mMode = Mode.DOUBLE_STRONG; // 吃药 + 洗手
        } else if (takeMedicineOn && !washOn) {
            mMode = Mode.SINGLE_STRONG; // 只有吃药
        } else if (readingOn && washOn) {
            mMode = Mode.SOFT_STRONG; // 读书 + 洗手
        } else if (readingOn && goingOutOn) {
            mMode = Mode.DOUBLE_SOFT; // 读书 + 出行
        } else if (readingOn) {
            mMode = Mode.SINGLE_SOFT; // 只有读书
        } else {
            mMode = Mode.SINGLE_SOFT; // 默认
        }

        mSwitchMaster.setOpened(mMasterEnabled);
        updateTimeUI();
        updateIntervalUI();
        updateModeUI();
        mHasPendingChanges = false;
        mIsApplied = mMasterEnabled;
        updateMasterUI();
    }

    private void sendCloseAllHealthRemind() {
        if (!mIsConnected) {
            return;
        }

        // 分别关闭所有提醒类型
        TimeData start = hasTimeSet() ? new TimeData(mStartHour, mStartMinute) : new TimeData(8, 0);
        TimeData end = hasTimeSet() ? new TimeData(mEndHour, mEndMinute) : new TimeData(20, 0);

        List<HealthRemindType> typesToClose = new ArrayList<>();
        typesToClose.add(HealthRemindType.READING);
        typesToClose.add(HealthRemindType.TAKE_MEDICINE);
        typesToClose.add(HealthRemindType.GOING_OUT);
        typesToClose.add(HealthRemindType.WASH);

        mIsSaving = true;
        mPendingWriteCount = typesToClose.size();
        setPageLocked(true);

        for (int i = 0; i < typesToClose.size(); i++) {
            final HealthRemindType type = typesToClose.get(i);
            int delayMs = i * 500; // 每个命令间隔500ms
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    HealthRemind remind = new HealthRemind(type, start, end, mIntervalMinutes, false);
                    VPOperateManager.getInstance().settingHealthRemind(remind, HealthRemindActivity.this,
                            mBleWriteResponse);
                }
            }, delayMs);
        }
    }

    private void setPageLocked(boolean locked) {
        if (mLoadingOverlay != null) {
            mLoadingOverlay.setEnabled(!locked);
            mLoadingOverlay.setAlpha(locked ? 0.6f : 1.0f);
        }
        mSaveButton.setEnabled(!locked);
        mSwitchMaster.setEnabled(!locked);
    }
}
