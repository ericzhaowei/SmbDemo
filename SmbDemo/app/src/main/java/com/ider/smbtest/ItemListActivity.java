package com.ider.smbtest;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;


public class ItemListActivity extends AppCompatActivity {

    static final String TAG = "SmbTest";
    private boolean anonymous = true; // 当前samba服务器是否开放

    private static void LOG(String str) {
        Log.d(TAG,str);
    }

    public ArrayList<MyFile> smbFiles;

    ListView listview;
    TextView title;
    SmbUtil smbUtil;
    FileAdapter adapter;
    SharedPreferences preferences;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constant.SCAN_UPDATE:
                    String host = (String) (msg.obj);
                    smbFiles.add(new MyFile(host));
                    adapter.notifyDataSetChanged();
                    break;
                case Constant.ITEM_CONNECT_FAILED:
                    Toast.makeText(ItemListActivity.this, "network not available, try again", Toast.LENGTH_SHORT).show();
                    break;
                case Constant.ITEM_WRONG_USER:
                    final String mPath = (String) msg.obj;
                    AlertDialog.Builder builder = new AlertDialog.Builder(ItemListActivity.this);
                    builder.setTitle("Login");
                    builder.setMessage("Please type in the username and password");
                    View view = LayoutInflater.from(ItemListActivity.this).inflate(R.layout.login_dialog, null, false);
                    final EditText editUser = (EditText) view.findViewById(R.id.username);
                    final EditText editPassword = (EditText) view.findViewById(R.id.password);
                    builder.setView(view);
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    });
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String username = editUser.getText().toString();
                            String password = editPassword.getText().toString();
                            MyFile file = new MyFile(mPath);
                            file.setmUsername(username);
                            file.setmPassword(password);
                            file.setmAnonymous(false);

                            smbUtil.getContentBySmbhost(file);
                            dialogInterface.dismiss();
                        }
                    });
                    Dialog dialog = builder.create();
                    dialog.show();
                    dialog.getWindow().setLayout(600, 400);
                    break;
                case Constant.ITEM_CONTENT:
                    smbFiles = (ArrayList<MyFile>) msg.obj;
                    adapter = new FileAdapter(smbFiles);
                    listview.setAdapter(adapter);

                    for(int i = 0; i < smbFiles.size(); i++) {
                        LOG(smbFiles.get(i).getmPath());
                    }
                    break;

            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);
        preferences = getSharedPreferences("smb_sp", MODE_PRIVATE);
        listview = (ListView) findViewById(R.id.ip_list);
        title = (TextView) findViewById(R.id.title);
        smbUtil = new SmbUtil(this, mHandler);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                MyFile file = smbFiles.get(i);

                final String mPath = file.getmPath();
                String host = mPath.split("/")[1];
                String username = preferences.getString("user" + host, null);
                String password = preferences.getString("password" + host, null);
                boolean anonymous = preferences.getBoolean("anonymous" + host, true);
                file.setmAnonymous(anonymous);
                file.setmUsername(username);
                file.setmPassword(password);
                if(mPath.startsWith(Constant.mDirSmb)) {
                    // 已经进入该samba服务器的子目录，因此不需要再进行mount，直接读取
                    if(mPath.substring(4).contains("/")) {
                        new Thread(new CheckMount(file)).start();

                    } else {
                        // 还未进入samba服务器，第一次点击该服务器，需要mount
                        smbUtil.stopSearch();
                        smbUtil.getContentBySmbhost(file);
                    }
                }


            }
        });
    }


    class CheckMount implements Runnable {
        MyFile file;
        public CheckMount(MyFile file) {
            this.file = file;
        }
        @Override
        public void run() {
            String mountResult = smbUtil.checkAndMount(file);
            LOG("mount over, result : " + mountResult);
        }
    }



    public void searchSmb(View view) {
        smbFiles = new ArrayList<>();
        adapter = new FileAdapter(smbFiles);
        new Thread() {
            @Override
            public void run() {
                smbUtil.searchSmbHost();
            }
        }.start();

        title.setText("searching...");
        listview.setAdapter(adapter);
    }


    class FileAdapter extends BaseAdapter {

        ArrayList<MyFile> list;
        public FileAdapter(ArrayList<MyFile> list) {
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int i) {
            return list.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            TextView text = new TextView(ItemListActivity.this);
            text.setText(list.get(i).getmPath());
            return text;
        }
    };


}
