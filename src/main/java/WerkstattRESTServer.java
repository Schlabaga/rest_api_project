import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WerkstattRESTServer {

    private static final int PORT = 8080;
    
    // Simuler une Base de Données en mémoire
    private static final Map<Long, WorkOrder> DATABASE = Collections.synchronizedMap(new HashMap<>());
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    public static void main(String[] args) {
        try {
            // Données de test
            initDummyData();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            HttpContext context = server.createContext("/");
            context.setHandler(new RequestHandler());

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            
            System.out.println("Werkstatt API Server auf Port " + PORT + " gestartet.");
            System.out.println("Resources: /workorders und /workorders/{id}");
            System.out.println("Stoppe Web-Server durch beliebige Eingabe...");
            
            Scanner sc = new Scanner(System.in);
            sc.next();
            sc.close();
            server.stop(0);
            System.out.println("Web-Server gestoppt.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initDummyData() {
        createOrder("SB-XY-123", "Bremsscheiben wechseln", "PENDING", "2025-10-15");
        createOrder("KL-AA-007", "Ölwechsel", "IN_PROGRESS", "2025-09-01");
    }
    
    private static WorkOrder createOrder(String lp, String desc, String status, String due) {
        long id = ID_GENERATOR.getAndIncrement();
        WorkOrder wo = new WorkOrder(id, lp, desc, status, due);
        DATABASE.put(id, wo);
        return wo;
    }

    // --- CLASSE DE MODÈLE (POJO) ---
    static class WorkOrder {
        long id;
        String licensePlate;
        String description;
        String status;
        String dueDate;

        public WorkOrder(long id, String licensePlate, String description, String status, String dueDate) {
            this.id = id;
            this.licensePlate = licensePlate;
            this.description = description;
            this.status = status;
            this.dueDate = dueDate;
        }

        // Conversion manuelle en JSON pour l'affichage
        public String toJson() {
            return String.format(
                "{\"id\":%d, \"licensePlate\":\"%s\", \"description\":\"%s\", \"status\":\"%s\", \"dueDate\":\"%s\"}",
                id, licensePlate, description, status, dueDate
            );
        }
    }

    // --- LE HANDLER PRINCIPAL ---
    static class RequestHandler implements HttpHandler {

        // HTTP Status Codes
        private static final int OK = 200;
        private static final int CREATED = 201;
        private static final int NO_CONTENT = 204;
        private static final int BAD_REQUEST = 400;
        private static final int NOT_FOUND = 404;
        private static final int METHOD_NOT_ALLOWED = 405;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // Log de la requête
            System.out.println(MessageFormat.format("Request: {0} {1}", method, path));

            // Lecture du body (si présent)
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // --- ROUTING (Aiguillage) ---
            
            // 1. Collection: /workorders
            if (path.equals("/workorders")) {
                switch (method) {
                    case "GET":
                        handleGetCollection(exchange);
                        break;
                    case "POST":
                        handlePost(exchange, body);
                        break;
                    default:
                        sendError(exchange, METHOD_NOT_ALLOWED, "Method not allowed. Use GET or POST.");
                }
            } 
            // 2. Item: /workorders/{id}
            else if (path.matches("/workorders/\\d+")) {
                // Extraction de l'ID depuis l'URL
                long id = extractIdFromPath(path);
                
                if (id == -1) {
                    sendError(exchange, BAD_REQUEST, "Invalid ID format");
                    return;
                }

                switch (method) {
                    case "GET":
                        handleGetItem(exchange, id);
                        break;
                    case "PUT":
                        handlePut(exchange, id, body);
                        break;
                    case "DELETE":
                        handleDelete(exchange, id);
                        break;
                    default:
                        sendError(exchange, METHOD_NOT_ALLOWED, "Method not allowed. Use GET, PUT or DELETE.");
                }
            } 
            // 3. Route inconnue
            else {
                sendError(exchange, NOT_FOUND, "Endpoint not found");
            }
        }

        // --- MÉTHODES SPÉCIFIQUES (Logique métier) ---

        private void handleGetCollection(HttpExchange exchange) throws IOException {
            // Transformer la Map en liste JSON
            String jsonResponse = DATABASE.values().stream()
                .map(WorkOrder::toJson)
                .collect(Collectors.joining(",", "[", "]"));
            
            sendJson(exchange, OK, jsonResponse);
        }

        private void handlePost(HttpExchange exchange, String body) throws IOException {
            // Parsing manuel (Attention: fragile sans librairie JSON)
            String licensePlate = extractJsonValue(body, "licensePlate");
            String description = extractJsonValue(body, "description");
            String status = extractJsonValue(body, "status");
            String dueDate = extractJsonValue(body, "dueDate");

            if (licensePlate == null || description == null) {
                sendError(exchange, BAD_REQUEST, "Missing required fields (licensePlate, description)");
                return;
            }

            // Création et sauvegarde
            WorkOrder newOrder = createOrder(licensePlate, description, status, dueDate);

            // Headers de réponse
            exchange.getResponseHeaders().add("Location", "/workorders/" + newOrder.id);
            sendJson(exchange, CREATED, newOrder.toJson());
        }

        private void handleGetItem(HttpExchange exchange, long id) throws IOException {
            WorkOrder order = DATABASE.get(id);
            if (order != null) {
                sendJson(exchange, OK, order.toJson());
            } else {
                sendError(exchange, NOT_FOUND, "WorkOrder with ID " + id + " not found.");
            }
        }

        private void handlePut(HttpExchange exchange, long id, String body) throws IOException {
            WorkOrder order = DATABASE.get(id);
            if (order == null) {
                sendError(exchange, NOT_FOUND, "WorkOrder not found.");
                return;
            }

            // Mise à jour partielle
            String licensePlate = extractJsonValue(body, "licensePlate");
            String description = extractJsonValue(body, "description");
            String status = extractJsonValue(body, "status");
            String dueDate = extractJsonValue(body, "dueDate");

            if (licensePlate != null) order.licensePlate = licensePlate;
            if (description != null) order.description = description;
            if (status != null) order.status = status;
            if (dueDate != null) order.dueDate = dueDate;

            sendJson(exchange, OK, order.toJson());
        }

        private void handleDelete(HttpExchange exchange, long id) throws IOException {
            if (DATABASE.remove(id) != null) {
                // 204 No Content ne renvoie pas de corps
                exchange.sendResponseHeaders(NO_CONTENT, -1);
            } else {
                sendError(exchange, NOT_FOUND, "WorkOrder not found.");
            }
        }

        // --- UTILITAIRES ---

        private long extractIdFromPath(String path) {
            try {
                return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private void sendJson(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            String jsonError = String.format("{\"error\": \"%s\"}", message);
            sendJson(exchange, statusCode, jsonError);
        }

        // Petit parser JSON "maison" (à ne pas utiliser en prod, utilisez Jackson/Gson)
        private String extractJsonValue(String json, String key) {
            try {
                String searchKey = "\"" + key + "\"";
                int start = json.indexOf(searchKey);
                if (start == -1) return null;
                
                int valueStart = json.indexOf(":", start) + 1;
                while (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '"') valueStart++;
                
                int valueEnd = valueStart;
                while (valueEnd < json.length() && json.charAt(valueEnd) != '"' && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd).trim();
            } catch (Exception e) {
                return null;
            }
        }
    }
}