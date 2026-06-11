package com.kala.bot.post.model;

import io.vertx.core.json.JsonObject;
import java.time.Instant;

public class Comment {
    private String commentId;
    private String postId;
    private String pageId;
    private String userId;
    private String userName;
    private String message;
    private Instant createdTime;
    private Instant receivedAt;

    private Comment() {}

    public static Comment fromWebhookValue(String pageId, JsonObject value) {
        Comment c = new Comment();
        c.pageId = pageId;
        c.commentId = value.getString("comment_id");
        c.postId = value.getString("post_id");
        JsonObject from = value.getJsonObject("from");
        if (from != null) {
            c.userId = from.getString("id");
            c.userName = from.getString("name");
        }
        c.message = value.getString("message");
        Long ct = value.getLong("created_time");
        if (ct != null) c.createdTime = Instant.ofEpochSecond(ct);
        c.receivedAt = Instant.now();
        return c;
    }

    public boolean isValid() {
        return commentId != null && !commentId.isBlank()
            && postId != null && !postId.isBlank()
            && pageId != null && !pageId.isBlank();
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject()
            .put("commentId", commentId)
            .put("postId", postId)
            .put("pageId", pageId);
        if (userId != null) j.put("userId", userId);
        if (userName != null) j.put("userName", userName);
        if (message != null) j.put("message", message);
        if (createdTime != null) j.put("createdTime", createdTime.toString());
        if (receivedAt != null) j.put("receivedAt", receivedAt.toString());
        return j;
    }

    public String getCommentId() { return commentId; }
    public String getPostId() { return postId; }
    public String getPageId() { return pageId; }
}
