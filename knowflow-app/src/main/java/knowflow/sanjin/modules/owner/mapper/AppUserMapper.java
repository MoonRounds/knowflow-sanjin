package knowflow.sanjin.modules.owner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.owner.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

/** 极简 System Owner 用户表 Mapper；V1 仅 Flyway 预置 id=1，无业务写路径。 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {}
