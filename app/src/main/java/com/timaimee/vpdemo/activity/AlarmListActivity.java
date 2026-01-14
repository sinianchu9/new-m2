package com.timaimee.vpdemo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.adapter.AlarmListAdapter;
import com.timaimee.vpdemo.adapter.TextAlarmListAdapter;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlarmListActivity extends AppCompatActivity implements AlarmListAdapter.OnAlarmToggleListener {
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

    private ListView mListView;
    private TextView mEmptyView;
    private TextView mStatus;
    private TextView mConnectionHint;
    private Button mAddButton;
    private ListView mTextListView;
    private TextView mTextEmptyView;
    private Button mAddTextButton;

    private final List<Alarm2Setting> mAlarms = new ArrayList<>();
    private final Set<Integer> mPendingIds = new HashSet<>();
    private AlarmListAdapter mAdapter;
    private final List<TextAlarm2Setting> mTextAlarms = new ArrayList<>();
    private final Set<Integer> mPendingTextIds = new HashSet<>();
    private TextAlarmListAdapter mTextAdapter;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_list);
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
        mListView = findViewById(R.id.lv_alarm_list);
        mEmptyView = findViewById(R.id.tv_alarm_empty);
        mStatus = findViewById(R.id.tv_alarm_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
        mAddButton = findViewById(R.id.btn_add_alarm);
        mTextListView = findViewById(R.id.lv_text_alarm_list);
        mTextEmptyView = findViewById(R.id.tv_text_alarm_empty);
        mAddTextButton = findViewById(R.id.btn_add_text_alarm);
        mAdapter = new AlarmListAdapter(this, mAlarms, this);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView);
        mTextAdapter = new TextAlarmListAdapter(this, mTextAlarms, setting -> onTextToggle(setting));
        mTextListView.setAdapter(mTextAdapter);
        mTextListView.setEmptyView(mTextEmptyView);
    }

    private void bindEvents() {
        mAddButton.setOnClickListener(v -> openDetailForNew());
        mListView.setOnItemClickListener((parent, view, position, id) -> openDetailForEdit(mAlarms.get(position)));
        mAddTextButton.setOnClickListener(v -> openTextDetailForNew());
        mTextListView.setOnItemClickListener(
                (parent, view, position, id) -> openTextDetailForEdit(mTextAlarms.get(position)));
    }

    private void openDetailForNew() {
        Intent intent = new Intent(this, AlarmDetailActivity.class);
        intent.putExtra("deviceaddress", mDeviceAddress);
        intent.putExtra(EXTRA_IS_NEW, true);
        intent.putExtra(EXTRA_IS_TEXT, false);
        startActivity(intent);
    }

    private void openDetailForEdit(Alarm2Setting setting) {
        Intent intent = new Intent(this, AlarmDetailActivity.class);
        intent.putExtra("deviceaddress", mDeviceAddress);
        intent.putExtra(EXTRA_IS_NEW, false);
        intent.putExtra(EXTRA_IS_TEXT, false);
        intent.putExtra(EXTRA_ID, setting.getAlarmId());
        intent.putExtra(EXTRA_HOUR, setting.getAlarmHour());
        intent.putExtra(EXTRA_MINUTE, setting.getAlarmMinute());
        intent.putExtra(EXTRA_REPEAT, setting.getRepeatStatus());
        intent.putExtra(EXTRA_DATE, setting.getUnRepeatDate());
        intent.putExtra(EXTRA_SCENE, setting.getScene());
        intent.putExtra(EXTRA_OPEN, setting.isOpen());
        startActivity(intent);
    }

    private void openTextDetailForNew() {
        Intent intent = new Intent(this, AlarmDetailActivity.class);
        intent.putExtra("deviceaddress", mDeviceAddress);
        intent.putExtra(EXTRA_IS_NEW, true);
        intent.putExtra(EXTRA_IS_TEXT, true);
        startActivity(intent);
    }

    private void openTextDetailForEdit(TextAlarm2Setting setting) {
        Intent intent = new Intent(this, AlarmDetailActivity.class);
        intent.putExtra("deviceaddress", mDeviceAddress);
        intent.putExtra(EXTRA_IS_NEW, false);
        intent.putExtra(EXTRA_IS_TEXT, true);
        intent.putExtra(EXTRA_ID, setting.getAlarmId());
        intent.putExtra(EXTRA_HOUR, setting.getAlarmHour());
        intent.putExtra(EXTRA_MINUTE, setting.getAlarmMinute());
        intent.putExtra(EXTRA_REPEAT, setting.getRepeatStatus());
        intent.putExtra(EXTRA_DATE, setting.getUnRepeatDate());
        intent.putExtra(EXTRA_SCENE, setting.getScene());
        intent.putExtra(EXTRA_OPEN, setting.isOpen());
        intent.putExtra(EXTRA_CONTENT, setting.getContent());
        startActivity(intent);
    }

    private void requestRead() {
        if (!mIsConnected) {
            updateStatus("状态：未连接");
            return;
        }
        android.util.Log.d("AlarmListActivity", "开始读取闹钟数据...");
        updateStatus("状态：读取中");

        // 使用匿名内部类（模仿SDK Demo）
        VPOperateManager.getInstance().readAlarm2(mBleWriteResponse, new IAlarm2DataListListener() {
            @Override
            public void onAlarmDataChangeListListener(AlarmData2 alarmData2) {
                android.util.Log.d("AlarmListActivity", "收到闹钟数据回调，状态：" + alarmData2.getOprate());
                runOnUiThread(() -> {
                    EMultiAlarmOprate opt = alarmData2.getOprate();
                    boolean isReadOk = opt == EMultiAlarmOprate.READ_SUCCESS ||
                            opt == EMultiAlarmOprate.READ_SUCCESS_SAME_CRC ||
                            opt == EMultiAlarmOprate.READ_SUCCESS_SAVE;
                    boolean isSetOk = opt == EMultiAlarmOprate.SETTING_SUCCESS ||
                            opt == EMultiAlarmOprate.CLEAR_SUCCESS;
                    if (isReadOk || isSetOk) {
                        mAlarms.clear();
                        mAlarms.addAll(alarmData2.getAlarm2SettingList());
                        if (isSetOk) {
                            updateStatus("状态：设置成功");
                        } else {
                            updateStatus("状态：已同步 " + mAlarms.size() + " 个闹钟");
                        }
                        mAdapter.notifyDataSetChanged();
                    } else if (opt == EMultiAlarmOprate.SETTING_FAIL || opt == EMultiAlarmOprate.CLEAR_FAIL) {
                        updateStatus("状态：设置失败");
                    } else {
                        updateStatus("状态：读取失败 - " + opt);
                    }
                    mPendingIds.clear();
                    mAdapter.updateState(mPendingIds, mIsConnected);
                });
            }
        });
        android.util.Log.d("AlarmListActivity", "readAlarm2调用完毕");

        VPOperateManager.getInstance().readTextAlarm(mBleWriteResponse, new ITextAlarmDataListener() {
            @Override
            public void onAlarmDataChangeListListener(TextAlarmData textAlarmData) {
                android.util.Log.d("AlarmListActivity", "收到文字闹钟数据回调，状态：" + textAlarmData.getOprate());
                runOnUiThread(() -> {
                    EMultiAlarmOprate opt = textAlarmData.getOprate();
                    boolean isReadOk = opt == EMultiAlarmOprate.READ_SUCCESS ||
                            opt == EMultiAlarmOprate.READ_SUCCESS_SAME_CRC ||
                            opt == EMultiAlarmOprate.READ_SUCCESS_SAVE;
                    boolean isSetOk = opt == EMultiAlarmOprate.SETTING_SUCCESS ||
                            opt == EMultiAlarmOprate.CLEAR_SUCCESS;
                    if (isReadOk || isSetOk) {
                        mTextAlarms.clear();
                        mTextAlarms.addAll(textAlarmData.getTextAlarm2SettingList());
                        mTextAdapter.notifyDataSetChanged();
                    }
                    mPendingTextIds.clear();
                    mTextAdapter.updateState(mPendingTextIds, mIsConnected);
                });
            }
        });
        android.util.Log.d("AlarmListActivity", "readTextAlarm调用完毕");
    }

    // 保留成员变量定义供onToggle等方法使用
    private final IAlarm2DataListListener mAlarmListener = new IAlarm2DataListListener() {
        @Override
        public void onAlarmDataChangeListListener(AlarmData2 alarmData2) {
            android.util.Log.d("AlarmListActivity", "收到闹钟数据回调(toggle)，状态：" + alarmData2.getOprate());
            runOnUiThread(() -> {
                EMultiAlarmOprate opt = alarmData2.getOprate();
                boolean isReadOk = opt == EMultiAlarmOprate.READ_SUCCESS ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAME_CRC ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAVE;
                boolean isSetOk = opt == EMultiAlarmOprate.SETTING_SUCCESS ||
                        opt == EMultiAlarmOprate.CLEAR_SUCCESS;
                if (isReadOk || isSetOk) {
                    mAlarms.clear();
                    mAlarms.addAll(alarmData2.getAlarm2SettingList());
                    if (isSetOk) {
                        updateStatus("状态：设置成功");
                    } else {
                        updateStatus("状态：已同步 " + mAlarms.size() + " 个闹钟");
                    }
                    mAdapter.notifyDataSetChanged();
                } else if (opt == EMultiAlarmOprate.SETTING_FAIL || opt == EMultiAlarmOprate.CLEAR_FAIL) {
                    updateStatus("状态：设置失败");
                } else {
                    updateStatus("状态：读取失败 - " + opt);
                }
                mPendingIds.clear();
                mAdapter.updateState(mPendingIds, mIsConnected);
            });
        }
    };

    private final ITextAlarmDataListener mTextAlarmListener = new ITextAlarmDataListener() {
        @Override
        public void onAlarmDataChangeListListener(TextAlarmData textAlarmData) {
            android.util.Log.d("AlarmListActivity", "收到文字闹钟数据回调(toggle)，状态：" + textAlarmData.getOprate());
            runOnUiThread(() -> {
                EMultiAlarmOprate opt = textAlarmData.getOprate();
                boolean isReadOk = opt == EMultiAlarmOprate.READ_SUCCESS ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAME_CRC ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAVE;
                boolean isSetOk = opt == EMultiAlarmOprate.SETTING_SUCCESS ||
                        opt == EMultiAlarmOprate.CLEAR_SUCCESS;
                if (isReadOk || isSetOk) {
                    mTextAlarms.clear();
                    mTextAlarms.addAll(textAlarmData.getTextAlarm2SettingList());
                    mTextAdapter.notifyDataSetChanged();
                }
                mPendingTextIds.clear();
                mTextAdapter.updateState(mPendingTextIds, mIsConnected);
            });
        }
    };

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            android.util.Log.d("AlarmListActivity", "BleWriteResponse code=" + code);
            if (code != Code.REQUEST_SUCCESS) {
                updateStatus("状态：指令发送失败");
            }
        }
    };

    @Override
    public void onToggle(Alarm2Setting setting) {
        if (!mIsConnected) {
            showMsg("未连接设备");
            mAdapter.updateState(mPendingIds, mIsConnected);
            return;
        }
        mPendingIds.add(setting.getAlarmId());
        mAdapter.updateState(mPendingIds, mIsConnected);
        updateStatus("状态：正在设置");
        VPOperateManager.getInstance().modifyAlarm2(mBleWriteResponse, mAlarmListener, setting);
    }

    private void onTextToggle(TextAlarm2Setting setting) {
        if (!mIsConnected) {
            showMsg("未连接设备");
            mTextAdapter.updateState(mPendingTextIds, mIsConnected);
            return;
        }
        mPendingTextIds.add(setting.getAlarmId());
        mTextAdapter.updateState(mPendingTextIds, mIsConnected);
        updateStatus("状态：正在设置");
        VPOperateManager.getInstance().modifyTextAlarm(mBleWriteResponse, mTextAlarmListener, setting);
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
        mAdapter.updateState(mPendingIds, mIsConnected);
        mTextAdapter.updateState(mPendingTextIds, mIsConnected);
    }

    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
            updateConnectionUI();
            mAdapter.updateState(mPendingIds, mIsConnected);
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
        mAddButton.setEnabled(mIsConnected);
        mAddButton.setAlpha(mIsConnected ? 1.0f : 0.6f);
        mAddTextButton.setEnabled(mIsConnected);
        mAddTextButton.setAlpha(mIsConnected ? 1.0f : 0.6f);
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
