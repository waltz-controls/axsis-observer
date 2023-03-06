package com.github.ingvord.axsis.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class AxsisMoveMessage {
    public String origin;
    public long id;
    public long parentId;
    public String target;
    public JsonNode payload;
    public String user;
    public AxsisAction action;

    public static class Payload {
        public int port;
        public String ip;
        public String action;
        public Map<String, Float> value;
    }

    public static enum AxsisAction {
        move,
        qPOS,
        done,
        error
    }

    public long getId(){
        return id;
    }

    public long getParentId(){
        return parentId;
    }
}
