package knowflow.sanjin.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;

/** FileMetadata 数据访问。 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {}
