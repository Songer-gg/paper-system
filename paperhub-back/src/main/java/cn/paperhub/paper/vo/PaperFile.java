package cn.paperhub.paper.vo;

import java.io.*;

public final class PaperFile implements Closeable {
    public final InputStream input;
    public final long size;
    public final String title;
    public final long id;
    public final long ownerId;
    public final boolean sensitive;
    public PaperFile(InputStream input, long size, String title, long id, long ownerId, boolean sensitive) {
        this.input=input; this.size=size; this.title=title; this.id=id; this.ownerId=ownerId; this.sensitive=sensitive;
    }
    @Override public void close() throws IOException { input.close(); }
}

