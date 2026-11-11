package com.gihan.contextioq.ContextIQ;
import com.gihan.contextioq.ContextIQ.ai.service.*;
import com.gihan.contextioq.ContextIQ.config.OllamaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
// Opt-in only: connects to an already-running Ollama; never starts a service or pulls a model.
@EnabledIfEnvironmentVariable(named="CONTEXTIQ_OLLAMA_SMOKE",matches="true")
class OllamaLiveTests {
 @Test void installedGemmaRespondsThroughSpringAi(){
  var config=new OllamaConfig();
  var model=config.ollamaChatModel("http://localhost:11434","gemma4:e2b",128,4096);
  var reply=new OllamaAiChatService(model,"gemma4:e2b").reply(List.of(new AiChatService.Turn("user","Reply with one short greeting.")));
  assertFalse(reply.content().isBlank());assertEquals("OLLAMA",reply.mode());assertNotNull(reply.outputTokens());
  System.out.println("Local Spring AI smoke test passed: model="+reply.model()+", outputTokens="+reply.outputTokens());
 }
}
