package com.github.ingvord.axsis.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class AxsisDoneMessage {
    public String origin;
    public long id;
    public long parentId;
    public String target;
    public String action;
    public String user;
    public JsonNode payload;
}
