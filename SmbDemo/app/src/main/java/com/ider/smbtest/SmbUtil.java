package com.ider.smbtest;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Exchanger;

import jcifs.smb.SmbFile;

/**
 * Created by ider-eric on 2016/7/13.
 */
public class SmbUtil {

    static boolean DEBUG = true;
    static String TAG = "SmbUtil";

    private final int THREAD_MAX = 16;
    private int startThread, overThread;

    private Context context;
    private Handler mHandler;
    private SharedPreferences preferences;
    private boolean searchInterrupt = false;

    public static final String SMB_MOUNTPOINT_ROOT = "/data/smb";
    public static final String SHELL_ROOT = "/data/etc";

    public static final String SHELL_PATH = "/data/etc/cifsmanager.sh";
    public static final String SHELL_LOG_PATH = "/data/etc/log";
    public static final String SHELL_HEAD = "#!/system/bin/sh";

    private Map<String, MyFile> MountSmbMap;

    private static void LOG(String str) {
        if (DEBUG) {
            Log.d(TAG, str);
        }
    }

    public SmbUtil(Context context, Handler mHandler) {
        this.context = context;
        this.mHandler = mHandler;
        preferences = context.getSharedPreferences("smb_sp", Context.MODE_PRIVATE);
        MountSmbMap = new HashMap<>();
    }

    public void stopSearch() {
        this.searchInterrupt = true;
    }

    public void searchSmbHost() {
        searchInterrupt = false;
        startThread = 0;
        overThread = 0;
        LOG("start search..");
        Vector<Vector<InetAddress>> vectorList = getsubnetAddress();
        for (int i = 0; i < vectorList.size(); i++) {
            if (searchInterrupt) return;
            Vector<InetAddress> vector = vectorList.get(i);
            for (int j = 0; j < vector.size(); ) {
                if (searchInterrupt) return;
                int activeThread = startThread - overThread;
                if (activeThread < THREAD_MAX) {
                    InetAddress pingIp = vector.get(j);
                    LOG("PING : " + pingIp.getHostAddress());
                    Thread scan = new Thread(new ConnectHost(pingIp));
                    scan.setPriority(10);
                    scan.start();
                    startThread++;
                    j++;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


    // 根据InterfaceAddress的长度获取到mask子网掩码
    public String calcMaskByPrefixLength(int length) {
        int mask = -1 << (32 - length);
        int partsNum = 4;
        int bitsOfPart = 8;
        int maskParts[] = new int[partsNum];
        int selector = 0x000000ff;

        for (int i = 0; i < maskParts.length; i++) {
            int pos = maskParts.length - 1 - i;
            maskParts[pos] = (mask >> (i * bitsOfPart)) & selector;
        }

        String result = "";
        result = result + maskParts[0];
        for (int i = 1; i < maskParts.length; i++) {
            result = result + "." + maskParts[i];
        }
        return result;
    }

    public ArrayList<String> getIpAndMask() {
        ArrayList<String> ipAndMastList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                List<InterfaceAddress> listIAddr = intf.getInterfaceAddresses();
                Iterator<InterfaceAddress> IAddrIterator = listIAddr.iterator();
                while (IAddrIterator.hasNext()) {
                    InterfaceAddress IAddr = IAddrIterator.next();
                    InetAddress inetAddress = IAddr.getAddress();
                    // 非127.0.0.1...等
                    if (!inetAddress.isLoopbackAddress()) {
                        String ip = inetAddress.getHostAddress();
                        String subnetmask = calcMaskByPrefixLength(IAddr.getNetworkPrefixLength());
                        String ipAndMask = ip + ";" + subnetmask;
                        ipAndMastList.add(ipAndMask);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }


        return ipAndMastList;
    }


    // 获取此设备所在网段下所有的ip地址
    public Vector<Vector<InetAddress>> getsubnetAddress() {
        int[] hostParts = new int[4];
        int[] maskParts = new int[4];
        int[] hostStart = new int[4];
        int[] hostEnd = new int[4];

        Vector<Vector<InetAddress>> vectorList = new Vector<>();
        ArrayList<String> ipAndMaskList = getIpAndMask();

        for (int i = 0; i < ipAndMaskList.size(); i++) {
            String[] maskAndIpSplit = ipAndMaskList.get(i).split(";");
            String host = maskAndIpSplit[0];
            String subnetmask = maskAndIpSplit[1];
            String[] split = host.split("\\.");
            if (split.length != 4) {
                continue;
            }

            hostParts[0] = Integer.parseInt(split[0]);
            hostParts[1] = Integer.parseInt(split[1]);
            hostParts[2] = Integer.parseInt(split[2]);
            hostParts[3] = Integer.parseInt(split[3]);

            split = subnetmask.split("\\.");
            maskParts[0] = Integer.parseInt(split[0]);
            maskParts[1] = Integer.parseInt(split[1]);
            maskParts[2] = Integer.parseInt(split[2]);
            maskParts[3] = Integer.parseInt(split[3]);

            hostStart[0] = maskParts[0] & hostParts[0];         //1&[0] = [0]    192
            hostStart[1] = maskParts[1] & hostParts[1];         //1&[1] = [1]    168
            hostStart[2] = maskParts[2] & hostParts[2];         //1&[2] = [2]    1
            hostStart[3] = maskParts[3] & hostParts[3];         //0&[3] = 0      0

            hostEnd[0] = hostParts[0] | (maskParts[0] ^ 0XFF);    //[0]|0 = [0]    192
            hostEnd[1] = hostParts[1] | (maskParts[1] ^ 0XFF);    //[1]|0 = [1]    168
            hostEnd[2] = hostParts[2] | (maskParts[2] ^ 0XFF);    //[2]|0 = [2]    1
            hostEnd[3] = hostParts[3] | (maskParts[3] ^ 0XFF);    //[3]|1 = 0xff   255


            Vector<InetAddress> vector = new Vector<>();
            for (int a = hostStart[0]; a <= hostEnd[0]; a++) {
                for (int b = hostStart[1]; b <= hostEnd[1]; b++) {
                    for (int c = hostStart[2]; c <= hostEnd[2]; c++) {
                        for (int d = hostStart[3]; d <= hostEnd[3]; d++) {
                            byte[] inetAddrhost = new byte[4];
                            inetAddrhost[0] = (byte) a;
                            inetAddrhost[1] = (byte) b;
                            inetAddrhost[2] = (byte) c;
                            inetAddrhost[3] = (byte) d;
                            try {
                                InetAddress inetAddress = InetAddress.getByAddress(inetAddrhost);
                                vector.add(inetAddress);
//                                LOG("getsubnetAddress : " + inetAddress.getHostAddress());
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            vectorList.add(vector);
        }

        return vectorList;
    }


    private class ConnectHost implements Runnable {

        private InetAddress host;   // ping的地址，如ping 192.168.1.250

        public ConnectHost(InetAddress host) {
            this.host = host;
        }

        @Override
        public void run() {
            if (ping(host.getHostAddress())) {
                if (searchInterrupt) return;
                smbSort(host);
            }
            overThread++;
        }

    }

    public boolean ping(String ip) {
        try {
            Socket server = new Socket();
            InetSocketAddress address = new InetSocketAddress(ip,
                    445);
            server.connect(address, 4000);
            server.close();
        } catch (UnknownHostException e) {
            try {
                Socket server = new Socket();
                InetSocketAddress address = new InetSocketAddress(ip,
                        139);
                server.connect(address, 4000);
                server.close();
            } catch (UnknownHostException e1) {
                return false;
            } catch (IOException e1) {
                return false;
            }
            return true;
        } catch (IOException e) {
            try {
                Socket server = new Socket();
                InetSocketAddress address = new InetSocketAddress(ip,
                        139);
                server.connect(address, 4000);
                server.close();
            } catch (UnknownHostException e1) {
                return false;
            } catch (IOException e1) {
                return false;
            }
            return true;
        }
        return true;
    }

    // 同步块，将SmbFile加入集合
    public synchronized void smbSort(InetAddress file) {
        // handler.send..

        LOG("ping OK : " + file.getHostAddress());
        Message msg = new Message();
        msg.what = Constant.SCAN_UPDATE;
        msg.obj = Constant.mDirSmb + "/" + file.getHostAddress();
        mHandler.sendMessage(msg);

    }


    public String checkAndMount(MyFile smbinfo) {
        String path = smbinfo.getmPath().substring(4);
        String[] smbSplit = path.split("/");
        String smbpath = "//"+smbSplit[0]+"/"+smbSplit[1];

        ArrayList<String> mountlist = SmbUtil.getMountMsg();
        ArrayList<String> cifslist = new ArrayList<>();
        ArrayList<String> mountpointlist = new ArrayList<>();
        for(String str : mountlist) {
            String[] split = str.split(" ");
            if(split[2].equals("cifs")) {
                cifslist.add(split[0].replace("\\"+"040", " ").replace("\\"+"134", "/"));
                mountpointlist.add(split[1]);
            }
        }
        if(cifslist.contains(smbpath)) {
            String mountpoint = mountpointlist.get(cifslist.indexOf(smbpath));
            smbinfo.setmMountpoint(mountpoint);

            File files = new File(mountpoint);
            LOG("file read:" + files.canRead()+" file exists:"+files.exists()+" file list:"+files.list().length);
            if(!files.exists() || !files.canRead() || files.list() == null || files.list().length == 0) {
                LOG("remote sharefolder can not read or null");
                umount(mountpoint);
            } else {
                smbinfo.setmIsMount(true);
                return mountpoint;
            }
        }

        String result = mount(smbinfo);
        if(result == null) {
            ArrayList<String> mountlist1 = getMountMsg();
            ArrayList<String> cifslist1 = new ArrayList<>();
            for(String str: mountlist1) {
                String split[] = str.split(" ");
                if(split[2].equals("cifs")) {
                    cifslist1.add(split[0].replace("\\"+"040", " ").replace("\\"+"134", "/"));
                }
            }
            if(!cifslist1.contains(smbpath)) {
                smbinfo.setmIsMount(false);
                Toast.makeText(context, "mount failed", Toast.LENGTH_SHORT).show();
                String mountPoint = smbinfo.getmMountpoint();
                SmbUtil.deleteMountPoint(mountPoint);
                return null;
            }

            String mountPoint = smbinfo.getmMountpoint();
            smbinfo.setmIsMount(true);
            MountSmbMap.put(mountPoint, smbinfo);
            Toast.makeText(context, "mount success", Toast.LENGTH_SHORT).show();
            return mountPoint;

        } else {
            Toast.makeText(context, "mount failed " + result, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    public String umount(String mountpoint){
        String allline;
        String mountPoint = mountpoint;
        if (!new File(mountPoint.replace("\"", "")).exists()){
            allline = "mount point lost";
            return allline;
        }
        try
        {
            String command = "busybox umount -fl "+mountPoint+" > "+SHELL_LOG_PATH+" 2>&1";
            LOG("umount command:"+command);
            String []cmd = {SHELL_HEAD,command};
            if (!ShellFileWrite(cmd)){
                allline = "write shell error";
                return allline;
            }
        }catch (Exception ex){
            System.out.println(ex.getMessage());
            allline = "write shell error";
            return allline;
        }

        PropertyReader.set("ctl.start", "cifsmanager");

        while(true)
        {
            String mount_rt = PropertyReader.getString("init.svc.cifsmanager");
            if(mount_rt != null && mount_rt.equals("stopped"))
            {
                allline = ShellLogRead();
                break;
            }

            try
            {
                Thread.sleep(1000);
            }catch(Exception ex){
                Log.e(TAG, "Exception: " + ex.getMessage());
                allline = "mount exception";
            }
        }
        if(allline == null){
            if(!deleteMountPoint(mountpoint)){
                allline = "mount point delete error";
            }
        }
        return allline;

    }


    private String mount(MyFile smbinfo) {
        String username = smbinfo.getmUsername();
        String password = smbinfo.getmPassword();
        boolean anonymous = smbinfo.ismAnonymous();
        String smbPath = "//" + smbinfo.getmPath().substring(4);

        String line = null;
        String allline = null;
        String mountPoint;
        if (smbinfo.getmMountpoint() != null) {
            mountPoint = smbinfo.getmMountpoint();
        } else {
            SimpleDateFormat sf = new SimpleDateFormat("yyMMddHHmmss");
            mountPoint = Constant.mSmbMountPoint + sf.format(new Date());
        }
        StringBuilder smbpathbuilder = new StringBuilder("\"");
        smbpathbuilder.append(smbPath);
        smbpathbuilder.append("\"");

        String newSmbpath = smbpathbuilder.toString();
        Log.i(TAG, "smbPath = " + newSmbpath);

        File shellDir = new File(SHELL_ROOT);
        if (!shellDir.exists()) {
            if (!shellDir.mkdirs()) {
                allline = "mkdir shell error";
                return allline;
            }
        }

        String result = null;
        File shellLog = new File(SHELL_LOG_PATH);
        if (!shellLog.exists()) {
            try {
                shellLog.createNewFile();
                shellLog.setExecutable(true);
            } catch (IOException e) {
                e.printStackTrace();
                result = "mount shell log fail";
                return result;
            }
        }

        if (anonymous) {
            try {
                String command = "busybox mount -t cifs -o iocharset=utf8,username=guest,uid=1000,gid=1015,file_mode=0775,dir_mode=0775,rw "
                        + newSmbpath + " " + mountPoint + " > " + SHELL_LOG_PATH + " 2>&1";
                LOG("mount command : " + command);
                String[] cmd = {SHELL_HEAD, command};
                if(!ShellFileWrite(cmd)) {
                    allline = "write shell file fail";
                    return allline;
                }
            } catch (Exception e) {
                e.printStackTrace();
                allline = "write shell file fail";
                return allline;
            }
        } else {
            try {
                String user = username;
                String pass = password;
                if(username.contains(" ")) {
                    StringBuilder userbuilder = new  StringBuilder("\"");
                    userbuilder.append(username);
                    userbuilder.append("\"");
                    user = userbuilder.toString();
                }
                if(password.contains(" ")) {
                    StringBuilder passbuilder = new StringBuilder("\"");
                    passbuilder.append(password);
                    passbuilder.append("\"");
                    pass = passbuilder.toString();
                }
                String command = "busybox mount -t cifs -o iocharset=utf8,username="+user+",password="+pass+
                        ",uid=1000,gid=1015,file_mode=0775,dir_mode=0775,rw "+newSmbpath+" "+mountPoint+" > "+SHELL_LOG_PATH+" 2>&1";
                LOG("mount command : " + command);
                String[] cmd = {SHELL_HEAD, command};
                if(!ShellFileWrite(cmd)) {
                    allline = "write shell file fail";
                    return allline;
                }
            } catch (Exception e) {
                e.printStackTrace();
                allline = "write shell file fail";
                return allline;
            }
        }

        if(!creatMountPoint(mountPoint)) {
            allline = "mount point create error";
            return allline;
        }

        int timeout = 0;
        while(true) {
            if(timeout > 2) {
                break;
            }

            allline = null;
            PropertyReader.set("ctl.start", "cifsmanager");
            try{
                Thread.sleep(3000);
            } catch(Exception e) {
                e.printStackTrace();
                allline = "mount error";
                timeout++;
                continue;
            }

            String mount_rt = PropertyReader.getString("init.svc.cifsmanager");
            LOG("mount runtime:" + mount_rt + " timeout:"+timeout);

            if(mount_rt != null && mount_rt.equals("running")) {
                allline = "connect timeout";
                PropertyReader.set("ctl.stop", "cifsmanager");
                timeout++;
                continue;
            }

            if(mount_rt != null && mount_rt.equals("stopped")) {
                allline = ShellLogRead();
                if(allline == null) {
                    break;
                }
            }
            timeout++;
        }

        if(allline != null) {
            deleteMountPoint(mountPoint);
        } else {
            smbinfo.setmMountpoint(mountPoint);
        }
        return allline;
    }


    public boolean creatMountPoint(String path){
        try{
            File root_smb = new File(Constant.mSmbMountPoint);
            if(!root_smb.exists()){
                if(!root_smb.mkdirs()){
                    return false;
                }else{
                    root_smb.setReadable(true,false);
                    root_smb.setExecutable(true, false);
                }
            }

            String abpath = new String(path).replace("\"", "");

            if(!new File(abpath).exists()){
                if(!new File(abpath).mkdirs()){
                    return false;
                }else{
                    LOG("creat mount point:"+abpath);
                }
            }
        }catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return true;

    }

    private static String ShellLogRead(){
        String result = null;
        File shellLog = new File(SHELL_LOG_PATH);
        if (!shellLog.exists()){
            try {
                shellLog.createNewFile();
                shellLog.setExecutable(true);

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                result = "create shell log fail";
                return result;
            }
        }

        try {
            BufferedReader buffrd = new BufferedReader(new FileReader(shellLog));
            String str = null;
            while((str=buffrd.readLine())!=null){
                System.out.println(str);
                if(result == null){
                    result = str+"\n";
                }else{
                    result = result + str + "\n";
                }
            }
            buffrd.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            result = "read shell log fail";
            return result;
        }
        return result;
    }

    private static boolean ShellFileWrite(String []cmd){

        File shell = new File(SHELL_PATH);
        if (!shell.exists()){
            try {
                shell.createNewFile();
                shell.setExecutable(true);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            }
        }

        try {
            BufferedWriter buffwr = new BufferedWriter(new FileWriter(shell));
            for (String str:cmd){
                buffwr.write(str);
                buffwr.newLine();
                buffwr.flush();
            }
            buffwr.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public ArrayList<String> callCommand(String command) {
        Process process;
        ArrayList<String> processList = new ArrayList<>();
        try {
            LOG("mount call command:" + command);
            process = Runtime.getRuntime().exec("ls");
            BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = input.readLine()) != null) {
                processList.add(line);
            }
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return processList;
    }

    public static boolean deleteMountPoint(String path){
        String abpath = new String(path).replace("\"", "");

        if(new File(abpath).exists()){
            if (!new File(abpath).delete()){
                return false;
            }else{
                LOG("delete mount point:"+abpath);
            }
        }
        return true;
    }

    public static ArrayList<String> getMountMsg() {
        String line;
        ArrayList<String> strlist = new ArrayList<>();
        try {
            Process pro = Runtime.getRuntime().exec("mount");
            BufferedReader br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            while ((line = br.readLine()) != null) {
                strlist.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return strlist;
    }



    public void getContentBySmbhost(MyFile file) {
        final String mPath = file.getmPath();
        final String username = file.getmUsername();
        final String password = file.getmPassword();
        final boolean anonymous = file.ismAnonymous();
        // host like SMB/IP
        new Thread(new Runnable() {
            @Override
            public void run() {
                String host = mPath.substring(4);
                if (!ping(host)) {
                    mHandler.sendEmptyMessage(Constant.ITEM_CONNECT_FAILED);
                    return;
                }
                try {
                    SmbFile smbfile;
                    if (anonymous) {
                        smbfile = new SmbFile("smb://guest:@" + host + "/");
                    } else {
                        smbfile = new SmbFile("smb://" + username + ":" + password + "@" + host + "/");
                    }

                    ArrayList<MyFile> contentPaths = new ArrayList<>();
                    for (String share : smbfile.list()) {
                        Log.i(TAG, share);
                        if (!share.endsWith("$")) {
                            String contentPath = Constant.mDirSmb + "/" + host + "/" + share;
                            contentPaths.add(new MyFile(contentPath));
                        }
                    }

                    if (!anonymous) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("user" + host, username);
                        editor.putString("password" + host, password);
                        editor.putBoolean("anonymous" + host, false);
                        editor.commit();
                    }

                    Message msg = new Message();
                    msg.what = Constant.ITEM_CONTENT;
                    msg.obj = contentPaths;
                    mHandler.sendMessage(msg);


                } catch (Exception e) {
                    if (e.getMessage().contains("unknown user name or bad password")) {
                        Message msg = new Message();
                        msg.obj = mPath;
                        msg.what = Constant.ITEM_WRONG_USER;
                        mHandler.sendMessage(msg);
                    }
                }
            }

        }).start();
    }


}
