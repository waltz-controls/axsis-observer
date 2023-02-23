package com.github.ingvord.axsis.message;

import java.util.Map;

public class AxsisMoveAction {
    public String origin;
    public long id;
    public long parentId;
    public String target;
    public Payload payload;

    public static class Payload {
        public int port;
        public String ip;
        public String action;
        public Map<String, Float> value;
    }
}
