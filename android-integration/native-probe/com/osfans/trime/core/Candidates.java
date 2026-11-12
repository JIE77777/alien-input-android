package com.osfans.trime.core;
public interface Candidates {
    final class Paged implements Candidates { public Paged(boolean a,boolean b,boolean c,int d,CandidateProto[] e){} }
    final class Bulk implements Candidates { public Bulk(int a,int b,CandidateProto[] c){} }
}
