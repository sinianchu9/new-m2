package com.timaimee.vpdemo.activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
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
import com.veepoo.protocol.listener.data.IAlarm2DataListListener;
import com.veepoo.protocol.listener.data.ITextAlarmDataListener;
import com.veepoo.protocol.model.datas.AlarmData2;
import com.veepoo.protocol.model.datas.TextAlarmData;
import com.veepoo.protocol.model.enums.EMultiAlarmOprate;
import com.veepoo.protocol.model.settings.Alarm2Setting;
import com.veepoo.protocol.model.settings.TextAlarm2Setting;

import java.util.Calendar;

public class AlarmDetailActivity extends AppCompatActivity {
    private static final String EXTRA_IS_NEW = "alarm_is_new";
    private static final String EXTRA_ID = "alarm_id";
    private static final String EXTRA_HOUR = "alarm_hour";
    private static final String EXTRA_MINUTE = "alarm_minute";
    private static final String EXTRA_REPEAT = "alarm_repeat";
    private static final String EXTRA_DATE = "alarm_date";
    private static final String EXTRA_SCENE = "alarm_scene";
    private static final String EXTRA_OPEN = "alarm_open";
    private static final String EXTRA_IS_TEXT = "alarm_is_text";
    private static final String EXTRA_CONTENT = "alarm_content";

    private TextView mTimeView;
    private SwitchView mOpenSwitch;
    private TextView mRepeatMode;
    private TextView mOnceMode;
    private TextView mOnceDateView;
    private TextView mConnectionHint;
    private Button mSaveButton;
    private Button mDeleteButton;
    private android.view.View mTextContentLayout;
    private EditText mContentInput;

    private TextView[] mDayViews;
    private final boolean[] mRepeatDays = new boolean[7];

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    private boolean mIsNew = true;
    private boolean mIsTextAlarm = false;
    private int mAlarmId = -1;
    private int mHour = 8;
    private int mMinute = 0;
    private int mScene = 0;
    private boolean mIsOpen = true;
    private boolean mIsOnceMode = false;
    private String mOnceDate = "";
    private String mContent = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_detail);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        parseExtras();
        bindEvents();
        applyDataToUI();
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
        mTimeView = findViewById(R.id.tv_alarm_time);
        mOpenSwitch = findViewById(R.id.switch_alarm_open);
        mRepeatMode = findViewById(R.id.btn_repeat_mode);
        mOnceMode = findViewById(R.id.btn_once_mode);
        mOnceDateView = findViewById(R.id.tv_once_date);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
        mSaveButton = findViewById(R.id.btn_save_alarm);
        mDeleteButton = findViewById(R.id.btn_delete_alarm);
        mTextContentLayout = findViewById(R.id.layout_text_content);
        mContentInput = findViewById(R.id.et_alarm_content);
        mDayViews = new TextView[]{
                findViewById(R.id.day_mon),
                findViewById(R.id.day_tue),
                findViewById(R.id.day_wed),
                findViewById(R.id.day_thu),
                findViewById(R.id.day_fri),
                findViewById(R.id.day_sat),
                findViewById(R.id.day_sun)
        };
    }

    private void parseExtras() {
        mIsNew = getIntent().getBooleanExtra(EXTRA_IS_NEW, true);
        mIsTextAlarm = getIntent().getBooleanExtra(EXTRA_IS_TEXT, false);
        if (!mIsNew) {
            mAlarmId = getIntent().getIntExtra(EXTRA_ID, -1);
            mHour = getIntent().getIntExtra(EXTRA_HOUR, 8);
            mMinute = getIntent().getIntExtra(EXTRA_MINUTE, 0);
            mScene = getIntent().getIntExtra(EXTRA_SCENE, 0);
            mIsOpen = getIntent().getBooleanExtra(EXTRA_OPEN, true);
            mContent = safeString(getIntent().getStringExtra(EXTRA_CONTENT));
            String repeat = getIntent().getStringExtra(EXTRA_REPEAT);
            mOnceDate = safeString(getIntent().getStringExtra(EXTRA_DATE));
            if (!TextUtils.isEmpty(repeat)) {
                if ("0000000".equals(repeat)) {
                    mIsOnceMode = true;
                    for (int i = 0; i < mRepeatDays.length; i++) {
                        mRepeatDays[i] = false;
                    }
                } else {
                    mIsOnceMode = false;
                    applyRepeatStatus(repeat);
                }
            }
            if (mIsOnceMode && TextUtils.isEmpty(mOnceDate)) {
                mOnceDate = getTodayDate();
            }
        } else {
            for (int i = 0; i < mRepeatDays.length; i++) {
                mRepeatDays[i] = true;
            }
            mIsOnceMode = false;
            mOnceDate = getTodayDate();
            if (mIsTextAlarm) {
                mContent = "提醒";
            }
        }
    }

    private void bindEvents() {
        mTimeView.setOnClickListener(v -> showTimePicker());
        mRepeatMode.setOnClickListener(v -> setRepeatMode(false));
        mOnceMode.setOnClickListener(v -> setRepeatMode(true));
        mOnceDateView.setOnClickListener(v -> {
            if (mIsOnceMode) {
                showDatePicker();
            }
        });
        mOpenSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                view.toggleSwitch(true);
                mIsOpen = true;
            }

            @Override
            public void toggleToOff(SwitchView view) {
                view.toggleSwitch(false);
                mIsOpen = false;
            }
        });
        for (int i = 0; i < mDayViews.length; i++) {
            int index = i;
            mDayViews[i].setOnClickListener(v -> toggleDay(index));
        }
        mSaveButton.setOnClickListener(v -> saveAlarm());
        mDeleteButton.setOnClickListener(v -> confirmDelete());
    }

    private void applyDataToUI() {
        mTimeView.setText(formatTime(mHour, mMinute));
        mOpenSwitch.setOpened(mIsOpen);
        setRepeatMode(mIsOnceMode);
        updateDayUI();
        updateOnceDateUI();
        if (mIsTextAlarm) {
            mTextContentLayout.setVisibility(android.view.View.VISIBLE);
            mContentInput.setText(mContent);
        } else {
            mTextContentLayout.setVisibility(android.view.View.GONE);
        }
        mDeleteButton.setVisibility(mIsNew ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void showTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            mHour = hourOfDay;
            mMinute = minute;
            mTimeView.setText(formatTime(mHour, mMinute));
        }, mHour, mMinute, true);
        dialog.show();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (!TextUtils.isEmpty(mOnceDate)) {
            String[] parts = mOnceDate.split("-");
            if (parts.length == 3) {
                calendar.set(Calendar.YEAR, safeInt(parts[0], calendar.get(Calendar.YEAR)));
                calendar.set(Calendar.MONTH, safeInt(parts[1], calendar.get(Calendar.MONTH) + 1) - 1);
                calendar.set(Calendar.DAY_OF_MONTH, safeInt(parts[2], calendar.get(Calendar.DAY_OF_MONTH)));
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            mOnceDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
            updateOnceDateUI();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void toggleDay(int index) {
        if (mIsOnceMode) {
            return;
        }
        mRepeatDays[index] = !mRepeatDays[index];
        updateDayUI();
    }

    private void setRepeatMode(boolean onceMode) {
        mIsOnceMode = onceMode;
        mRepeatMode.setSelected(!onceMode);
        mOnceMode.setSelected(onceMode);
        int repeatColor = !onceMode ? getResources().getColor(android.R.color.white) : getResources().getColor(R.color.text_primary);
        int onceColor = onceMode ? getResources().getColor(android.R.color.white) : getResources().getColor(R.color.text_primary);
        mRepeatMode.setTextColor(repeatColor);
        mOnceMode.setTextColor(onceColor);
        for (TextView day : mDayViews) {
            day.setEnabled(!onceMode);
            day.setAlpha(onceMode ? 0.5f : 1.0f);
        }
        updateDayUI();
        updateOnceDateUI();
    }

    private void updateDayUI() {
        for (int i = 0; i < mDayViews.length; i++) {
            TextView day = mDayViews[i];
            boolean selected = mRepeatDays[i];
            day.setSelected(selected);
            int color = selected ? getResources().getColor(android.R.color.white) : getResources().getColor(R.color.text_primary);
            day.setTextColor(color);
        }
    }

    private void updateOnceDateUI() {
        if (mIsOnceMode) {
            mOnceDateView.setText(TextUtils.isEmpty(mOnceDate) ? "请选择日期" : mOnceDate);
            mOnceDateView.setEnabled(true);
            mOnceDateView.setAlpha(1.0f);
        } else {
            mOnceDateView.setText("重复模式下无需设置");
            mOnceDateView.setEnabled(false);
            mOnceDateView.setAlpha(0.6f);
        }
    }

    private void saveAlarm() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        String repeatStatus = buildRepeatStatus();
        if (!mIsOnceMode && "0000000".equals(repeatStatus)) {
            showMsg("请选择重复周期");
            return;
        }
        if (mIsOnceMode && TextUtils.isEmpty(mOnceDate)) {
            showMsg("请选择单次日期");
            return;
        }
        if (mIsTextAlarm) {
            String content = mContentInput.getText().toString().trim();
            if (TextUtils.isEmpty(content)) {
                showMsg("请输入提醒内容");
                return;
            }
            mContent = content;
        }
        String unRepeatDate = mIsOnceMode ? mOnceDate : "0000-00-00";
        if (mIsTextAlarm) {
            TextAlarm2Setting setting = new TextAlarm2Setting();
            setting.setAlarmHour(mHour);
            setting.setAlarmMinute(mMinute);
            setting.setRepeatStatus(repeatStatus);
            setting.setUnRepeatDate(unRepeatDate);
            setting.setOpen(mIsOpen);
            setting.setContent(mContent);
            if (!mIsNew) {
                setting.setAlarmId(mAlarmId);
            }
            if (mIsNew) {
                VPOperateManager.getInstance().addTextAlarm(mBleWriteResponse, mTextAlarmListener, setting);
            } else {
                VPOperateManager.getInstance().modifyTextAlarm(mBleWriteResponse, mTextAlarmListener, setting);
            }
        } else {
            Alarm2Setting setting = new Alarm2Setting(mHour, mMinute, repeatStatus, mScene, unRepeatDate, mIsOpen);
            if (!mIsNew) {
                setting.setAlarmId(mAlarmId);
            }
            if (mIsNew) {
                VPOperateManager.getInstance().addAlarm2(mBleWriteResponse, mAlarmListener, setting);
            } else {
                VPOperateManager.getInstance().modifyAlarm2(mBleWriteResponse, mAlarmListener, setting);
            }
        }
    }

    private void confirmDelete() {
        if (mIsNew) {
            return;
        }
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除闹钟")
                .setMessage("确定删除该闹钟吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteAlarm())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAlarm() {
        if (mIsTextAlarm) {
            TextAlarm2Setting setting = new TextAlarm2Setting();
            setting.setAlarmHour(mHour);
            setting.setAlarmMinute(mMinute);
            setting.setRepeatStatus(buildRepeatStatus());
            setting.setUnRepeatDate(mIsOnceMode ? mOnceDate : "0000-00-00");
            setting.setOpen(mIsOpen);
            setting.setContent(mContent);
            setting.setAlarmId(mAlarmId);
            VPOperateManager.getInstance().deleteTextAlarm(mBleWriteResponse, mTextAlarmListener, setting);
        } else {
            Alarm2Setting setting = new Alarm2Setting(mHour, mMinute, buildRepeatStatus(), mScene, mIsOnceMode ? mOnceDate : "0000-00-00", mIsOpen);
            setting.setAlarmId(mAlarmId);
            VPOperateManager.getInstance().deleteAlarm2(mBleWriteResponse, mAlarmListener, setting);
        }
    }

    private final IAlarm2DataListListener mAlarmListener = new IAlarm2DataListListener() {
        @Override
        public void onAlarmDataChangeListListener(AlarmData2 alarmData2) {
            EMultiAlarmOprate opt = alarmData2.getOprate();
            boolean success = opt == EMultiAlarmOprate.SETTING_SUCCESS || opt == EMultiAlarmOprate.CLEAR_SUCCESS;
            if (success) {
                showMsg("设置成功");
                finish();
            } else if (opt == EMultiAlarmOprate.ALARM_FULL) {
                showMsg("闹钟已满（最多 20 个）");
            } else {
                showMsg("设置失败");
            }
        }
    };

    private final ITextAlarmDataListener mTextAlarmListener = new ITextAlarmDataListener() {
        @Override
        public void onAlarmDataChangeListListener(TextAlarmData textAlarmData) {
            EMultiAlarmOprate opt = textAlarmData.getOprate();
            boolean success = opt == EMultiAlarmOprate.SETTING_SUCCESS || opt == EMultiAlarmOprate.CLEAR_SUCCESS;
            if (success) {
                showMsg("设置成功");
                finish();
            } else if (opt == EMultiAlarmOprate.ALARM_FULL) {
                showMsg("闹钟已满（最多 10 个）");
            } else {
                showMsg("设置失败");
            }
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                showMsg("指令发送失败");
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
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
        mSaveButton.setEnabled(mIsConnected);
        mSaveButton.setAlpha(mIsConnected ? 1.0f : 0.6f);
        mDeleteButton.setEnabled(mIsConnected);
        mDeleteButton.setAlpha(mIsConnected ? 1.0f : 0.6f);
    }

    private void applyRepeatStatus(String repeatStatus) {
        if (repeatStatus == null || repeatStatus.length() != 7) {
            return;
        }
        for (int i = 0; i < 7; i++) {
            char c = repeatStatus.charAt(6 - i);
            mRepeatDays[i] = c == '1';
        }
    }

    private String buildRepeatStatus() {
        char[] chars = new char[]{'0', '0', '0', '0', '0', '0', '0'};
        if (mIsOnceMode) {
            return new String(chars);
        }
        for (int i = 0; i < mRepeatDays.length; i++) {
            if (mRepeatDays[i]) {
                chars[6 - i] = '1';
            }
        }
        return new String(chars);
    }

    private String formatTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private String getTodayDate() {
        Calendar calendar = Calendar.getInstance();
        return String.format("%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
