@echo off
echo.
echo [INFO] 启动 RuoYi Workflow 工作流模块
echo.

set JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC

cd %~dp0
cd ..
call mvn -pl ruoyi-modules/ruoyi-workflow -am spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%"

pause
