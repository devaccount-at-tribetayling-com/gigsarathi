package com.gigsarathi.bot;

import java.util.List;

public interface PlatformMessageSender {

    void sendMessage(String userId, String platform, String text);

    void sendButtonMessage(String userId, String platform, String text, List<String> buttons);

    boolean supports(String platform);
}
