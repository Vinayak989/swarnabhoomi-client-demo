package in.swarnabhoomi.farmlog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class BrowserPushControllerTest {
    private final BrowserPushService service = mock(BrowserPushService.class);
    private final AppUser farmer = new AppUser("+919800000009", "farmer", "test-hash");
    private MockMvc mvc;

    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new BrowserPushController(service)).build(); }

    @Test void registrationErrorsIncludeTheActualReasonInsteadOfAGenericBadRequest() throws Exception {
        when(service.subscribe(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोनवरील सूचना नोंदणी अपूर्ण आहे."));
        mvc.perform(post("/api/push/subscriptions").requestAttr(UserAuthFilter.USER_ATTRIBUTE, farmer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://fcm.googleapis.com/wp/test\",\"keys\":{},\"androidApp\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUSH_SUBSCRIPTION_INVALID"))
                .andExpect(jsonPath("$.message").value("फोनवरील सूचना नोंदणी अपूर्ण आहे."));
    }

    @Test void standardAndroidSubscriptionJsonIsAccepted() throws Exception {
        when(service.subscribe(any(), any())).thenReturn(new BrowserPushService.SubscriptionResponse(1L));
        mvc.perform(post("/api/push/subscriptions").requestAttr(UserAuthFilter.USER_ATTRIBUTE, farmer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://fcm.googleapis.com/wp/test\",\"expirationTime\":null,\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"},\"androidApp\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        verify(service).subscribe(argThat(request -> request.androidApp() && request.keys().p256dh().equals("key")), eq(farmer));
    }

    @Test void malformedJsonHasAnActionableError() throws Exception {
        mvc.perform(post("/api/push/subscriptions").requestAttr(UserAuthFilter.USER_ATTRIBUTE, farmer)
                .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUSH_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(service);
    }
}
