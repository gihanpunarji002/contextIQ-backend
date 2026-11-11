package com.gihan.contextioq.ContextIQ.ai.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.*;
class OllamaAiChatServiceTests {
 private ChatModel model; private OllamaAiChatService service;
 @BeforeEach void setup(){model=mock(ChatModel.class);service=new OllamaAiChatService(model,"gemma4:e2b");}
 @Test void sendsConversationThroughSpringAiAndPreservesUsage(){
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Local reply"))),ChatResponseMetadata.builder().model("gemma4:e2b").usage(new DefaultUsage(12,4)).build()));
  var reply=service.reply(List.of(new AiChatService.Turn("user","First"),new AiChatService.Turn("assistant","Earlier reply"),new AiChatService.Turn("user","Follow up")));
  assertEquals("OLLAMA",reply.mode());assertEquals("gemma4:e2b",reply.model());assertEquals(12,reply.inputTokens());assertEquals("Local reply",reply.content());
  var capture=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model).call(capture.capture());
  var messages=capture.getValue().getInstructions();assertEquals(4,messages.size());assertInstanceOf(SystemMessage.class,messages.get(0));assertInstanceOf(AssistantMessage.class,messages.get(2));assertEquals("Follow up",messages.get(3).getText());
 }
 @Test void emptyAndTruncatedResponsesAreNotSuccessful(){
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
  assertEquals(502,assertThrows(ResponseStatusException.class,()->service.reply(List.of())).getStatusCode().value());
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Partial"),ChatGenerationMetadata.builder().finishReason("length").build()))));
  assertEquals(502,assertThrows(ResponseStatusException.class,()->service.reply(List.of())).getStatusCode().value());
 }
 @Test void missingModelAndTimeoutAreSafe(){
  when(model.call(any(Prompt.class))).thenThrow(new RuntimeException(HttpClientErrorException.create(org.springframework.http.HttpStatus.NOT_FOUND,"not found",org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)));
  assertEquals(503,assertThrows(ResponseStatusException.class,()->service.reply(List.of())).getStatusCode().value());
  when(model.call(any(Prompt.class))).thenThrow(new ResourceAccessException("timeout",new java.net.SocketTimeoutException()));
  assertEquals(504,assertThrows(ResponseStatusException.class,()->service.reply(List.of())).getStatusCode().value());
 }
 @Test void unavailableServerHasUsefulErrorAndNoRetry(){
  when(model.call(any(Prompt.class))).thenThrow(new ResourceAccessException("connection refused"));
  var e=assertThrows(ResponseStatusException.class,()->service.reply(List.of()));assertEquals(502,e.getStatusCode().value());assertTrue(e.getReason().contains("reachable"));verify(model,times(1)).call(any(Prompt.class));
 }
}
