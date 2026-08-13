package knowflow.sanjin.modules.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 文档上传配置装配：注册 {@code knowflow.document.*}。 */
@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfig {}
