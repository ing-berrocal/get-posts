package com.kala.bot.post;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestMainVerticle {

    private static final int TEST_PORT = 18888;
    private static final String VERIFY_TOKEN = "test_token_123";
    private static final String APP_SECRET = "test_secret_abc";
    private static WebClient client;

    @BeforeAll
    static void setUp(Vertx vertx, VertxTestContext ctx) {
        JsonObject config = new JsonObject()
            .put("HTTP_PORT", TEST_PORT)
            .put("FB_VERIFY_TOKEN", VERIFY_TOKEN)
            .put("FB_APP_SECRET", APP_SECRET)
            .put("FB_PAGE_ID", "12345")
            .put("MONGO_URI", "mongodb://localhost:27017")
            .put("MONGO_DB_NAME", "kala_test");

        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(new MainVerticle(), opts)
            .onComplete(ctx.succeeding(id -> {
                client = WebClient.create(vertx, new WebClientOptions().setDefaultPort(TEST_PORT));
                ctx.completeNow();
            }));
    }

    // ─── Health ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void health_check(VertxTestContext ctx) {
        client.get("/health").send()
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(200, res.statusCode());
                assertEquals("UP", res.bodyAsJsonObject().getString("status"));
                ctx.completeNow();
            })));
    }

    // ─── GET /webhook (challenge) ────────────────────────────────────────────

    @Test
    @Order(2)
    void webhook_challenge_valid(VertxTestContext ctx) {
        client.get("/webhook")
            .addQueryParam("hub.mode", "subscribe")
            .addQueryParam("hub.verify_token", VERIFY_TOKEN)
            .addQueryParam("hub.challenge", "CHALLENGE_CODE")
            .send()
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(200, res.statusCode());
                assertEquals("CHALLENGE_CODE", res.bodyAsString());
                ctx.completeNow();
            })));
    }

    @Test
    @Order(3)
    void webhook_challenge_invalid_token(VertxTestContext ctx) {
        client.get("/webhook")
            .addQueryParam("hub.mode", "subscribe")
            .addQueryParam("hub.verify_token", "wrong_token")
            .addQueryParam("hub.challenge", "CHALLENGE_CODE")
            .send()
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(403, res.statusCode());
                ctx.completeNow();
            })));
    }

    @Test
    @Order(4)
    void webhook_challenge_missing_mode(VertxTestContext ctx) {
        client.get("/webhook")
            .addQueryParam("hub.verify_token", VERIFY_TOKEN)
            .addQueryParam("hub.challenge", "CHALLENGE_CODE")
            .send()
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(403, res.statusCode());
                ctx.completeNow();
            })));
    }

    // ─── POST /webhook (events) ──────────────────────────────────────────────

    @Test
    @Order(5)
    void webhook_event_invalid_signature(VertxTestContext ctx) {
        JsonObject payload = new JsonObject().put("object", "page");
        client.post("/webhook")
            .putHeader("content-type", "application/json")
            .putHeader("X-Hub-Signature-256", "sha256=invalidsignature")
            .sendJson(payload)
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(403, res.statusCode());
                ctx.completeNow();
            })));
    }

    @Test
    @Order(6)
    void webhook_event_non_page_object_ignored(VertxTestContext ctx) throws Exception {
        JsonObject payload = new JsonObject().put("object", "user");
        String body = payload.encode();
        String signature = computeSignature(APP_SECRET, body);
        client.post("/webhook")
            .putHeader("content-type", "application/json")
            .putHeader("X-Hub-Signature-256", signature)
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(200, res.statusCode());
                assertEquals("ignored", res.bodyAsJsonObject().getString("status"));
                ctx.completeNow();
            })));
    }

    @Test
    @Order(7)
    void webhook_event_page_wrong_page_id_ignored(VertxTestContext ctx) throws Exception {
        JsonObject payload = new JsonObject()
            .put("object", "page")
            .put("entry", new io.vertx.core.json.JsonArray()
                .add(new JsonObject()
                    .put("id", "99999") // different from configured "12345"
                    .put("changes", new io.vertx.core.json.JsonArray())));
        String body = payload.encode();
        String signature = computeSignature(APP_SECRET, body);
        client.post("/webhook")
            .putHeader("content-type", "application/json")
            .putHeader("X-Hub-Signature-256", signature)
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(200, res.statusCode());
                assertEquals("received", res.bodyAsJsonObject().getString("status"));
                ctx.completeNow();
            })));
    }

    @Test
    @Order(8)
    void webhook_event_non_comment_item_ignored(VertxTestContext ctx) throws Exception {
        JsonObject payload = buildPagePayload("12345", "status", new JsonObject()
            .put("item", "status")
            .put("post_id", "post1"));
        String body = payload.encode();
        String signature = computeSignature(APP_SECRET, body);
        client.post("/webhook")
            .putHeader("content-type", "application/json")
            .putHeader("X-Hub-Signature-256", signature)
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(200, res.statusCode());
                ctx.completeNow();
            })));
    }

    // ─── POST /posts validation ──────────────────────────────────────────────

    @Test
    @Order(9)
    void post_create_missing_postId_returns_400(VertxTestContext ctx) {
        JsonObject body = new JsonObject()
            .put("descripcion", "Test description")
            .put("fechaVencimiento", Instant.now().plusSeconds(3600).toString());
        client.post("/posts")
            .putHeader("content-type", "application/json")
            .sendJson(body)
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(400, res.statusCode());
                assertNotNull(res.bodyAsJsonObject().getString("error"));
                ctx.completeNow();
            })));
    }

    @Test
    @Order(10)
    void post_create_description_over_200_chars_returns_400(VertxTestContext ctx) {
        JsonObject body = new JsonObject()
            .put("postId", "p_test")
            .put("descripcion", "x".repeat(201))
            .put("fechaVencimiento", Instant.now().plusSeconds(3600).toString());
        client.post("/posts")
            .putHeader("content-type", "application/json")
            .sendJson(body)
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(400, res.statusCode());
                ctx.completeNow();
            })));
    }

    @Test
    @Order(11)
    void post_create_fechaVencimiento_in_past_returns_400(VertxTestContext ctx) {
        JsonObject body = new JsonObject()
            .put("postId", "p_test")
            .put("descripcion", "Test description")
            .put("fechaVencimiento", Instant.now().minusSeconds(3600).toString());
        client.post("/posts")
            .putHeader("content-type", "application/json")
            .sendJson(body)
            .onComplete(ctx.succeeding(res -> ctx.verify(() -> {
                assertEquals(400, res.statusCode());
                ctx.completeNow();
            })));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String computeSignature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    private JsonObject buildPagePayload(String pageId, String field, JsonObject value) {
        return new JsonObject()
            .put("object", "page")
            .put("entry", new io.vertx.core.json.JsonArray()
                .add(new JsonObject()
                    .put("id", pageId)
                    .put("changes", new io.vertx.core.json.JsonArray()
                        .add(new JsonObject()
                            .put("field", field)
                            .put("value", value)))));
    }
}
