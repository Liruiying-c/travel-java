package org.example.traveljava.common;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 的 Mapper 扫描单独放一个配置类。
 *
 * <p>放在主启动类上会有一个副作用：@WebMvcTest 这类切片测试会把主类整个带进来，
 * 于是 @MapperScan 注册的 MapperFactoryBean 在没有 SqlSessionFactory 的切片里
 * 直接启动失败。挪出来以后，切片只加载 Web 组件，Mapper 在测试里用 @MockitoBean 替代。</p>
 */
@Configuration
@MapperScan("org.example.traveljava.Mapper")
public class MybatisConfig {
}
