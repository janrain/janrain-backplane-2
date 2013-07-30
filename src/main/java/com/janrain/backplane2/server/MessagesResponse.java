package com.janrain.backplane2.server;

import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class MessagesResponse {

    // - PUBLIC

    public MessagesResponse(String lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public void setLastMessageId(String lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public String getLastMessageId() {
        return lastMessageId;
    }

    public boolean moreMessages() {
        return moreMessages;
    }

    public void moreMessages(boolean moreMessages) {
        this.moreMessages = moreMessages;
    }

    public void addMessages(List<BackplaneMessage> messages) {
        this.messages.addAll(messages);
    }

    public boolean hasMessages() {
        return messages != null && ! messages.isEmpty();
    }

    public int messageCount() {
        return messages.size();
    }

    public Map<String, Object> asResponseFields(String serverName, boolean privileged) throws BackplaneServerException {
        List<Map<String,Object>> frames = new ArrayList<Map<String, Object>>();
        for (BackplaneMessage message : messages) {
            frames.add(message.asFrame(serverName, privileged));
        }

        Map<String, Object> messagesResponse = new HashMap<String, Object>();
        messagesResponse.put("nextURL", "https://" + serverName + "/v2/messages" +
                (!StringUtils.isBlank(lastMessageId) ? "?since=" + lastMessageId : ""));
        messagesResponse.put("moreMessages", moreMessages);
        messagesResponse.put("messages", frames);
        return messagesResponse;
    }

    public List<BackplaneMessage> getMessages() {
        return messages;
    }

    // - PRIVATE

    private List<BackplaneMessage> messages = new ArrayList<BackplaneMessage>();
    private String lastMessageId;
    private boolean moreMessages = false;
}
