import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.List;
import java.util.Scanner;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class KleinerRESTServer {

	private static final int port = 8080;

	public static void main(String[] args) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
			HttpContext context = server.createContext("/");
			context.setHandler(new Handler());

			server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
			server.start();
			System.out.println("Web-Server auf Port " + port + " gestartet.");
			System.out.println(
					"Rufe HTTP-Server im Web-Browser auf mit http://localhost:" + port + "/MeineRessource?MeineFrage");

			System.out.println("Stoppe Web-Server durch beliebige Eingabe");
			Scanner sc = new Scanner(System.in);
			sc.next();
			sc.close();
			server.stop(0);
			System.out.println("Web-Server gestoppt.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static class Handler implements HttpHandler {

		// HTTP Status Codes
		private static final int OK = 200;
		private static final int CREATED = 201;
		private static final int NO_CONTENT = 204;
		private static final int BAD_REQUEST = 400;
		private static final int NOT_FOUND = 404;
		private static final int METHOD_NOT_ALLOWED = 405;
		// http error messages
		private static final String BAD_REQUEST_MAL_FORMED_ID = "<h1>400 Bad Request</h1>malformed request syntax. User ID not an integer.";
		private static final String BAD_REQUEST_INVALID_CONTENT_FORMAT_OR_VALUES = "<h1>400 Bad Request</h1>content of invalid format or value constraint violation. Should be a user in JSON format";
		private static final String BAD_REQUEST_INVALID_CONTENT_TYPE = "<h1>400 Bad Request</h1>invalid content type. User /text/json";
		private static final String NOT_FOUND_WRONG_CONTEXT = "<h1>404 Not Found</h1>No context found for request. Use URL /users or /users/{id}";
		private static final String NOT_FOUND_NO_USER = "<h1>404 Not Found</h1>valid endpoint but resource does not exist. no user with id = %d";
		private static final String BAD_REQUEST_INVALID_USER_ID = "<h1>400 Bad Request</h1>invalid user id. %d out of intervall [0,127]";

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			System.out.println("handle http request");

			String method = exchange.getRequestMethod();
			String request_target_url = exchange.getRequestURI().getPath();
			String protocol = exchange.getProtocol();
			String http_request_start_line = String.format(
					"http request start-line - method: %s, request-target: %s, protocol: %s", method,
					request_target_url, protocol);
			System.out.println(http_request_start_line);

			// String path = URLDecoder.decode(exchange.getRequestURI().getPath(),
			// StandardCharsets.UTF_8);
			System.out.println("http request header");
			List<String> ct = exchange.getRequestHeaders().get("Content-Type");
			String contenttype = (ct != null) ? ct.toString() : "";
			List<String> cl = exchange.getRequestHeaders().get("Content-Length");
			String contentlenght = (cl != null) ? cl.toString() : "";
			System.out.println(String.format("Representation headers: Content-Type: %s, Content-Lenght: %s",
					contenttype, contentlenght));
			System.out.println("Request headers: ");
			exchange.getRequestHeaders().forEach((key, value) -> {
				System.out.println("\tKey : " + key + " Value : " + value);
			});

			String query = exchange.getRequestURI().getQuery();
			String body = new String(exchange.getRequestBody().readAllBytes());
			String request = MessageFormat.format(
					"{0} Methode mit URI \"{1}\" und Query \"{2}\" und Body \"{3}\" und content type \"{4}\" erhalten.",
					method, request_target_url, query, body, contenttype);
			System.out.println(request);

			if (request_target_url.equals("/users")) {
				if (method.equals("POST")) {
					if (contenttype.equals("[application/json]")) {
						boolean parseUser = true;
						if (parseUser) {
							// add user to list, handle if user exists or list full
							exchange.getResponseHeaders().add("Content-type", "text/json; charset=utf-8");
							exchange.getResponseHeaders().add("Location", "http://localhost:8080/users/67");
							exchange.sendResponseHeaders(CREATED, 0);
						} else {
							errorResponse(exchange, BAD_REQUEST, BAD_REQUEST_INVALID_CONTENT_FORMAT_OR_VALUES);
						}
					} else {
						errorResponse(exchange, BAD_REQUEST, BAD_REQUEST_INVALID_CONTENT_TYPE);
					}
				} else if (method.equals("GET")) {
					// The resource has been fetched and transmitted in the message body
					String response = "[ { \"id\" : 67, \"user\" : { \"email\": \"dfhi@htwsaar.de\", \"authorization\" : \"rm\" } },{ \"id\" : 57, \"user\" : { \"email\": \"isfates@ul.de\", \"authorization\" : \"rcmd\" } } ]";
					exchange.getResponseHeaders().add("Content-type", "text/json; charset=utf-8");
					exchange.sendResponseHeaders(OK, response.getBytes().length);
					OutputStream os = exchange.getResponseBody();
					os.write(response.getBytes());
					os.close();
				} else {
					exchange.getResponseHeaders().add("Allow", "POST, GET");
					exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, 0);
				}
			} else if (request_target_url.matches("/users/.*")) {
				try {
					String pathParameter = request_target_url.substring(7);
					System.out.println("Path Parameter " + pathParameter);
					int requestedID = Integer.parseInt(pathParameter);
					if (0 <= requestedID && requestedID <= 127) {
						boolean userFound = true;
						if (userFound) {
							if (method.equals("GET")) {
								// The resource has been fetched and transmitted in the message body
								String response = "{\"email\":\"dfhi@htwsaar.de\",\"authorization\":\"rm\"}";
								exchange.getResponseHeaders().add("Content-type", "text/json; charset=utf-8");
								exchange.sendResponseHeaders(OK, response.getBytes().length);
								OutputStream os = exchange.getResponseBody();
								os.write(response.getBytes());
								os.close();
							} else if (method.equals("PUT")) {
								// update User
								exchange.sendResponseHeaders(NO_CONTENT, -1);
							} else if (method.equals("DELETE")) {
								// delete user
								exchange.sendResponseHeaders(NO_CONTENT, -1);
							} else {
								exchange.getResponseHeaders().add("Allow", "GET, PUT, DELETE");
								exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, 0);
							}
						} else if (method.equals("GET") || method.equals("PUT") || method.equals("DELETE")) {
							errorResponse(exchange, NOT_FOUND, String.format(NOT_FOUND_NO_USER, requestedID));
						} else {
							exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, 0);
						}
					} else {
						errorResponse(exchange, BAD_REQUEST, String.format(BAD_REQUEST_INVALID_USER_ID, requestedID));
					}
				} catch (NumberFormatException e) {
					errorResponse(exchange, BAD_REQUEST, BAD_REQUEST_MAL_FORMED_ID);
				}
			} else {
				errorResponse(exchange, NOT_FOUND, NOT_FOUND_WRONG_CONTEXT);
			}
		}

	}

	public static void errorResponse(HttpExchange exchange, int http_status_code, String htmlRspMsg)
			throws IOException {
		exchange.getResponseHeaders().add("Content-type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(http_status_code, htmlRspMsg.getBytes().length);
		OutputStream os = exchange.getResponseBody();
		os.write(htmlRspMsg.getBytes());
		os.close();
	}
}
