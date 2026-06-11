package com.kala.bot.post.api;

import com.kala.bot.post.model.Comment;
import com.kala.bot.post.service.CommentService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class WebhookHandler {
    private final CommentService commentService;
    private final String verifyToken;
    private final String appSecret;

    public WebhookHandler(CommentService commentService, String verifyToken, String appSecret) {
        this.commentService = commentService;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    public void handleChallenge(RoutingContext ctx) {
        String mode = ctx.request().getParam("hub.mode");
        String token = ctx.request().getParam("hub.verify_token");
        String challenge = ctx.request().getParam("hub.challenge");
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            ctx.response().setStatusCode(200).end(challenge != null ? challenge : "");
        } else {
            ctx.response().setStatusCode(403)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", "Token de verificación inválido").encode());
        }
    }

    public void handleEvent(RoutingContext ctx) {
        String rawBody = ctx.body().asString();
        String signature = ctx.request().getHeader("X-Hub-Signature-256");
        if (appSecret != null && !appSecret.isBlank() && !isSignatureValid(rawBody, signature)) {
            ctx.response().setStatusCode(403)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", "Firma inválida").encode());
            return;
        }
        JsonObject body;
        try {
            body = new JsonObject(rawBody != null ? rawBody : "{}");
        } catch (Exception e) {
            ctx.response().setStatusCode(400)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", "Payload inválido").encode());
            return;
        }
        if (!"page".equals(body.getString("object"))) {
            ctx.response().setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("status", "ignored").encode());
            return;
        }
        String configuredPageId = commentService.getConfiguredPageId();
        JsonArray entries = body.getJsonArray("entry", new JsonArray());
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.getJsonObject(i);
            String entryPageId = entry.getString("id");
            if (configuredPageId != null && !configuredPageId.isBlank()
                && !configuredPageId.equals(entryPageId)) {
                continue;
            }
            JsonArray changes = entry.getJsonArray("changes", new JsonArray());
            for (int j = 0; j < changes.size(); j++) {
                JsonObject change = changes.getJsonObject(j);
                if (!"feed".equals(change.getString("field"))) continue;
                JsonObject value = change.getJsonObject("value");
                if (value == null) continue;
                if (!"comment".equals(value.getString("item"))) continue;
                Comment comment = Comment.fromWebhookValue(entryPageId, value);
                if (!comment.isValid()) continue;
                commentService.saveComment(comment)
                    .onFailure(err -> System.err.println(
                        "Error saving comment " + comment.getCommentId() + ": " + err.getMessage()));
            }
        }
        ctx.response().setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("status", "received").encode());
    }

    private boolean isSignatureValid(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            String computed = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
