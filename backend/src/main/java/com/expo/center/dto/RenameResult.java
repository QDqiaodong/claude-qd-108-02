package com.expo.center.dto;

import com.expo.center.entity.Booth;

/** 改号结果：新旧号、连带换发了几张排期确认函、几张封道条。 */
public class RenameResult {

    public String oldCode;
    public String newCode;
    public Booth booth;
    public int bookingsUpdated;
    public int closuresUpdated;

    public RenameResult(String oldCode, String newCode, Booth booth,
                        int bookingsUpdated, int closuresUpdated) {
        this.oldCode = oldCode;
        this.newCode = newCode;
        this.booth = booth;
        this.bookingsUpdated = bookingsUpdated;
        this.closuresUpdated = closuresUpdated;
    }
}
