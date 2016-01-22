package com.vivam.mediaplayerdemo;

import java.io.Serializable;

/**
 * Created by vivam on 1/21/16.
 */
public class MusicBean implements Serializable {

    private long id;

    private String title;

    private long duration;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
