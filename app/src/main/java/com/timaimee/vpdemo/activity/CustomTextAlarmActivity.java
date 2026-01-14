package com.timaimee.vpdemo.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.adapter.CustomTextAlarmAdapter;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.ITextAlarmDataListener;
import com.veepoo.protocol.model.datas.TextAlarmData;
import com.veepoo.protocol.model.enums.EMultiAlarmOprate;
import com.veepoo.protocol.model.settings.TextAlarm2Setting;

import java.util.ArrayList;
import java.util.List;

public class CustomTextAlarmActivity extends Activity implements CustomTextAlarmAdapter.OnAlarmActionListener {

    private ListView mListView;
    private TextView mTvConnectionStatus;
    private TextView mTvStatus;
    private TextView mTvEmpty;
    private CustomTextAlarmAdapter mAdapter;
    private List<TextAlarm2Setting> mAlarms = new ArrayList<>();
    private String mDeviceAddress;
    private boolean mIsConnected = true; // 假设已连接，实际应从Intent或全局状态获取

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_text_alarm);

        mDeviceAddress = getIntent().getStringExtra("deviceaddress");

        initViews();
        requestRead();
    }

    private void initViews() {
        mListView = findViewById(R.id.lv_text_alarms);
        mTvConnectionStatus = findViewById(R.id.tv_connection_status);
        mTvStatus = findViewById(R.id.tv_status);
        mTvEmpty = findViewById(R.id.tv_empty);

        mAdapter = new CustomTextAlarmAdapter(this, mAlarms, this);
        mListView.setAdapter(mAdapter);

        findViewById(R.id.fab_add).setOnClickListener(v -> showAddDialog(null));

        mTvConnectionStatus.setText("设备地址: " + mDeviceAddress);
    }

    private void requestRead() {
        updateStatus("正在读取闹钟...");
        VPOperateManager.getInstance().readTextAlarm(mWriteResponse, mTextAlarmListener);
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> mTvStatus.setText(status));
    }

    private void showAddDialog(@Nullable final TextAlarm2Setting existingSetting) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text_alarm, null);
        final TimePicker timePicker = view.findViewById(R.id.time_picker);
        final EditText etContent = view.findViewById(R.id.et_content);

        final CheckBox cbMon = view.findViewById(R.id.cb_mon);
        final CheckBox cbTue = view.findViewById(R.id.cb_tue);
        final CheckBox cbWed = view.findViewById(R.id.cb_wed);
        final CheckBox cbThu = view.findViewById(R.id.cb_thu);
        final CheckBox cbFri = view.findViewById(R.id.cb_fri);
        final CheckBox cbSat = view.findViewById(R.id.cb_sat);
        final CheckBox cbSun = view.findViewById(R.id.cb_sun);

        timePicker.setIs24HourView(true);

        if (existingSetting != null) {
            timePicker.setHour(existingSetting.getAlarmHour());
            timePicker.setMinute(existingSetting.getAlarmMinute());
            etContent.setText(existingSetting.getContent());

            String repeatStatus = existingSetting.getRepeatStatus();
            if (repeatStatus != null && repeatStatus.length() == 7) {
                cbSun.setChecked(repeatStatus.charAt(0) == '1');
                cbSat.setChecked(repeatStatus.charAt(1) == '1');
                cbFri.setChecked(repeatStatus.charAt(2) == '1');
                cbThu.setChecked(repeatStatus.charAt(3) == '1');
                cbWed.setChecked(repeatStatus.charAt(4) == '1');
                cbTue.setChecked(repeatStatus.charAt(5) == '1');
                cbMon.setChecked(repeatStatus.charAt(6) == '1');
            }
        } else {
            // 默认每天
            cbMon.setChecked(true);
            cbTue.setChecked(true);
            cbWed.setChecked(true);
            cbThu.setChecked(true);
            cbFri.setChecked(true);
            cbSat.setChecked(true);
            cbSun.setChecked(true);
        }

        new AlertDialog.Builder(this)
                .setTitle(existingSetting == null ? "添加文字闹钟" : "编辑文字闹钟")
                .setView(view)
                .setPositiveButton("确定", (dialog, which) -> {
                    String content = etContent.getText().toString();
                    if (TextUtils.isEmpty(content)) {
                        Toast.makeText(this, "请输入闹钟内容", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    TextAlarm2Setting setting = existingSetting != null ? existingSetting : new TextAlarm2Setting();
                    setting.setAlarmHour(timePicker.getHour());
                    setting.setAlarmMinute(timePicker.getMinute());
                    setting.setContent(content);
                    setting.setOpen(true);

                    StringBuilder sb = new StringBuilder();
                    sb.append(cbSun.isChecked() ? "1" : "0");
                    sb.append(cbSat.isChecked() ? "1" : "0");
                    sb.append(cbFri.isChecked() ? "1" : "0");
                    sb.append(cbThu.isChecked() ? "1" : "0");
                    sb.append(cbWed.isChecked() ? "1" : "0");
                    sb.append(cbTue.isChecked() ? "1" : "0");
                    sb.append(cbMon.isChecked() ? "1" : "0");
                    setting.setRepeatStatus(sb.toString());

                    setting.setUnRepeatDate("0000-00-00");

                    if (existingSetting == null) {
                        addAlarm(setting);
                    } else {
                        modifyAlarm(setting);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addAlarm(TextAlarm2Setting setting) {
        updateStatus("正在添加闹钟...");
        VPOperateManager.getInstance().addTextAlarm(mWriteResponse, mTextAlarmListener, setting);
    }

    private void modifyAlarm(TextAlarm2Setting setting) {
        updateStatus("正在修改闹钟...");
        VPOperateManager.getInstance().modifyTextAlarm(mWriteResponse, mTextAlarmListener, setting);
    }

    private void deleteAlarm(TextAlarm2Setting setting) {
        updateStatus("正在删除闹钟...");
        VPOperateManager.getInstance().deleteTextAlarm(mWriteResponse, mTextAlarmListener, setting);
    }

    @Override
    public void onToggle(TextAlarm2Setting setting) {
        setting.setOpen(!setting.isOpen());
        modifyAlarm(setting);
    }

    @Override
    public void onDelete(TextAlarm2Setting setting) {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定删除该闹钟吗？")
                .setPositiveButton("确定", (dialog, which) -> deleteAlarm(setting))
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onItemClick(TextAlarm2Setting setting) {
        showAddDialog(setting);
    }

    private final ITextAlarmDataListener mTextAlarmListener = new ITextAlarmDataListener() {
        @Override
        public void onAlarmDataChangeListListener(TextAlarmData textAlarmData) {
            runOnUiThread(() -> {
                EMultiAlarmOprate opt = textAlarmData.getOprate();
                if (opt == EMultiAlarmOprate.READ_SUCCESS ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAME_CRC ||
                        opt == EMultiAlarmOprate.READ_SUCCESS_SAVE ||
                        opt == EMultiAlarmOprate.SETTING_SUCCESS ||
                        opt == EMultiAlarmOprate.CLEAR_SUCCESS ||
                        opt == EMultiAlarmOprate.DEVICE_TEXT_ALARM_SWITCH_CHANGED ||
                        opt == EMultiAlarmOprate.DEVICE_ALARM_MODIFY ||
                        opt == EMultiAlarmOprate.DEVICE_ADD_ONE_TEXT_ALARM ||
                        opt == EMultiAlarmOprate.DEVICE_DELETE_ONE_TEXT_ALARM) {

                    mAlarms.clear();
                    mAlarms.addAll(textAlarmData.getTextAlarm2SettingList());
                    mAdapter.notifyDataSetChanged();
                    mTvEmpty.setVisibility(mAlarms.isEmpty() ? View.VISIBLE : View.GONE);
                    updateStatus("同步成功 (" + mAlarms.size() + ")");
                } else if (opt == EMultiAlarmOprate.ALARM_FULL) {
                    updateStatus("闹钟已满");
                    Toast.makeText(CustomTextAlarmActivity.this, "闹钟已满（最多10个）", Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus("操作失败: " + opt);
                }
            });
        }
    };

    private final IBleWriteResponse mWriteResponse = code -> {
        if (code != 0) {
            updateStatus("写入失败: " + code);
        }
    };
}
