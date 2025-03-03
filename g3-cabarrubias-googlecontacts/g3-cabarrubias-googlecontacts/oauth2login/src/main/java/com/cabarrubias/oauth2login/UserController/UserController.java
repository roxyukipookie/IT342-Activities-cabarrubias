package com.cabarrubias.oauth2login.UserController;

import com.cabarrubias.oauth2login.model.Contact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@Controller
public class UserController {
    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping
    public String index() {
        return "<h1>Hello, This is the landing page.";
    }

    @GetMapping("/user-info")
    public String getUserInfo(Model model, @AuthenticationPrincipal OAuth2User oAuth2User) {
        System.out.println("User Data: " + oAuth2User.getAttributes());
        model.addAttribute("user", oAuth2User.getAttributes());
        return "user-info"; // match a Thymeleaf template like `user-info.html`
    }


    @GetMapping("/googleuser")
    public String getUserContacts(Model model, OAuth2AuthenticationToken authentication) {
        OAuth2User user = authentication.getPrincipal();
        String accessToken = getAccessToken(authentication);
        System.out.println("Access Token: " + accessToken);

        List<Contact> contacts = fetchGoogleContacts(accessToken);
        System.out.println("Updated Contacts List: " + contacts);

        model.addAttribute("contacts", contacts);
        return "contacts";
    }

    private String getAccessToken(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
        return client.getAccessToken().getTokenValue();
    }

    private List<Contact> fetchGoogleContacts(String accessToken) {
        String url = "https://people.googleapis.com/v1/people/me/connections"
                + "?personFields=names,emailAddresses,phoneNumbers";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Contact> contacts = new ArrayList<>();
        if (response.getBody() != null && response.getBody().containsKey("connections")) {
            List<Map<String, Object>> connections = (List<Map<String, Object>>) response.getBody().get("connections");
            for (Map<String, Object> connection : connections) {
                String resourceName = (String) connection.get("resourceName");
                String name = extractValue(connection, "names", "displayName");
                String email = extractValue(connection, "emailAddresses", "value");
                String phone = extractValue(connection, "phoneNumbers", "value");
                contacts.add(new Contact(resourceName, name, email, phone));
            }
        }
        return contacts;
    }

    private String extractValue(Map<String, Object> data, String field, String key) {
        if (data.containsKey(field)) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get(field);
            if (!items.isEmpty() && items.get(0).containsKey(key)) {
                return (String) items.get(0).get(key);
            }
        }
        return "Not Applicable";
    }


    @PostMapping("/delete-contact")
    public String deleteContact(@RequestParam String resourceName, OAuth2AuthenticationToken authentication,
                                @RequestHeader(value = "Referer", required = false) String referer) {
        String accessToken = getAccessToken(authentication);
        if (accessToken == null) return "redirect:/googleuser";

        String url = "https://people.googleapis.com/v1/" + resourceName + ":deleteContact";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("✅ Contact deleted successfully.");
            } else {
                System.out.println("Failed to delete contact: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error deleting contact: " + e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/googleuser");
    }

    @PostMapping("/add-contact")
    public String addContact(@RequestParam String firstName, @RequestParam String lastName, @RequestParam String email, @RequestParam String phone,
                             OAuth2AuthenticationToken authentication, Model model) {
        String accessToken = getAccessToken(authentication);

        // Google People API endpoint for creating a contact
        String url = "https://people.googleapis.com/v1/people:createContact";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");

        String fullName = firstName + " " + lastName;

        // Request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("names", List.of(Map.of("givenName", firstName, "familyName", lastName)));        requestBody.put("emailAddresses", List.of(Map.of("value", email)));
        requestBody.put("phoneNumbers", List.of(Map.of("value", phone)));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            model.addAttribute("message", "Contact added successfully!");
        } else {
            model.addAttribute("message", "Failed to add contact.");
        }

        return "redirect:/googleuser"; // Redirect to refresh the contact list
    }

    @GetMapping("/edit-contact")
    public ResponseEntity<?> editContactForm(
            @RequestParam String resourceName,
            Model model,
            OAuth2AuthenticationToken authentication,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        try {
            // Fetch OAuth token
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    authentication.getAuthorizedClientRegistrationId(), authentication.getName());
            String accessToken = client.getAccessToken().getTokenValue();

            // Fetch contact details
            String getUrl = "https://people.googleapis.com/v1/" + resourceName + "?personFields=names,emailAddresses,phoneNumbers";
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> getRequest = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(getUrl, HttpMethod.GET, getRequest, Map.class);

            Map<String, Object> contactData = response.getBody();
            if (contactData == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Contact not found"));
            }

            // Return JSON if it's an AJAX request
            if ("XMLHttpRequest".equals(requestedWith)) {
                return ResponseEntity.ok(contactData);
            }

            // Otherwise, return the Thymeleaf page
            model.addAttribute("contact", contactData);
            model.addAttribute("resourceName", resourceName);
            return ResponseEntity.ok().body(contactData); // Can be replaced with a Thymeleaf view if needed.
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/edit-contact")
    public ResponseEntity<String> updateContact(
            @RequestBody Map<String, String> requestData,
            OAuth2AuthenticationToken authentication) {
        try {
            String resourceName = requestData.get("resourceName");
            String firstName = requestData.getOrDefault("firstName", "").trim();
            String lastName = requestData.getOrDefault("lastName", "").trim();
            String email = requestData.getOrDefault("email", "").trim();
            String phone = requestData.getOrDefault("phone", "").trim();
            // 🔹 Fetch OAuth Token
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    authentication.getAuthorizedClientRegistrationId(), authentication.getName());
            String accessToken = client.getAccessToken().getTokenValue();
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 🔹 Fetch Contact to get etag
            String getUrl = "https://people.googleapis.com/v1/" + resourceName + "?personFields=metadata";
            HttpEntity<Void> getRequest = new HttpEntity<>(headers);
            ResponseEntity<Map> getResponse = restTemplate.exchange(getUrl, HttpMethod.GET, getRequest, Map.class);
            Map<String, Object> contactData = getResponse.getBody();
            if (contactData == null || !contactData.containsKey("etag")) {
                return ResponseEntity.badRequest().body("Error: Contact etag is missing.");
            }
            String etag = (String) contactData.get("etag");
            // 🔹 Construct Request Body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("etag", etag);
            List<Map<String, String>> names = new ArrayList<>();
            if (!firstName.isEmpty() || !lastName.isEmpty()) {
                names.add(Map.of(
                        "givenName", firstName.isEmpty() ? "" : firstName,
                        "familyName", lastName.isEmpty() ? "" : lastName
                ));
            }
            List<Map<String, String>> emails = new ArrayList<>();
            if (!email.isEmpty()) {
                emails.add(Map.of("value", email));
            }
            List<Map<String, String>> phones = new ArrayList<>();
            if (!phone.isEmpty()) {
                phones.add(Map.of("value", phone));
            }
            if (!names.isEmpty()) requestBody.put("names", names);
            if (!emails.isEmpty()) requestBody.put("emailAddresses", emails);
            if (!phones.isEmpty()) requestBody.put("phoneNumbers", phones);
            // 🔹 Send "PATCH" Request (Using POST Override)
            String patchUrl = "https://people.googleapis.com/v1/" + resourceName + ":updateContact?updatePersonFields=names,emailAddresses,phoneNumbers";
            headers.set("X-HTTP-Method-Override", "PATCH");
            HttpEntity<Map<String, Object>> patchRequest = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(patchUrl, HttpMethod.POST, patchRequest, String.class);
            return ResponseEntity.ok("Contact updated successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/secured")
    public String secured() {
        return "<h1>Hello. This is a secured page.</h1>";
    }
}