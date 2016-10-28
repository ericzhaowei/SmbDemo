package com.ider.smbtest;

import android.graphics.drawable.Drawable;

import java.io.File;

/**
 * Created by Eric on 2016/7/18.
 */
class MyFile {
    public File mFile;
    public Drawable mIcon;
    public boolean mIsDir;

    public String mFileType;

    public boolean mIsSelected;
    public int mSelectedIndex;

    //SambaInfo
    private String mPath;
    private String mUsername;
    private String mPassword;
    private boolean mAnonymous;
    private String mMountpoint;
    private boolean mIsMount;


    public MyFile() {

    }

    public MyFile(String mPath) {
        this.mPath = mPath;
    }


    public Drawable getmIcon() {
        return mIcon;
    }

    public void setmIcon(Drawable mIcon) {
        this.mIcon = mIcon;
    }

    public boolean ismIsDir() {
        return mIsDir;
    }

    public void setmIsDir(boolean mIsDir) {
        this.mIsDir = mIsDir;
    }

    public String getmFileType() {
        return mFileType;
    }

    public void setmFileType(String mFileType) {
        this.mFileType = mFileType;
    }

    public boolean ismIsSelected() {
        return mIsSelected;
    }

    public void setmIsSelected(boolean mIsSelected) {
        this.mIsSelected = mIsSelected;
    }

    public String getmPath() {
        return mPath;
    }

    public void setmPath(String mPath) {
        this.mPath = mPath;
    }


    public int getmSelectedIndex() {
        return mSelectedIndex;
    }

    public void setmSelectedIndex(int mSelectedIndex) {
        this.mSelectedIndex = mSelectedIndex;
    }

    public String getmUsername() {
        return mUsername;
    }

    public void setmUsername(String mUsername) {
        this.mUsername = mUsername;
    }

    public String getmPassword() {
        return mPassword;
    }

    public void setmPassword(String mPassword) {
        this.mPassword = mPassword;
    }

    public boolean ismAnonymous() {
        return mAnonymous;
    }

    public void setmAnonymous(boolean mAnonymous) {
        this.mAnonymous = mAnonymous;
    }

    public String getmMountpoint() {
        return mMountpoint;
    }

    public void setmMountpoint(String mMountpoint) {
        this.mMountpoint = mMountpoint;
    }

    public boolean ismIsMount() {
        return mIsMount;
    }

    public void setmIsMount(boolean mIsMount) {
        this.mIsMount = mIsMount;
    }

}
