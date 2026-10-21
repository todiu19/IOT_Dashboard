package com.iot.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "IoT Dashboard API",
                version = "1.0.0",
                description = "API giám sát cảm biến và điều khiển thiết bị IoT"
        ),
        tags = {
                @Tag(name = "Authentication", description = "Đăng nhập và đăng xuất"),
                @Tag(name = "Dashboard", description = "Dữ liệu tổng hợp cho Dashboard"),
                @Tag(name = "Devices", description = "Điều khiển thiết bị"),
                @Tag(name = "Sensors", description = "Tra cứu dữ liệu cảm biến"),
                @Tag(name = "Action History", description = "Tra cứu lịch sử điều khiển"),
                @Tag(name = "Profile", description = "Hồ sơ người dùng hiện tại")
        }
)
@SecurityScheme(
        name = OpenApiConfig.JWT_COOKIE_SCHEME,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "jwt",
        description = "JWT HttpOnly được backend đặt sau khi gọi POST /api/auth/login"
)
public class OpenApiConfig {

    public static final String JWT_COOKIE_SCHEME = "jwtCookie";
}
