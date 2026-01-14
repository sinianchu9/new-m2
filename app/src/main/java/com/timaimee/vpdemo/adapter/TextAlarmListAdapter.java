package com.timaimee.vpdemo.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.activity.SwitchView;
import com.veepoo.protocol.model.settings.TextAlarm2Setting;

import java.util.List;
import java.util.Set;

public class TextAlarmListAdapter extends BaseAdapter {
    private final Context context;
    private final List<TextAlarm2Setting> items;
    private final OnTextAlarmToggleListener listener;
    private Set<Integer> pendingIds;
    private boolean isConnected;

    public TextAlarmListAdapter(Context context, List<TextAlarm2Setting> items, OnTextAlarmToggleListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    public void updateState(Set<Integer> pendingIds, boolean isConnected) {
        this.pendingIds = pendingIds;
        this.isConnected = isConnected;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public TextAlarm2Setting getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_text_alarm_list, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        TextAlarm2Setting setting = getItem(position);
        holder.time.setText(formatTime(setting.getAlarmHour(), setting.getAlarmMinute()));
        holder.content.setText(TextUtils.isEmpty(setting.getContent()) ? "无内容" : setting.getContent());
        holder.switchView.setOpened(setting.isOpen());
        boolean pending = pendingIds != null && pendingIds.contains(setting.getAlarmId());
        boolean enableSwitch = isConnected && !pending;
        holder.switchView.setEnabled(enableSwitch);
        holder.switchView.setAlpha(enableSwitch ? 1.0f : 0.5f);
        holder.switchView.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                setting.setOpen(true);
                if (listener != null) {
                    listener.onToggle(setting);
                }
            }

            @Override
            public void toggleToOff(SwitchView view) {
                setting.setOpen(false);
                if (listener != null) {
                    listener.onToggle(setting);
                }
            }
        });
        return convertView;
    }

    private String formatTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    static class ViewHolder {
        TextView time;
        TextView content;
        SwitchView switchView;

        ViewHolder(View itemView) {
            time = itemView.findViewById(R.id.tv_text_alarm_time);
            content = itemView.findViewById(R.id.tv_text_alarm_content);
            switchView = itemView.findViewById(R.id.switch_text_alarm);
        }
    }

    public interface OnTextAlarmToggleListener {
        void onToggle(TextAlarm2Setting setting);
    }
}
