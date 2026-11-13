package org.openstreetmap.josm.plugins.dawarich;

public final class DawarichException extends Exception {
    public enum Category { AUTH, RATE_LIMIT, SERVER, CONNECTIVITY, REDIRECT, PROTOCOL, LIMIT, CANCELLED, DEADLINE }
    private final Category category;
    private final Integer retryAfterSeconds;
    DawarichException(Category c, String safeMessage, Integer retryAfter) { super(safeMessage); category=c; retryAfterSeconds=retryAfter; }
    DawarichException(Category c, String safeMessage) { this(c,safeMessage,null); }
    public Category category(){return category;}
    public boolean stopsBatch(){return category!=Category.AUTH;}
    public Integer retryAfterSeconds(){return retryAfterSeconds;}
    static DawarichException limit(){return new DawarichException(Category.LIMIT,"Batch limit exceeded");}
    static DawarichException cancelled(){return new DawarichException(Category.CANCELLED,"Request cancelled");}
    static DawarichException deadline(){return new DawarichException(Category.DEADLINE,"Batch deadline exceeded");}
    @Override public String toString(){return "DawarichException["+category+"]";}
}
