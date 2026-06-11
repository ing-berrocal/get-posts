package com.kala.bot.post.model;

import io.vertx.core.json.JsonObject;
import java.time.Instant;

public class Post {
    private String postId;
    private String descripcion;
    private Instant fechaRegistro;
    private Instant fechaVencimiento;
    private String estado;
    private Instant fechaInactivacion;

    private Post() {}

    public static Post fromRequest(JsonObject body) {
        Post p = new Post();
        p.postId = body.getString("postId");
        p.descripcion = body.getString("descripcion");
        String fv = body.getString("fechaVencimiento");
        if (fv != null) p.fechaVencimiento = Instant.parse(fv);
        p.estado = "activo";
        p.fechaRegistro = Instant.now();
        return p;
    }

    public static Post fromMongo(JsonObject doc) {
        Post p = new Post();
        p.postId = doc.getString("postId");
        p.descripcion = doc.getString("descripcion");
        String fr = doc.getString("fechaRegistro");
        if (fr != null) p.fechaRegistro = Instant.parse(fr);
        String fv = doc.getString("fechaVencimiento");
        if (fv != null) p.fechaVencimiento = Instant.parse(fv);
        p.estado = doc.getString("estado");
        String fi = doc.getString("fechaInactivacion");
        if (fi != null) p.fechaInactivacion = Instant.parse(fi);
        return p;
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject()
            .put("postId", postId)
            .put("descripcion", descripcion)
            .put("estado", estado);
        if (fechaRegistro != null) j.put("fechaRegistro", fechaRegistro.toString());
        if (fechaVencimiento != null) j.put("fechaVencimiento", fechaVencimiento.toString());
        if (fechaInactivacion != null) j.put("fechaInactivacion", fechaInactivacion.toString());
        return j;
    }

    public String validate() {
        if (postId == null || postId.isBlank()) return "postId es obligatorio";
        if (descripcion == null || descripcion.isBlank()) return "descripcion es obligatoria";
        if (descripcion.length() > 200) return "descripcion no puede superar 200 caracteres";
        if (fechaVencimiento == null) return "fechaVencimiento es obligatoria";
        if (!fechaVencimiento.isAfter(fechaRegistro)) return "fechaVencimiento debe ser posterior a fechaRegistro";
        return null;
    }

    public String getPostId() { return postId; }
    public String getDescripcion() { return descripcion; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public Instant getFechaVencimiento() { return fechaVencimiento; }
    public String getEstado() { return estado; }
    public Instant getFechaInactivacion() { return fechaInactivacion; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setFechaInactivacion(Instant fi) { this.fechaInactivacion = fi; }
}
