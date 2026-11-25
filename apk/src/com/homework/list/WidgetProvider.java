package com.homework.list;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * 桌面小组件（抽卡模式）：一次只显示一项"下一个该写的作业"。
 * 「已完成」：标记当前项完成，推进到下一个未完成项；
 * 「跳过」：不标记，直接换下一项。
 * 每 30 分钟系统自动刷新拉取最新数据；新的一天（key 变化）自动清空完成状态。
 * 完成状态存在小组件自己的存储里，与 App 内勾选互不干扰。
 */
public class WidgetProvider extends AppWidgetProvider {

    private static final String[] DATA_URLS = {
        "https://tyeod-yomuh.github.io/homework-list/data/latest.json?v=",
        "https://raw.githubusercontent.com/tyeod-yomuh/homework-list/main/data/latest.json?v="
    };
    private static final String PREFS = "hw_widget_cache";
    public static final String ACTION_DONE = "com.homework.list.ACTION_DONE";
    public static final String ACTION_SKIP = "com.homework.list.ACTION_SKIP";
    public static final String ACTION_SYNC = "com.homework.list.ACTION_SYNC";

    /** 断网兜底：与 App 内置一致（最近一次作业），发布时同步更新 */
    private static final String FALLBACK_JSON =
        "{\"date\": \"2026-11-25\", \"label\": \"11月25日 周三\", \"key\": \"hw-list-2026-11-25\", \"photo\": \"\", \"items\": [{\"text\": \"课本课后 P85-86\", \"subject\": \"物理\", \"done\": false}, {\"text\": \"一张试卷写完\", \"subject\": \"化学\", \"done\": false}, {\"text\": \"基训写完后往后写三页\", \"subject\": \"数学\", \"done\": false}, {\"text\": \"《基训》P57-59（P58第1题、P59第1、3题不写）\", \"subject\": \"政治\", \"done\": false}]}";

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        refreshData(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();

        // App 端勾选状态同步过来：只按最新状态重绘，不重拉数据
        if (ACTION_SYNC.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, WidgetProvider.class);
            for (int id : mgr.getAppWidgetIds(cn)) {
                render(context, mgr, id);
            }
            return;
        }

        if (!ACTION_DONE.equals(action) && !ACTION_SKIP.equals(action)) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS, 0);
        String json = sp.getString("data", null);
        if (json == null) json = FALLBACK_JSON;

        try {
            JSONObject d = new JSONObject(json);
            JSONArray items = d.optJSONArray("items");
            int n = items == null ? 0 : items.length();
            int cur = sp.getInt("cur", 0);
            if (n > 0 && cur >= 0) {
                Set<String> done = new HashSet<String>(sp.getStringSet("done", new HashSet<String>()));
                if (ACTION_DONE.equals(action)) done.add(String.valueOf(cur));
                int next = findNext(done, cur, n);
                sp.edit().putStringSet("done", done).putInt("cur", next).apply();
            }
        } catch (Exception ignored) {
        }

        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, WidgetProvider.class);
        for (int id : mgr.getAppWidgetIds(cn)) {
            render(context, mgr, id);
        }
    }

    /** 从 cur+1 起找下一个未完成项；全部完成返回 -1 */
    private static int findNext(Set<String> done, int cur, int n) {
        if (done.size() >= n) return -1;
        for (int step = 1; step <= n; step++) {
            int i = (cur + step) % n;
            if (!done.contains(String.valueOf(i))) return i;
        }
        return -1;
    }

    /** 拉取最新数据（成功存缓存）；新的一天重置完成状态 */
    private static void refreshData(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
                String json = null;
                for (int u = 0; u < DATA_URLS.length && json == null; u++) {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(DATA_URLS[u] + System.currentTimeMillis()).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        if (c.getResponseCode() == 200) {
                            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line);
                            json = sb.toString();
                        }
                    } catch (Exception ignored) {
                        // 这个源失败，试下一个
                    }
                }
                if (json != null) {
                    try {
                        String newKey = new JSONObject(json).optString("key", "");
                        String oldKey = sp.getString("data_key", "");
                        SharedPreferences.Editor e = sp.edit().putString("data", json);
                        if (!newKey.equals(oldKey)) {
                            e.putString("data_key", newKey);
                            e.putStringSet("done", new HashSet<String>());
                            e.putInt("cur", 0);
                        }
                        e.apply();
                    } catch (Exception ignored) {
                    }
                }
                AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
                ComponentName cn = new ComponentName(ctx, WidgetProvider.class);
                for (int id : mgr.getAppWidgetIds(cn)) {
                    render(ctx, mgr, id);
                }
            }
        }).start();
    }

    /** 渲染：标题 + 当前项（大字） + 进度 + 两个按钮 */
    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
        String json = sp.getString("data", null);
        if (json == null) json = FALLBACK_JSON;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);
        try {
            JSONObject d = new JSONObject(json);
            JSONArray items = d.optJSONArray("items");
            int n = items == null ? 0 : items.length();
            Set<String> done = sp.getStringSet("done", new HashSet<String>());
            int cur = sp.getInt("cur", 0);

            // 完成状态变化后校正指针：
            // 1) cur 指向的项被完成了 → 推进到下一个未完成
            // 2) 曾全部完成(cur=-1)后又取消了勾选 → 恢复到第一个未完成
            if (n > 0 && done.size() < n && (cur < 0 || done.contains(String.valueOf(cur)))) {
                cur = findNext(done, cur, n);
                sp.edit().putInt("cur", cur).apply();
            }

            rv.setTextViewText(R.id.w_title, d.optString("label", "作业清单"));

            if (n == 0) {
                rv.setTextViewText(R.id.w_current, "今天没有作业");
                rv.setTextViewText(R.id.w_progress, "");
            } else if (cur < 0 || done.size() >= n) {
                rv.setTextViewText(R.id.w_current, "全部写完了 🎉");
                rv.setTextViewText(R.id.w_progress, n + " / " + n + " 项");
            } else {
                if (cur >= n) cur = 0;
                JSONObject it = items.optJSONObject(cur);
                String subj = it == null ? "" : it.optString("subject", "");
                String text = it == null ? "" : it.optString("text", "");
                rv.setTextViewText(R.id.w_current, "【" + subj + "】" + text);
                rv.setTextViewText(R.id.w_progress, "第 " + (cur + 1) + " / " + n + " 项 · 已完成 " + done.size() + " 项");
            }
        } catch (Exception e) {
            rv.setTextViewText(R.id.w_title, "作业清单");
            rv.setTextViewText(R.id.w_current, "点「跳过」试试");
            rv.setTextViewText(R.id.w_progress, "");
        }

        Intent doneI = new Intent(ctx, WidgetProvider.class);
        doneI.setAction(ACTION_DONE);
        rv.setOnClickPendingIntent(R.id.w_btn_done, PendingIntent.getBroadcast(ctx, 1, doneI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent skipI = new Intent(ctx, WidgetProvider.class);
        skipI.setAction(ACTION_SKIP);
        rv.setOnClickPendingIntent(R.id.w_btn_skip, PendingIntent.getBroadcast(ctx, 2, skipI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 点小组件空白区域（除两个按钮外的整面）：打开 App
        Intent open = new Intent(ctx, MainActivity.class);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        try {
            mgr.updateAppWidget(id, rv);
        } catch (Exception ignored) {
        }
    }
}
