package com.kala.bot.post.service;

import com.kala.bot.post.model.Comment;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

public class CommentService {
    private final MongoClient mongo;
    private final String collection;
    private final String pageId;

    public CommentService(MongoClient mongo, String collection, String pageId) {
        this.mongo = mongo;
        this.collection = collection;
        this.pageId = pageId;
    }

    public Future<Void> ensureIndexes() {
        return Future.all(
            mongo.createIndexWithOptions(collection,
                new JsonObject().put("commentId", 1),
                new IndexOptions().unique(true)),
            mongo.createIndex(collection, new JsonObject().put("postId", 1)),
            mongo.createIndex(collection, new JsonObject().put("createdTime", 1))
        ).mapEmpty();
    }

    public Future<Void> saveComment(Comment comment) {
        JsonObject filter = new JsonObject().put("commentId", comment.getCommentId());
        JsonObject update = new JsonObject().put("$setOnInsert", comment.toJson());
        UpdateOptions opts = new UpdateOptions().setUpsert(true);
        return mongo.updateCollectionWithOptions(collection, filter, update, opts)
            .mapEmpty();
    }

    public String getConfiguredPageId() {
        return pageId;
    }
}
