package com.kala.bot.post.service;

import com.kala.bot.post.model.Post;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class PostService {
    private final MongoClient mongo;
    private final String collection;

    public PostService(MongoClient mongo, String collection) {
        this.mongo = mongo;
        this.collection = collection;
    }

    public Future<Void> ensureIndexes() {
        return Future.all(
            mongo.createIndexWithOptions(collection,
                new JsonObject().put("postId", 1),
                new IndexOptions().unique(true)),
            mongo.createIndex(collection, new JsonObject().put("estado", 1)),
            mongo.createIndex(collection, new JsonObject().put("fechaVencimiento", 1))
        ).mapEmpty();
    }

    public Future<Post> createPost(Post post) {
        return mongo.insert(collection, post.toJson())
            .map(id -> post);
    }

    public Future<List<Post>> listPosts(String estado, boolean incluirVencidos, int page, int size) {
        JsonObject query = new JsonObject();
        if (estado != null && !estado.isBlank()) {
            query.put("estado", estado);
        } else {
            query.put("estado", "activo");
        }
        if (!incluirVencidos) {
            query.put("fechaVencimiento", new JsonObject().put("$gte", Instant.now().toString()));
        }
        FindOptions opts = new FindOptions()
            .setSkip(page * size)
            .setLimit(size)
            .setSort(new JsonObject().put("fechaRegistro", -1));
        return mongo.findWithOptions(collection, query, opts)
            .map(docs -> docs.stream().map(Post::fromMongo).collect(Collectors.toList()));
    }

    public Future<Post> inactivatePost(String postId) {
        JsonObject query = new JsonObject().put("postId", postId);
        return mongo.findOne(collection, query, null)
            .compose(doc -> {
                if (doc == null) return Future.failedFuture("NOT_FOUND");
                Post p = Post.fromMongo(doc);
                if ("inactivo".equals(p.getEstado())) return Future.failedFuture("ALREADY_INACTIVE");
                String now = Instant.now().toString();
                JsonObject update = new JsonObject().put("$set", new JsonObject()
                    .put("estado", "inactivo")
                    .put("fechaInactivacion", now));
                return mongo.updateCollection(collection, query, update)
                    .compose(r -> mongo.findOne(collection, query, null))
                    .map(Post::fromMongo);
            });
    }
}
