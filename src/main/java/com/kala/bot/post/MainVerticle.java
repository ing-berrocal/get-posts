package com.kala.bot.post;

import com.kala.bot.post.api.PostHandler;
import com.kala.bot.post.api.WebhookHandler;
import com.kala.bot.post.service.CommentService;
import com.kala.bot.post.service.PostService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends VerticleBase {

    @Override
    public Future<?> start() {
        JsonObject cfg = config();
        String mongoUri   = cfg.getString("MONGO_URI",   env("MONGO_URI",   "mongodb://localhost:27017"));
        String mongoDb    = cfg.getString("MONGO_DB_NAME", env("MONGO_DB_NAME", "kala_db"));
        String commentsColl = cfg.getString("MONGO_COLLECTION", env("MONGO_COLLECTION", "kala_comments"));
        String fbVerifyToken = cfg.getString("FB_VERIFY_TOKEN", env("FB_VERIFY_TOKEN", "test_token"));
        String fbAppSecret   = cfg.getString("FB_APP_SECRET",   env("FB_APP_SECRET",   ""));
        String fbPageId      = cfg.getString("FB_PAGE_ID",      env("FB_PAGE_ID",      ""));
        int port = cfg.getInteger("HTTP_PORT", Integer.parseInt(env("HTTP_PORT", "8888")));

        JsonObject mongoConfig = new JsonObject()
            .put("connection_string", mongoUri)
            .put("db_name", mongoDb);

        MongoClient mongoClient = MongoClient.create(vertx, mongoConfig);

        CommentService commentService = new CommentService(mongoClient, commentsColl, fbPageId);
        PostService postService       = new PostService(mongoClient, "kala_posts");
        WebhookHandler webhookHandler = new WebhookHandler(commentService, fbVerifyToken, fbAppSecret);
        PostHandler postHandler       = new PostHandler(postService);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/webhook").handler(webhookHandler::handleChallenge);
        router.post("/webhook").handler(webhookHandler::handleEvent);
        router.post("/posts").handler(postHandler::createPost);
        router.get("/posts").handler(postHandler::listPosts);
        router.patch("/posts/:postId/inactivar").handler(postHandler::inactivatePost);
        router.get("/health").handler(ctx -> ctx.response()
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("status", "UP").encode()));

        // Index creation is fire-and-forget; doesn't block startup
        commentService.ensureIndexes()
            .onFailure(e -> System.err.println("Index creation failed (comments): " + e.getMessage()));
        postService.ensureIndexes()
            .onFailure(e -> System.err.println("Index creation failed (posts): " + e.getMessage()));

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(s -> System.out.println("HTTP server started on port " + port));
    }

    private String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return val != null ? val : defaultValue;
    }
}
