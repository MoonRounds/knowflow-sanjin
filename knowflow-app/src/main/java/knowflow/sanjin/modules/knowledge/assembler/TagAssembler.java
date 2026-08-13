package knowflow.sanjin.modules.knowledge.assembler;

import java.util.List;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.vo.TagResponse;

/** Tag 实体与 API 模型的显式转换。 */
public final class TagAssembler {

  private TagAssembler() {}

  public static TagResponse toResponse(Tag tag) {
    TagResponse r = new TagResponse();
    r.setId(tag.getId().toString());
    r.setName(tag.getName());
    return r;
  }

  public static List<TagResponse> toResponseList(List<Tag> tags) {
    return tags.stream().map(TagAssembler::toResponse).toList();
  }
}
