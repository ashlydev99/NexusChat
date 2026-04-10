package org.thoughtcrime.securesms.notifications;

public class ChatData {
    public final int accountId;
    public final int chatId;

    public ChatData(int accountId, int chatId) {
        this.accountId = accountId;
        this.chatId = chatId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChatData) {
            ChatData other = (ChatData) obj;
            return this.accountId == other.accountId && this.chatId == other.chatId;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * accountId + chatId;
    }
}