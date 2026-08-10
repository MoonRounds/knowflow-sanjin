package knowflow.sanjin.modules.knowledgebase.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import knowflow.sanjin.common.exception.GlobalExceptionHandler;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeBaseControllerTest {

  private MockMvc mvc;
  private KnowledgeBaseService service;

  @BeforeEach
  void setUp() {
    service = mock(KnowledgeBaseService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new KnowledgeBaseController(service))
            .addPlaceholderValue("knowflow.api.base-path", "/api/v1")
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("should serialize BIGINT ids as strings and return a strong ETag")
  void shouldReturnStringIdAndEtag() throws Exception {
    KnowledgeBase kb = new KnowledgeBase();
    kb.setId(Long.MAX_VALUE);
    kb.setOwnerId(1L);
    kb.setDisplayName("Notes");
    kb.setEnabled(true);
    kb.setDeleted(false);
    kb.setRowVersion(7);
    kb.setCreatedAt(Instant.parse("2026-08-10T00:00:00Z"));
    kb.setUpdatedAt(Instant.parse("2026-08-10T00:00:00Z"));
    when(service.getByIdAndOwner(anyLong())).thenReturn(kb);

    mvc.perform(get("/api/v1/knowledge-bases/{id}", Long.toString(Long.MAX_VALUE)))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"7\""))
        .andExpect(jsonPath("$.id").value(Long.toString(Long.MAX_VALUE)));
  }

  @Test
  @DisplayName("should return Problem Details and 428 when If-Match is missing")
  void shouldRequireIfMatch() throws Exception {
    mvc.perform(delete("/api/v1/knowledge-bases/1"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("IF_MATCH_REQUIRED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  @DisplayName("should return Problem Details for malformed If-Match and validation failures")
  void shouldReturnProblemDetailsForBadWrites() throws Exception {
    mvc.perform(delete("/api/v1/knowledge-bases/1").header("If-Match", "7"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

    mvc.perform(
            put("/api/v1/knowledge-bases/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"missing version\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }
}
