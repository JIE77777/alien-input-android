package com.osfans.trime.core;
import java.util.concurrent.CountDownLatch;
public final class Rime {
    public static final CountDownLatch deployed=new CountDownLatch(1);
    public static volatile boolean successful;
    static { System.loadLibrary("rime_jni"); }
    public static void handleRimeMessage(int type,Object[] values){
        if(type==3 && values.length>0){
            if("success".equals(values[0])){successful=true;deployed.countDown();}
            else if("failure".equals(values[0]))deployed.countDown();
        }
    }
    public static native void startupRime(String shared,String user,String version,boolean full);
    public static native void exitRime();
    public static native boolean selectRimeSchema(String id);
    public static native String getCurrentRimeSchema();
    public static native boolean processRimeKey(int key,int mask);
    public static native void clearRimeComposition();
    public static native CandidateProto[] getRimeCandidates(int start,int limit);
    public static native boolean selectRimeCandidate(int index,boolean global);
    public static native CommitProto getRimeCommit();
}
