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
import com.veepoo.protocol.model.settings.Alarm2Setting;

import java.util.List;
import java.util.Set;

public class AlarmListAdapter extends BaseAdapter {
    private final Context context;
    private final List<Alarm2Setting> items;
    private final OnAlarmToggleListener listener;
    private Set<Integer> pendingIds;
    private boolean isConnected;

    public AlarmListAdapter(Context context, List<Alarm2Setting> items, OnAlarmToggleListener listener) {
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
    public Alarm2Setting getItem(int position) {
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
            convertView = LayoutInflater.from(context).inflate(R.layout.item_alarm_list, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Alarm2Setting setting = getItem(position);
        holder.time.setText(formatTime(setting.getAlarmHour(), setting.getAlarmMinute()));
        holder.repeat.setText(formatRepeat(setting));
        holder.arrow.setVisibility(View.VISIBLE);
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

    private String formatRepeat(Alarm2Setting setting) {
        String repeatStatus = setting.getRepeatStatus();
        if (TextUtils.isEmpty(repeatStatus) || "0000000".equals(repeatStatus)) {
            String date = setting.getUnRepeatDate();
            if (!TextUtils.isEmpty(date) && !"0000-00-00".equals(date)) {
                return "仅一次 " + date;
            }
            return "仅一次";
        }
        boolean[] days = parseRepeatStatus(repeatStatus);
        String[] labels = new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < days.length; i++) {
            if (days[i]) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(labels[i]);
            }
        }
        return builder.length() == 0 ? "仅一次" : builder.toString();
    }

    private boolean[] parseRepeatStatus(String repeatStatus) {
        boolean[] days = new boolean[7];
        if (repeatStatus == null || repeatStatus.length() != 7) {
            return days;
        }
        for (int i = 0; i < 7; i++) {
            char c = repeatStatus.charAt(6 - i);
            days[i] = c == '1';
        }
        return days;
    }

    static class ViewHolder {
        TextView time;
        TextView repeat;
        TextView arrow;
        SwitchView switchView;

        ViewHolder(View itemView) {
            time = itemView.findViewById(R.id.tv_alarm_time);
            repeat = itemView.findViewById(R.id.tv_alarm_repeat);
            arrow = itemView.findViewById(R.id.tv_alarm_arrow);
            switchView = itemView.findViewById(R.id.switch_alarm);
        }
    }

    public interface OnAlarmToggleListener {
        void onToggle(Alarm2Setting setting);
    }
}
