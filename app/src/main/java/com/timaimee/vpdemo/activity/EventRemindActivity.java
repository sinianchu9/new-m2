package com.timaimee.vpdemo.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
import com.veepoo.protocol.listener.data.ISocialMsgDataListener;
import com.veepoo.protocol.model.datas.FunctionSocailMsgData;
import com.veepoo.protocol.model.enums.EFunctionStatus;
import com.veepoo.protocol.model.enums.ESocailMsg;

import java.util.ArrayList;
import java.util.List;

public class EventRemindActivity extends AppCompatActivity {
    private LinearLayout mItemsContainer;
    private TextView mStatus;
    private TextView mConnectionHint;

    private String mDeviceAddress;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;
    private boolean mIsSetting = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private FunctionSocailMsgData mCurrentData;
    private final List<EventItem> mItems = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_remind);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
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
        mItemsContainer = findViewById(R.id.event_items_container);
        mStatus = findViewById(R.id.tv_event_status);
        mConnectionHint = findViewById(R.id.tv_connection_hint);
    }

    private void requestRead() {
        if (!mIsConnected) {
            updateStatus("状态：未连接");
            return;
        }
        updateStatus("状态：读取中");
        VPOperateManager.getInstance().readSocialMsg(mBleWriteResponse, mSocialListener);
    }

    private void renderItems() {
        mItemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < mItems.size(); i++) {
            EventItem item = mItems.get(i);
            View view = inflater.inflate(R.layout.item_event_switch, mItemsContainer, false);
            TextView label = view.findViewById(R.id.tv_event_label);
            SwitchView switchView = view.findViewById(R.id.switch_event);
            View divider = view.findViewById(R.id.view_divider);
            label.setText(item.label);
            switchView.setOpened(item.status == EFunctionStatus.SUPPORT_OPEN);
            switchView.setEnabled(mIsConnected && !mIsSetting);
            switchView.setAlpha(switchView.isEnabled() ? 1.0f : 0.5f);
            switchView.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
                @Override
                public void toggleToOn(SwitchView view) {
                    handleToggle(item, true);
                }

                @Override
                public void toggleToOff(SwitchView view) {
                    handleToggle(item, false);
                }
            });
            divider.setVisibility(i == mItems.size() - 1 ? View.GONE : View.VISIBLE);
            mItemsContainer.addView(view);
        }
    }

    private void handleToggle(EventItem item, boolean open) {
        if (!mIsConnected) {
            showMsg("未连接设备");
            renderItems();
            return;
        }
        if (mIsSetting) {
            return;
        }
        if (mCurrentData == null) {
            showMsg("请先读取设备状态");
            renderItems();
            return;
        }
        mIsSetting = true;
        updateStatus(item.label + "：正在设置");
        setStatus(mCurrentData, item.type, open ? EFunctionStatus.SUPPORT_OPEN : EFunctionStatus.SUPPORT_CLOSE);
        updateItemsFromData(mCurrentData);
        VPOperateManager.getInstance().settingSocialMsg(mBleWriteResponse, mSocialListener, mCurrentData);
        mHandler.postDelayed(() -> {
            if (mIsSetting) {
                VPOperateManager.getInstance().readSocialMsg(mBleWriteResponse, mSocialListener);
            }
        }, 1200);
        renderItems();
    }

    private void updateItemsFromData(FunctionSocailMsgData data) {
        mItems.clear();
        addItemIfSupport(data, ESocailMsg.PHONE, "来电");
        addItemIfSupport(data, ESocailMsg.SMS, "短信");
        addItemIfSupport(data, ESocailMsg.WECHAT, "微信");
        addItemIfSupport(data, ESocailMsg.QQ, "QQ");
        addItemIfSupport(data, ESocailMsg.DINGDING, "钉钉");
        addItemIfSupport(data, ESocailMsg.WHATS, "WhatsApp");
        addItemIfSupport(data, ESocailMsg.TWITTER, "X");
        addItemIfSupport(data, ESocailMsg.FACEBOOK, "Facebook");
    }

    private void addItemIfSupport(FunctionSocailMsgData data, ESocailMsg type, String label) {
        EFunctionStatus status = getStatus(data, type);
        if (status == null || status == EFunctionStatus.UNSUPPORT) {
            return;
        }
        mItems.add(new EventItem(type, label, status));
    }

    private EFunctionStatus getStatus(FunctionSocailMsgData data, ESocailMsg type) {
        switch (type) {
            case PHONE:
                return data.getPhone();
            case SMS:
                return data.getMsg();
            case WECHAT:
                return data.getWechat();
            case QQ:
                return data.getQq();
            case DINGDING:
                return data.getDingding();
            case WHATS:
                return data.getWhats();
            case TWITTER:
                return data.getTwitter();
            case FACEBOOK:
                return data.getFacebook();
            default:
                return null;
        }
    }

    private void setStatus(FunctionSocailMsgData data, ESocailMsg type, EFunctionStatus status) {
        switch (type) {
            case PHONE:
                data.setPhone(status);
                break;
            case SMS:
                data.setMsg(status);
                break;
            case WECHAT:
                data.setWechat(status);
                break;
            case QQ:
                data.setQq(status);
                break;
            case DINGDING:
                data.setDingding(status);
                break;
            case WHATS:
                data.setWhats(status);
                break;
            case TWITTER:
                data.setTwitter(status);
                break;
            case FACEBOOK:
                data.setFacebook(status);
                break;
            default:
                break;
        }
    }

    private final ISocialMsgDataListener mSocialListener = new ISocialMsgDataListener() {
        @Override
        public void onSocialMsgSupportDataChange(FunctionSocailMsgData socailMsgData) {
            handleSocialData(socailMsgData);
        }

        @Override
        public void onSocialMsgSupportDataChange2(FunctionSocailMsgData socailMsgData) {
            handleSocialData(socailMsgData);
        }
    };

    private void handleSocialData(FunctionSocailMsgData data) {
        if (data == null) {
            updateStatus("状态：读取失败");
            mIsSetting = false;
            renderItems();
            return;
        }
        mCurrentData = data;
        updateItemsFromData(data);
        if (mItems.isEmpty()) {
            updateStatus("状态：设备暂无可设置事件");
        } else if (mIsSetting) {
            updateStatus("状态：设置成功");
        } else {
            updateStatus("状态：已同步设备");
        }
        mIsSetting = false;
        renderItems();
    }

    private final IBleWriteResponse mBleWriteResponse = new IBleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code != Code.REQUEST_SUCCESS) {
                updateStatus("状态：指令发送失败");
                mIsSetting = false;
                renderItems();
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
        renderItems();
    }

    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
            updateConnectionUI();
            renderItems();
        }
    };

    private void updateConnectionUI() {
        mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
    }

    private void updateStatus(String text) {
        mStatus.setText(text);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static class EventItem {
        private final ESocailMsg type;
        private final String label;
        private final EFunctionStatus status;

        private EventItem(ESocailMsg type, String label, EFunctionStatus status) {
            this.type = type;
            this.label = label;
            this.status = status;
        }
    }
}
