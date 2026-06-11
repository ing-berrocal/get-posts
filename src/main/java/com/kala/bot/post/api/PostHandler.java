package com.kala.bot.post.api;

import com.kala.bot.post.model.Post;
import com.kala.bot.post.service.PostService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class PostHandler {
    private final PostService postService;

    public PostHandler(PostService postService) {
        this.postService = postService;
    }

    public void createPost(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Body requerido");
            return;
        }
        Post post = Post.fromRequest(body);
        String err = post.validate();
        if (err != null) {
            badRequest(ctx, err);
            return;
        }
        postService.createPost(post)
            .onSuccess(created -> ctx.response().setStatusCode(201)
                .putHeader("content-type", "application/json")
                .end(created.toJson().encode()))
            .onFailure(e -> {
                if (e.getMessage() != null && e.getMessage().contains("E11000")) {
                    ctx.response().setStatusCode(409)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("error", "Post ya existe con ese postId").encode());
                } else {
                    System.err.println("Error creating post: " + e.getMessage());
                    internalError(ctx);
                }
            });
    }

    public void listPosts(RoutingContext ctx) {
        String estado = ctx.request().getParam("estado");
        boolean incluirVencidos = "true".equalsIgnoreCase(ctx.request().getParam("incluirVencidos"));
        int page = parseIntOrDefault(ctx.request().getParam("page"), 0);
        int size = parseIntOrDefault(ctx.request().getParam("size"), 20);
        postService.listPosts(estado, incluirVencidos, page, size)
            .onSuccess(posts -> {
                JsonArray arr = new JsonArray();
                posts.forEach(p -> arr.add(p.toJson()));
                ctx.response().setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(arr.encode());
            })
            .onFailure(e -> {
                System.err.println("Error listing posts: " + e.getMessage());
                internalError(ctx);
            });
    }

    public void inactivatePost(RoutingContext ctx) {
        String postId = ctx.pathParam("postId");
        postService.inactivatePost(postId)
            .onSuccess(post -> ctx.response().setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(post.toJson().encode()))
            .onFailure(e -> {
                String msg = e.getMessage();
                if ("NOT_FOUND".equals(msg)) {
                    ctx.response().setStatusCode(404)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("error", "Post no encontrado").encode());
                } else if ("ALREADY_INACTIVE".equals(msg)) {
                    ctx.response().setStatusCode(409)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("error", "Post ya está inactivo").encode());
                } else {
                    System.err.println("Error inactivating post: " + msg);
                    internalError(ctx);
                }
            });
    }

    private void badRequest(RoutingContext ctx, String msg) {
        ctx.response().setStatusCode(400)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", msg).encode());
    }

    private void internalError(RoutingContext ctx) {
        ctx.response().setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", "Error interno").encode());
    }

    private int parseIntOrDefault(String val, int defaultValue) {
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
