# Publish all Nacos configs via API (with server identity auth)
$base = "http://127.0.0.1:18848/nacos/v1/cs/configs"
$headers = @{"admin" = "security"}

$configs = @(
    @{dataId="application-dev.yml"; content=@"
spring:
  datasource:
    dynamic:
      druid:
        initial-size: 5
        min-idle: 5
        maxActive: 20
      datasource:
        master:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/ry-cloud?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
          username: ${DB_USER:root}
          password: ${DB_PASSWORD:password}
"@},
    @{dataId="ruoyi-gateway-dev.yml"; content=@"
spring:
  cloud:
    gateway:
      discovery:
        locator:
          lowerCaseServiceId: true
          enabled: true
      routes:
        - id: ruoyi-auth
          uri: lb://ruoyi-auth
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=1
        - id: ruoyi-gen
          uri: lb://ruoyi-gen
          predicates:
            - Path=/code/**
          filters:
            - StripPrefix=1
        - id: ruoyi-job
          uri: lb://ruoyi-job
          predicates:
            - Path=/schedule/**
          filters:
            - StripPrefix=1
        - id: ruoyi-system
          uri: lb://ruoyi-system
          predicates:
            - Path=/system/**
          filters:
            - StripPrefix=1
        - id: ruoyi-file
          uri: lb://ruoyi-file
          predicates:
            - Path=/file/**
          filters:
            - StripPrefix=1
security:
  captcha:
    enabled: true
    type: math
  xss:
    enabled: true
    excludeUrls:
      - /system/notice
  ignore:
    whites:
      - /auth/logout
      - /auth/login
      - /auth/register
      - /*/v2/api-docs
      - /*/v3/api-docs
      - /csrf
"@},
    @{dataId="ruoyi-system-dev.yml"; content=@"
spring:
  datasource:
    dynamic:
      datasource:
        master:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/ry-cloud?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
          username: ${DB_USER:root}
          password: ${DB_PASSWORD:password}
mybatis:
  typeAliasesPackage: com.ruoyi.system
  mapperLocations: classpath:mapper/**/*.xml
"@},
    @{dataId="ruoyi-auth-dev.yml"; content=@"
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: 
"@},
    @{dataId="ruoyi-gen-dev.yml"; content=@"
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/ry-cloud?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:password}
mybatis:
  typeAliasesPackage: com.ruoyi.gen.domain
  mapperLocations: classpath:mapper/**/*.xml
gen:
  author: ruoyi
  packageName: com.ruoyi.system
  autoRemovePre: false
  tablePrefix: sys_
  allowOverwrite: false
"@},
    @{dataId="ruoyi-job-dev.yml"; content=@"
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/ry-cloud?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:password}
mybatis:
  typeAliasesPackage: com.ruoyi.job.domain
  mapperLocations: classpath:mapper/**/*.xml
"@},
    @{dataId="ruoyi-monitor-dev.yml"; content=@"
spring:
  security:
    user:
      name: ruoyi
      password: 123456
  boot:
    admin:
      ui:
        title: 若依服务状态监控
"@}
)

foreach ($cfg in $configs) {
    $body = "dataId=$($cfg.dataId)&group=DEFAULT_GROUP&content=$([System.Uri]::EscapeDataString($cfg.content))"
    $result = curl.exe -s -X POST $base -H "admin: security" -d $body
    Write-Host "$($cfg.dataId): $result"
}

Write-Host "[DONE] All configs published!"
