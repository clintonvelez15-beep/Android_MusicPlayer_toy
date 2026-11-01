package com.example.music_0921;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.animation.LinearInterpolator;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import android.widget.TextView;
import android.animation.ObjectAnimator;

import java.io.InputStream;


public class MainActivity extends AppCompatActivity {
    private Handler handler = new Handler(Looper.getMainLooper()); //任务调度员,指定任务跑在主线程以调用UI
    private MediaPlayer mediaPlayer;
    private View rootLayout;
    private ImageView bgImage;
    private SharedPreferences sp;
    private ImageButton btnPlay;//按钮升格为成员变量,对按钮对象调用
    private SeekBar seekBar;
    private java.util.ArrayList<Uri> songList = new java.util.ArrayList<>();
    private TextView tvTitle;
    private int currentIndex = 0;
    private int tick = 0;
    private ImageView ivDisc;
    private ObjectAnimator discAnim;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        rootLayout = findViewById(R.id.rootLayout);
        sp = getSharedPreferences("app", MODE_PRIVATE);


        mediaPlayer = new MediaPlayer();   // 空播放器
        seekBar = findViewById(R.id.seekBar);
        btnPlay = findViewById(R.id.btnPlay);
        bgImage = findViewById(R.id.bgImage);
        tvTitle = findViewById(R.id.tvTitle);
        ivDisc = findViewById(R.id.ivDisc);
        discAnim = ObjectAnimator.ofFloat(ivDisc, "rotation", 0f, 360f);
        discAnim.setDuration(8000);
        discAnim.setRepeatCount(-1);
        discAnim.setInterpolator(f -> f);



        // 恢复现场
        String saved = sp.getString("song_list", null);
        if (saved != null) {
            songList.clear();
            for (String line : saved.split("\n")) {
                if (!line.isEmpty()) songList.add(Uri.parse(line));
            }
        }
        currentIndex = sp.getInt("last_index", 0);                       // 恢复第几首
        if (currentIndex >= songList.size()) currentIndex = 0;           // 越界防护
        if (!songList.isEmpty()) {               // 有才装弹
            loadSong(songList.get(currentIndex));
            int savedPos = sp.getInt("last_position", 0);
            if (savedPos > 0) {
                mediaPlayer.seekTo(savedPos);
                seekBar.setProgress(savedPos);
            }
        } else {
            tvTitle.setText("歌单为空，请导入歌曲");   // 空态提示
        }


        //正常监听
        btnPlay.setOnClickListener(v -> {
            if(!mediaPlayer.isPlaying()) {
                if (songList.isEmpty()) return;
                mediaPlayer.start();
                discAnim.start();
                btnPlay.setImageResource(R.drawable.outline_motion_photos_paused_24);  // 图标换成
            }
            else {
                mediaPlayer.pause();
                discAnim.pause();
                btnPlay.setImageResource(R.drawable.round_arrow_right_24);  // 图标换成
            }
        });
        // 音乐播完时要干的事
        mediaPlayer.setOnCompletionListener( mp -> {
            discAnim.pause();
            if (songList.isEmpty()) return;
            currentIndex = (currentIndex + 1) % songList.size();
            playSong(songList.get(currentIndex));
        });

        //SeekBar
        handler.post(updateTask); //进onCreate启动第一次
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar s) {
                // 手指松手时调用
                mediaPlayer.seekTo(s.getProgress());
            }
        });

        //歌单
        findViewById(R.id.btnList).setOnClickListener(v -> {
            String[] titles = new String[songList.size()];
            for (int i = 0; i < songList.size(); i++) {
                titles[i] = getSongTitle(songList.get(i));
            }

            SongAdapter adapter = new SongAdapter(titles);

            // 弹窗：AlertDialog 装一个 ListView
            android.widget.ListView listView = new android.widget.ListView(this);
            listView.setAdapter(adapter);                        // 把翻译器装上


            // 充气 sheet 布局，往里塞 ListView
            android.view.View sheet = getLayoutInflater().inflate(R.layout.sheet_songs, null);
            android.widget.LinearLayout container = (android.widget.LinearLayout) sheet;
            container.addView(listView);

            final android.widget.PopupWindow pw = new android.widget.PopupWindow(
                    sheet,
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (400 * getResources().getDisplayMetrics().density),
                    true);

            android.widget.TextView btnClear = new android.widget.TextView(this);
            btnClear.setText("清空歌单");
            btnClear.setTextSize(14);
            btnClear.setTextColor(0xFF888888);
            btnClear.setPadding(32, 16, 32, 16);
            btnClear.setOnClickListener(x -> {
                songList.clear();
                currentIndex = 0;
                mediaPlayer.reset();
                updateIcon();
                seekBar.setProgress(0);
                tvTitle.setText("歌单为空，请导入歌曲");
                saveSongList();
                pw.dismiss();
            });
            container.addView(btnClear, 1);

            pw.showAtLocation(rootLayout, android.view.Gravity.BOTTOM, 0, 0);
            sheet.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up));

            // 条目点击：position 就是行号 = songList 下标，天然对应
            listView.setOnItemClickListener((parent, view, position, id) -> {
                currentIndex = position;                          // 记住当前是哪首
                playSong(songList.get(position));
                pw.dismiss();                   // 播它
            });

            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                songList.remove(position);                       // ① 删数据
                // 重建歌名数组（titles 是快照，跟着 songList 一起变）
                String[] newTitles = new String[songList.size()];
                for (int i = 0; i < songList.size(); i++) {
                    newTitles[i] = songList.get(i).getLastPathSegment();
                }
                // 知Adapter 重画（ArrayAdapter 不能换数组，用下面这个方法更省）
                listView.setAdapter(new SongAdapter(newTitles));
                // 存盘（变化即存——老原则）
                saveSongList();
                // 标纠偏（见下文，重头戏）
                fixIndexAfterRemove(position);
                return true;   // 长按事件被消费了，不会再触发点击
            });

        });

        //歌曲导入
        findViewById(R.id.btnImport).setOnClickListener(v -> {
            pickMusic.launch(new String[]{"audio/*"});
        });


        //背景更换
        findViewById(R.id.btnBg).setOnClickListener(v -> {
            pickImage.launch(new String[]{"image/*"});
        });
        //背景常驻
        restoreBackground();

    }

    private void restoreBackground() {
        String s = sp.getString("bg_uri", null);
        if (s == null) return;
        try {
            setBackgroundFromUri(Uri.parse(s));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class SongAdapter extends android.widget.BaseAdapter {
        private String[] data;
        SongAdapter(String[] data) { this.data = data; }

        @Override
        public int getCount() { return data.length; }
        @Override
        public Object getItem(int i) { return data[i]; }
        @Override
        public long getItemId(int i) { return i; }

        @Override
        public android.view.View getView(int i, android.view.View convertView, android.view.ViewGroup parent) {
            // 每个条目就是一个 TextView（系统布局拿来直接用）
            android.widget.TextView tv = new android.widget.TextView(MainActivity.this);
            tv.setText(data[i]);
            tv.setTextSize(16);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad / 2, pad, pad / 2);

            // ★ 高亮逻辑：正在播的行变色
            if (i == currentIndex) {
                tv.setTextColor(0xFFFF5722);      // 亮橙，醒目
                tv.setText("▶ " + data[i]);        // 加个播放标记（可选但效果拉满）
            } else {
                tv.setTextColor(0xFF333333);
            }
            return tv;
        }
    }

    private void loadSong(Uri uri) {          // 只准备，不唱
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();
            seekBar.setMax(mediaPlayer.getDuration());
            tvTitle.setText(getSongTitle(uri));
            loadCover(uri);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCover(Uri uri) {
        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null) {
                ivDisc.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
                ivDisc.setClipToOutline(true);
                ivDisc.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                try { mmr.release(); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try { mmr.release(); } catch (Exception ignored) {}
        ivDisc.setImageResource(R.drawable.ic_disc);
    }

    // 读  内嵌歌名
    private String getSongTitle(Uri uri) {
        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            String t = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (t != null && !t.isEmpty()) {
                return t;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return cleanTitle(uri);
    }

    private String cleanTitle(Uri u) {
        String seg = u.getLastPathSegment();
        int slash = seg.lastIndexOf('/');
        return slash >= 0 ? seg.substring(slash + 1) : seg;
    }

    private void playSong(Uri uri) {
        try {
            loadSong(uri);
            mediaPlayer.start();
            discAnim.start();
            updateIcon();                                 // 同步播放图标
            // 更新歌名显示（tvTitle.setText(...)）
        } catch (Exception e) {
            e.printStackTrace();
        }
        //上次播放
        sp.edit().putInt("last_index", currentIndex).apply();
    }

    private void saveSongList() {  // 存歌单
        StringBuilder sb = new StringBuilder();
        for (Uri u : songList) {
            sb.append(u.toString()).append("\n");
        }
        sp.edit().putString("song_list", sb.toString()).apply();
    }

    private void fixIndexAfterRemove(int removed) {  // 删歌后续
        if (songList.isEmpty()) {                 // 情况A：删光了
            currentIndex = 0;
            mediaPlayer.reset();
            updateIcon();
            tvTitle.setText("歌单为空，请导入歌曲");
            return;
        }
        if (removed < currentIndex) {
            currentIndex--;                       // 情况B：删的是前面的歌，下标前移
        } else if (removed == currentIndex) {     // 情况C：删的正是当前那首
            currentIndex = 0;
            loadSong(songList.get(0));
        }
    }

    private void updateIcon() {
        //播放完毕设置
        if (mediaPlayer.isPlaying()) {
            btnPlay.setImageResource(R.drawable.outline_motion_photos_paused_24);
        } else {
            btnPlay.setImageResource(R.drawable.round_arrow_right_24);
        }
    }

    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                int pos = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(pos);
                if (++tick % 2 == 0) {
                    sp.edit().putInt("last_position", pos).apply();
                }
            }
            handler.postDelayed(this, 250); //  500ms 循环，预约的调用
        }
    };

    private ActivityResultLauncher<String[]> pickImage = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                // uri选图\null
                // 权限申请\贴背景\存储
                if (uri != null) {                 // 用户取消时 uri 为 null
                    try {
                        // 解码贴背景
                        setBackgroundFromUri(uri);
                        // 长期读取权限
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        sp.edit().putString("bg_uri", uri.toString()).apply();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

    private ActivityResultLauncher<String[]> pickMusic = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                boolean wasEmpty = songList.isEmpty();

                for (Uri u : uris) {
                    if (!songList.contains(u)) {        // 查重闸：列表里没有才放行
                        songList.add(u);
                    } else {
                        android.util.Log.d("SONG", "跳过重复：" + u.getLastPathSegment());
                    }
                }
                android.util.Log.d("SONG", "导入后共有 " + songList.size() + " 首");
                saveSongList();

                if (wasEmpty && !songList.isEmpty()) {     // 从零到有
                    currentIndex = 0;
                    loadSong(songList.get(0));
                }
            });



    private void setBackgroundFromUri(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        bgImage.setImageBitmap(bitmap);
        is.close();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            sp.edit().putInt("last_position", mediaPlayer.getCurrentPosition()).apply();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            sp.edit().putInt("last_position", mediaPlayer.getCurrentPosition()).apply();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacks(updateTask);   // 退出时才拆循环
    }
}