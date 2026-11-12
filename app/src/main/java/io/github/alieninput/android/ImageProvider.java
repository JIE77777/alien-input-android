package io.github.alieninput.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, unexported provider. Android grants only explicitly shared URIs. */
public final class ImageProvider extends ContentProvider {
    public static final String AUTHORITY="io.github.alieninput.android.images";
    @Override public boolean onCreate() {return true;}
    private File file(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(!AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1||name==null||
           !name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png"))
            throw new FileNotFoundException("Invalid image URI");
        File f=new File(new File(getContext().getCacheDir(),"shared"),name);
        if(!f.isFile()) throw new FileNotFoundException("Image expired");
        return f;
    }
    @Override public String getType(Uri uri) {return "image/png";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try {
            File f=file(uri);
            String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(cols);
            Object[] values=new Object[cols.length];
            for(int i=0;i<cols.length;i++) {
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i])) values[i]="alien-message.png";
                else if(OpenableColumns.SIZE.equals(cols[i])) values[i]=f.length();
            }
            result.addRow(values);return result;
        } catch(FileNotFoundException e) {return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues v) {throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a) {throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a) {throw new UnsupportedOperationException();}
}
