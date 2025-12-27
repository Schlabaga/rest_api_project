import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    // Simulate a database in the ram
    private static final Map<Long, WorkOrder> DATABASE = Collections.synchronizedMap(new HashMap<>());
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    public static void main(String[] args) {
        try {
            // test data
            initDummyData();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            HttpContext context = server.createContext("/");
            context.setHandler(new RequestHandler());

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();

            System.out.println("=".repeat(60));
            System.out.println("Werkstatt API Server gestartet");
            System.out.println("=".repeat(60));
            System.out.println("Port:      " + PORT);
            System.out.println("Base URL:  http://localhost:" + PORT);
            System.out.println("\nEndpoints:");
            System.out.println("  GET    /workorders           - Liste aller Aufträge");
            System.out.println("  POST   /workorders           - Neuen Auftrag erstellen");
            System.out.println("  GET    /workorders/{id}      - Auftrag abrufen");
            System.out.println("  PUT    /workorders/{id}      - Auftrag aktualisieren");
            System.out.println("  DELETE /workorders/{id}      - Auftrag löschen");
            System.out.println("\nQuery-Parameter (GET /workorders):");
            System.out.println("  ?status=PENDING");
            System.out.println("  ?licensePlate=SB-XY-123");
            System.out.println("  ?dueDate=2025-10-15");
            System.out.println("=".repeat(60));
            System.out.println("\nDrücke ENTER zum Beenden...\n");

            Scanner sc = new Scanner(System.in);
            sc.nextLine();
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
        createOrder("SB-BB-999", "TÜV Hauptuntersuchung", "PENDING", "2025-12-20");
        createOrder("SB-XY-123", "Klimaanlage prüfen", "COMPLETED", "2025-08-10");
    }

    private static WorkOrder createOrder(String lp, String desc, String status, String due) {
        long id = ID_GENERATOR.getAndIncrement();
        WorkOrder wo = new WorkOrder(id, lp, desc, status, due);
        DATABASE.put(id, wo);
        return wo;
    }

    // --- MODEL CLASS (POJO) ---
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

        public String toJson() {
            return String.format(
                "{\"id\":%d,\"licensePlate\":\"%s\",\"description\":\"%s\",\"status\":\"%s\",\"dueDate\":\"%s\"}",
                id, escapedJson(licensePlate), escapedJson(description), status, dueDate
            );
        }

        private String escapedJson(String str) {
            return str.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    // --- MAIN HANDLER ---
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
            String query = exchange.getRequestURI().getQuery();

            System.out.println(MessageFormat.format("[{0}] {1} {2}",
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                method,
                path + (query != null ? "?" + query : "")));

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // --- ROUTING ---

            if (path.equals("/workorders")) {
                switch (method) {
                    case "GET":
                        handleGetCollection(exchange, query);
                        break;
                    case "POST":
                        handlePost(exchange, body);
                        break;
                    default:
                        sendMethodNotAllowed(exchange, "GET, POST");
                }
            }
            else if (path.matches("/workorders/\\d+")) {
                long id = extractIdFromPath(path);

                if (id == -1) {
                    sendError(exchange, BAD_REQUEST, "Invalid ID format", "ID must be a positive integer", path);
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
                        sendMethodNotAllowed(exchange, "GET, PUT, DELETE");
                }
            }
            else {
                sendError(exchange, NOT_FOUND, "Endpoint not found",
                    "Available endpoints: /workorders, /workorders/{id}", path);
            }
        }

        // --- COLLECTION WITH FILTERS ---
        private void handleGetCollection(HttpExchange exchange, String queryString) throws IOException {
            Map<String, String> params = parseQueryParams(queryString);

            // Data filtering
            List<WorkOrder> filtered = DATABASE.values().stream()
                .filter(wo -> {
                    // Filtre par status
                    if (params.containsKey("status")) {
                        if (!wo.status.equalsIgnoreCase(params.get("status"))) {
                            return false;
                        }
                    }
                    // Filtre par licensePlate
                    if (params.containsKey("licensePlate")) {
                        if (!wo.licensePlate.equalsIgnoreCase(params.get("licensePlate"))) {
                            return false;
                        }
                    }
                    // Filtre par dueDate
                    if (params.containsKey("dueDate")) {
                        if (!wo.dueDate.equals(params.get("dueDate"))) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

            String jsonResponse = filtered.stream()
                .map(WorkOrder::toJson)
                .collect(Collectors.joining(",", "[", "]"));

            sendJson(exchange, OK, jsonResponse);
        }

        // --- POST avec validation ---
        private void handlePost(HttpExchange exchange, String body) throws IOException {
            // Vérification Content-Type
            List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
            if (contentTypes == null || !contentTypes.get(0).contains("application/json")) {
                sendError(exchange, BAD_REQUEST, "Invalid Content-Type",
                    "Expected: application/json", "/workorders");
                return;
            }

            String licensePlate = extractJsonValue(body, "licensePlate");
            String description = extractJsonValue(body, "description");
            String status = extractJsonValue(body, "status");
            String dueDate = extractJsonValue(body, "dueDate");

            // Validation
            ValidationResult validation = validateWorkOrder(licensePlate, description, status, dueDate);
            if (!validation.isValid) {
                sendError(exchange, BAD_REQUEST, validation.errorMessage,
                    validation.errorDetail, "/workorders");
                return;
            }

            // Création
            WorkOrder newOrder = createOrder(
                licensePlate,
                description,
                status != null ? status : "PENDING",
                dueDate
            );

            exchange.getResponseHeaders().add("Location", "/workorders/" + newOrder.id);
            sendJson(exchange, CREATED, newOrder.toJson());
        }

        private void handleGetItem(HttpExchange exchange, long id) throws IOException {
            WorkOrder order = DATABASE.get(id);
            if (order != null) {
                sendJson(exchange, OK, order.toJson());
            } else {
                sendError(exchange, NOT_FOUND, "WorkOrder not found",
                    "No work order exists with ID " + id, "/workorders/" + id);
            }
        }

        private void handlePut(HttpExchange exchange, long id, String body) throws IOException {
            WorkOrder order = DATABASE.get(id);
            if (order == null) {
                sendError(exchange, NOT_FOUND, "WorkOrder not found",
                    "No work order exists with ID " + id, "/workorders/" + id);
                return;
            }

            // Vérification Content-Type
            List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
            if (contentTypes == null || !contentTypes.get(0).contains("application/json")) {
                sendError(exchange, BAD_REQUEST, "Invalid Content-Type",
                    "Expected: application/json", "/workorders/" + id);
                return;
            }

            String licensePlate = extractJsonValue(body, "licensePlate");
            String description = extractJsonValue(body, "description");
            String status = extractJsonValue(body, "status");
            String dueDate = extractJsonValue(body, "dueDate");

            // Validation des champs modifiés
            if (status != null && !isValidStatus(status)) {
                sendError(exchange, BAD_REQUEST, "Invalid status",
                    "Status must be PENDING, IN_PROGRESS, or COMPLETED", "/workorders/" + id);
                return;
            }

            if (dueDate != null && !isValidDate(dueDate)) {
                sendError(exchange, BAD_REQUEST, "Invalid date format",
                    "Date must be in YYYY-MM-DD format", "/workorders/" + id);
                return;
            }

            if (licensePlate != null && !isValidLicensePlate(licensePlate)) {
                sendError(exchange, BAD_REQUEST, "Invalid license plate",
                    "License plate must be 1-20 characters", "/workorders/" + id);
                return;
            }

            if (description != null && !isValidDescription(description)) {
                sendError(exchange, BAD_REQUEST, "Invalid description",
                    "Description must be 1-255 characters", "/workorders/" + id);
                return;
            }

            // Mise à jour
            if (licensePlate != null) order.licensePlate = licensePlate;
            if (description != null) order.description = description;
            if (status != null) order.status = status;
            if (dueDate != null) order.dueDate = dueDate;

            sendJson(exchange, OK, order.toJson());
        }

        private void handleDelete(HttpExchange exchange, long id) throws IOException {
            if (DATABASE.remove(id) != null) {
                exchange.sendResponseHeaders(NO_CONTENT, -1);
                System.out.println("  → WorkOrder " + id + " deleted");
            } else {
                sendError(exchange, NOT_FOUND, "WorkOrder not found",
                    "No work order exists with ID " + id, "/workorders/" + id);
            }
        }

        // --- VALIDATION ---

        static class ValidationResult {
            boolean isValid;
            String errorMessage;
            String errorDetail;

            ValidationResult(boolean valid, String msg, String detail) {
                this.isValid = valid;
                this.errorMessage = msg;
                this.errorDetail = detail;
            }
        }

        private ValidationResult validateWorkOrder(String lp, String desc, String status, String dueDate) {
            if (lp == null || lp.trim().isEmpty()) {
                return new ValidationResult(false, "Missing required field",
                    "licensePlate is required");
            }
            if (!isValidLicensePlate(lp)) {
                return new ValidationResult(false, "Invalid license plate",
                    "License plate must be 1-20 characters");
            }

            if (desc == null || desc.trim().isEmpty()) {
                return new ValidationResult(false, "Missing required field",
                    "description is required");
            }
            if (!isValidDescription(desc)) {
                return new ValidationResult(false, "Invalid description",
                    "Description must be 1-255 characters");
            }

            if (status != null && !isValidStatus(status)) {
                return new ValidationResult(false, "Invalid status",
                    "Status must be PENDING, IN_PROGRESS, or COMPLETED");
            }

            if (dueDate == null || dueDate.trim().isEmpty()) {
                return new ValidationResult(false, "Missing required field",
                    "dueDate is required");
            }
            if (!isValidDate(dueDate)) {
                return new ValidationResult(false, "Invalid date format",
                    "Date must be in YYYY-MM-DD format");
            }

            return new ValidationResult(true, null, null);
        }

        private boolean isValidStatus(String status) {
            return status.equals("PENDING") || status.equals("IN_PROGRESS") || status.equals("COMPLETED");
        }

        private boolean isValidDate(String date) {
            try {
                LocalDate.parse(date);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }

        private boolean isValidLicensePlate(String lp) {
            return lp != null && lp.length() >= 1 && lp.length() <= 20;
        }

        private boolean isValidDescription(String desc) {
            return desc != null && desc.length() >= 1 && desc.length() <= 255;
        }

        // --- UTILS ---

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) {
                return params;
            }

            for (String param : query.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        params.put(key, value);
                    } catch (Exception e) {
                        // Ignorer les paramètres malformés
                    }
                }
            }
            return params;
        }

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

        private void sendError(HttpExchange exchange, int statusCode, String message,
                               String detail, String path) throws IOException {
            String jsonError = String.format(
                "{\"message\":\"%s\",\"detail\":\"%s\",\"path\":\"%s\"}",
                message, detail, path
            );
            sendJson(exchange, statusCode, jsonError);
        }

        private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
            exchange.getResponseHeaders().add("Allow", allowedMethods);
            sendError(exchange, METHOD_NOT_ALLOWED, "Method not allowed",
                "Allowed methods: " + allowedMethods, exchange.getRequestURI().getPath());
        }

        // Parse simple JSON (in production: use Jackson/Gson)
        private String extractJsonValue(String json, String key) {
            try {
                String searchKey = "\"" + key + "\"";
                int start = json.indexOf(searchKey);
                if (start == -1) return null;

                int valueStart = json.indexOf(":", start) + 1;
                while (valueStart < json.length() &&
                       (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '"')) {
                    valueStart++;
                }

                int valueEnd = valueStart;
                while (valueEnd < json.length() &&
                       json.charAt(valueEnd) != '"' &&
                       json.charAt(valueEnd) != ',' &&
                       json.charAt(valueEnd) != '}') {
                    valueEnd++;
                }

                String value = json.substring(valueStart, valueEnd).trim();
                return value.isEmpty() ? null : value;
            } catch (Exception e) {
                return null;
            }
        }
    }
}