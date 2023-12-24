package com.mrbreaknfix.config;


import com.google.gson.Gson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DMCconfig implements Serializable {
    // 0 means disabled
    public String sendingChannelID = "";
    public String previousChannelID = "";

    public String selectedAccountToken = "";
    // token: name
    public Map<String, String> accountTokens = new TreeMap<>();

    public List<String> listeningChannels = new ArrayList<>();

    public Map<String, String> cacheGuildNames = new TreeMap<>();
    public Map<String, String> cacheChannelNames = new TreeMap<>();

    public DMCconfig() {}
}
