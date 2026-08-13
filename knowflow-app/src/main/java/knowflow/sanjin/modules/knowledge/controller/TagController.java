package knowflow.sanjin.modules.knowledge.controller;

import java.util.List;
import knowflow.sanjin.modules.knowledge.assembler.TagAssembler;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.knowledge.vo.TagResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tag REST 入口：owner 级标签列表，供文档列表 Tag 过滤下拉使用（G23）。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class TagController {

  private final KnowledgeDocumentService service;

  public TagController(KnowledgeDocumentService service) {
    this.service = service;
  }

  @GetMapping("/tags")
  public List<TagResponse> list() {
    return TagAssembler.toResponseList(service.listTagsForOwner());
  }
}
