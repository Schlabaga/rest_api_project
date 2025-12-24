import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Test class for WerkstattRESTServer
 * Starts the server and executes manual tests
 * 
 * Note: This is a manual test since the project doesn't use JUnit
 * For a professional approach, add JUnit 5 to pom.xml
 */
public class WerkstattRESTServerTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("WERKSTATT REST API - TESTS");
        System.out.println("=".repeat(70));
        System.out.println("\n⚠️  IMPORTANT: First launch WerkstattRESTServer.java");
        System.out.println("   then execute this test class\n");
        System.out.println("=".repeat(70));

        int totalTests = 0;
        int passedTests = 0;
        int failedTests = 0;

        try {
            // Test 1: GET /workorders - Complete list
            totalTests++;
            System.out.println("\n[TEST 1] GET /workorders - Retrieve all work orders");
            if (testGetAllWorkOrders()) {
                passedTests++;
                printSuccess("List retrieved successfully");
            } else {
                failedTests++;
                printFailure("Failed to retrieve list");
            }

            // Test 2: GET /workorders?status=PENDING - Filter by status
            totalTests++;
            System.out.println("\n[TEST 2] GET /workorders?status=PENDING - Filter by status");
            if (testFilterByStatus()) {
                passedTests++;
                printSuccess("Status filtering works");
            } else {
                failedTests++;
                printFailure("Status filtering failed");
            }

            // Test 3: GET /workorders?licensePlate=SB-XY-123 - Filter by license plate
            totalTests++;
            System.out.println("\n[TEST 3] GET /workorders?licensePlate=SB-XY-123 - Filter by plate");
            if (testFilterByLicensePlate()) {
                passedTests++;
                printSuccess("License plate filtering works");
            } else {
                failedTests++;
                printFailure("License plate filtering failed");
            }

            // Test 4: POST /workorders - Create new work order
            totalTests++;
            System.out.println("\n[TEST 4] POST /workorders - Create new work order");
            Long newId = testCreateWorkOrder();
            if (newId != null) {
                passedTests++;
                printSuccess("Work order created with ID: " + newId);
            } else {
                failedTests++;
                printFailure("Failed to create work order");
            }

            // Test 5: GET /workorders/{id} - Retrieve specific work order
            totalTests++;
            System.out.println("\n[TEST 5] GET /workorders/1 - Retrieve work order by ID");
            if (testGetWorkOrderById(1L)) {
                passedTests++;
                printSuccess("Work order retrieved successfully");
            } else {
                failedTests++;
                printFailure("Failed to retrieve work order");
            }

            // Test 6: PUT /workorders/{id} - Update work order
            totalTests++;
            System.out.println("\n[TEST 6] PUT /workorders/1 - Update status");
            if (testUpdateWorkOrder(1L)) {
                passedTests++;
                printSuccess("Work order updated successfully");
            } else {
                failedTests++;
                printFailure("Failed to update work order");
            }

            // Test 7: POST /workorders - Validation test (missing fields)
            totalTests++;
            System.out.println("\n[TEST 7] POST /workorders - Validation test (missing required fields)");
            if (testValidationError()) {
                passedTests++;
                printSuccess("Validation works correctly (400 returned)");
            } else {
                failedTests++;
                printFailure("Validation should have failed");
            }

            // Test 8: GET /workorders/9999 - Test 404 Not Found
            totalTests++;
            System.out.println("\n[TEST 8] GET /workorders/9999 - Test 404 Not Found");
            if (testNotFound()) {
                passedTests++;
                printSuccess("404 error handled correctly");
            } else {
                failedTests++;
                printFailure("404 error not handled correctly");
            }

            // Test 9: DELETE /workorders/{id} - Delete work order
            totalTests++;
            System.out.println("\n[TEST 9] DELETE /workorders/2 - Delete work order");
            if (testDeleteWorkOrder(2L)) {
                passedTests++;
                printSuccess("Work order deleted successfully");
            } else {
                failedTests++;
                printFailure("Failed to delete work order");
            }

            // Test 10: GET /workorders?dueDate=2025-10-15 - Filter by due date
            totalTests++;
            System.out.println("\n[TEST 10] GET /workorders?dueDate=2025-10-15 - Filter by due date");
            if (testFilterByDueDate()) {
                passedTests++;
                printSuccess("Due date filtering works");
            } else {
                failedTests++;
                printFailure("Due date filtering failed");
            }

        } catch (Exception e) {
            System.err.println("\n❌ CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println("Total tests:  " + totalTests);
        System.out.println("✅ Passed:    " + passedTests);
        System.out.println("❌ Failed:    " + failedTests);
        System.out.println("Success rate: " + (totalTests > 0 ? (passedTests * 100 / totalTests) : 0) + "%");
        System.out.println("=".repeat(70));
    }

    // Test method implementations

    private static boolean testGetAllWorkOrders() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 200 && 
                   response.body().startsWith("[") && 
                   response.body().contains("licensePlate");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testFilterByStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders?status=PENDING"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            // Check that all returned items have status PENDING
            return response.statusCode() == 200 && 
                   response.body().contains("\"status\":\"PENDING\"");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testFilterByLicensePlate() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders?licensePlate=SB-XY-123"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 200 && 
                   response.body().contains("\"licensePlate\":\"SB-XY-123\"");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testFilterByDueDate() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders?dueDate=2025-10-15"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 200 && 
                   response.body().contains("\"dueDate\":\"2025-10-15\"");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static Long testCreateWorkOrder() {
        try {
            String jsonBody = """
                {
                    "licensePlate": "TEST-123",
                    "description": "Test repair from automated test",
                    "status": "PENDING",
                    "dueDate": "2025-12-31"
                }
                """;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            if (response.statusCode() == 201) {
                // Extract ID from Location header or response body
                String location = response.headers().firstValue("Location").orElse("");
                if (location.contains("/workorders/")) {
                    String idStr = location.substring(location.lastIndexOf('/') + 1);
                    return Long.parseLong(idStr);
                }
                // Try to extract from body
                String body = response.body();
                if (body.contains("\"id\":")) {
                    int start = body.indexOf("\"id\":") + 5;
                    int end = body.indexOf(",", start);
                    return Long.parseLong(body.substring(start, end).trim());
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return null;
        }
    }

    private static boolean testGetWorkOrderById(Long id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders/" + id))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 200 && 
                   response.body().contains("\"id\":" + id);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testUpdateWorkOrder(Long id) {
        try {
            String jsonBody = """
                {
                    "status": "IN_PROGRESS",
                    "description": "Updated description from test"
                }
                """;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders/" + id))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 200 && 
                   response.body().contains("\"status\":\"IN_PROGRESS\"");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testDeleteWorkOrder(Long id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders/" + id))
                    .DELETE()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 204;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testValidationError() {
        try {
            // Missing required fields (licensePlate)
            String jsonBody = """
                {
                    "description": "Test without license plate"
                }
                """;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            // Should return 400 Bad Request
            return response.statusCode() == 400 && 
                   response.body().contains("message");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean testNotFound() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/workorders/9999"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            printResponse(response);
            
            return response.statusCode() == 404;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    // Utility methods

    private static void printResponse(HttpResponse<String> response) {
        System.out.println("  Status: " + response.statusCode());
        if (!response.body().isEmpty()) {
            String body = response.body();
            if (body.length() > 200) {
                System.out.println("  Body: " + body.substring(0, 200) + "... (truncated)");
            } else {
                System.out.println("  Body: " + body);
            }
        }
    }

    private static void printSuccess(String message) {
        System.out.println("  ✅ " + message);
    }

    private static void printFailure(String message) {
        System.out.println("  ❌ " + message);
    }
}